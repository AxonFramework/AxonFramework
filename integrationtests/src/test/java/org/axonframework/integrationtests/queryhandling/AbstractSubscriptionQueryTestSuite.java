/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.queryhandling;

import org.axonframework.common.TypeReference;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotations.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotations.MultiParameterResolverFactory;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryHandlingComponent;
import org.axonframework.queryhandling.QueryPriorityCalculator;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.QueryUpdateEmitterParameterResolverFactory;
import org.axonframework.queryhandling.SubscriptionQueryAlreadyRegisteredException;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResponseMessages;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.annotations.AnnotatedQueryHandlingComponent;
import org.axonframework.queryhandling.annotations.QueryHandler;
import org.axonframework.serialization.PassThroughConverter;
import org.junit.jupiter.api.*;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;
import reactor.util.concurrent.Queues;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract test suite for the
 * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, org.axonframework.messaging.unitofwork.ProcessingContext,
 * int)} functionality.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 */
public abstract class AbstractSubscriptionQueryTestSuite {

    private static final MessageType TEST_QUERY_TYPE = new MessageType("chatMessages");
    private static final MessageType TEST_UPDATE_TYPE = new MessageType("update");
    private static final String TEST_QUERY_PAYLOAD = "axonFrameworkCR";
    private static final String TEST_UPDATE_PAYLOAD = "some-update";

    private static final String FOUND = "found";

    private QueryBus queryBus;
    private QueryGateway queryGateway;
    private ChatQueryHandler queryHandlingComponent;

    @BeforeEach
    void setUp() {
        queryBus = queryBus();
        queryGateway = new DefaultQueryGateway(queryBus,
                                               new ClassBasedMessageTypeResolver(),
                                               QueryPriorityCalculator.defaultCalculator());
        queryHandlingComponent = new ChatQueryHandler();
        ParameterResolverFactory parameterResolverFactory = MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(ChatQueryHandler.class),
                new QueryUpdateEmitterParameterResolverFactory()
        );
        QueryHandlingComponent annotatedQueryHandlingComponent = new AnnotatedQueryHandlingComponent<>(
                queryHandlingComponent, parameterResolverFactory, PassThroughConverter.MESSAGE_INSTANCE
        );
        queryBus.subscribe(annotatedQueryHandlingComponent);
        Hooks.onErrorDropped(error -> {/*Ignore these exceptions for these test cases*/});
    }

    @AfterEach
    void tearDown() {
        Hooks.resetOnErrorDropped();
    }

    /**
     * Return the {@link QueryBus} used to test the
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)} functionality with.
     *
     * @return The {@link QueryBus} used to test the
     * {@link QueryBus#subscriptionQuery(SubscriptionQueryMessage, ProcessingContext, int)} functionality with.
     */
    public abstract QueryBus queryBus();

    @SuppressWarnings("ConstantValue")
    @Test
    void emittingAnUpdate() {
        // given
        SubscriptionQueryMessage queryMessage1 = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        SubscriptionQueryMessage queryMessage2 = new GenericSubscriptionQueryMessage(
                new MessageType("numberOfMessages"), 5,
                instanceOf(Integer.class), instanceOf(Integer.class)
        );
        ProcessingContext testContext = null;
        Predicate<SubscriptionQueryMessage> stringQueryFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage stringUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(new MessageType("query-string"), "Update11");
        SubscriptionQueryUpdateMessage stringUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(new MessageType("query-string"), "Update12");
        Predicate<SubscriptionQueryMessage> integerQueryFilter =
                message -> Objects.requireNonNull(message.payloadAs(Integer.class)).equals(5);
        SubscriptionQueryUpdateMessage integerUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(new MessageType("query-integer"), 1);
        SubscriptionQueryUpdateMessage integerUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(new MessageType("query-integer"), 2);
        // when
        SubscriptionQueryResponseMessages resultOne = queryBus.subscriptionQuery(queryMessage1, testContext, 50);
        queryBus.emitUpdate(stringQueryFilter, () -> stringUpdateOne, testContext);
        queryBus.completeSubscriptions(stringQueryFilter, testContext);
        queryBus.emitUpdate(stringQueryFilter, () -> stringUpdateTwo, testContext);
        SubscriptionQueryResponseMessages resultTwo = queryBus.subscriptionQuery(queryMessage2, testContext, 50);
        queryBus.emitUpdate(integerQueryFilter, () -> integerUpdateOne, testContext);
        queryBus.completeSubscriptions(integerQueryFilter, testContext);
        queryBus.emitUpdate(integerQueryFilter, () -> integerUpdateTwo, testContext);
        // then
        StepVerifier.create(resultOne.initialResult().mapNotNull(Message::payload))
                    .expectNext("Message1", "Message2", "Message3")
                    .expectComplete()
                    .verify();
        StepVerifier.create(resultOne.updates().mapNotNull(Message::payload))
                    .expectNext("Update11")
                    .expectComplete()
                    .verify();
        StepVerifier.create(resultTwo.initialResult().mapNotNull(Message::payload))
                    .expectNext(0)
                    .verifyComplete();
        StepVerifier.create(resultTwo.updates().mapNotNull(Message::payload))
                    .expectNext(1)
                    .verifyComplete();
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void emittingNullUpdate() {
        // given
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        ProcessingContext testContext = null;
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, null, String.class);
        // when
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);
        queryBus.emitUpdate(testFilter, () -> testUpdate, testContext);
        queryBus.completeSubscriptions(testFilter, testContext);
        // then
        StepVerifier.create(result.updates())
                    .expectNextMatches(m -> m.payload() == null)
                    .verifyComplete();
    }

    @Test
    void emittingUpdateInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        // given...
        String testQueryName = "chatMessages";
        List<String> expectedUpdates = Collections.singletonList(TEST_UPDATE_PAYLOAD);
        UnitOfWork testUoW = UnitOfWorkTestUtils.aUnitOfWork();
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, TEST_UPDATE_PAYLOAD, String.class);
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType(testQueryName), TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // when...
        testUoW.onInvocation(context -> queryBus.emitUpdate(testFilter, () -> testUpdate, context));
        // then before we commit we don't have anything yet...
        List<String> updateList = new ArrayList<>();
        result.updates().mapNotNull(m -> m.payloadAs(String.class)).subscribe(updateList::add);
        assertTrue(updateList.isEmpty());
        // when we execute the UoW, it commits...
        testUoW.execute().join();
        // then...
        assertEquals(expectedUpdates, updateList);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void completingSubscriptionQueryExceptionally() {
        // given
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        RuntimeException toBeThrown = new RuntimeException();
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;

        // when
        SubscriptionQueryResponseMessages result =
                queryBus.subscriptionQuery(queryMessage, null, Queues.SMALL_BUFFER_SIZE);
        try (ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.schedule(() -> {
                queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
                queryBus.completeSubscriptionsExceptionally(testFilter, toBeThrown, testContext);
                queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
            }, 500, TimeUnit.MILLISECONDS);
        }
        // then
        StepVerifier.create(result.initialResult().mapNotNull(Message::payload))
                    .expectNext("Message1", "Message2", "Message3")
                    .verifyComplete();
        StepVerifier.create(result.updates().mapNotNull(Message::payload))
                    .expectNext("Update1")
                    .expectErrorMatches(toBeThrown::equals)
                    .verify();
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void completingSubscriptionQueryExceptionallyWhenOneOfSubscriptionFails() {
        // given
        SubscriptionQueryMessage queryMessage1 = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        SubscriptionQueryMessage queryMessage2 = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        List<String> queryOneUpdates = new ArrayList<>();
        List<String> queryTwoUpdates = new ArrayList<>();
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        // when
        SubscriptionQueryResponseMessages resultOne = queryBus.subscriptionQuery(queryMessage1, null, 50);
        SubscriptionQueryResponseMessages resultTwo = queryBus.subscriptionQuery(queryMessage2, null, 50);
        resultOne.updates()
                 .mapNotNull(m -> m.payloadAs(String.class))
                 .subscribe(queryOneUpdates::add, t -> {
                     queryOneUpdates.add("Error1");
                     throw (RuntimeException) t;
                 });
        resultTwo.updates()
                 .mapNotNull(m -> m.payloadAs(String.class))
                 .subscribe(queryTwoUpdates::add, t -> queryTwoUpdates.add("Error2"));
        queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
        queryBus.completeSubscriptionsExceptionally(testFilter, new RuntimeException(), testContext);
        queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        // then
        assertEquals(Arrays.asList("Update1", "Error1"), queryOneUpdates);
        assertEquals(Arrays.asList("Update1", "Error2"), queryTwoUpdates);
    }

    @Test
    void completingSubscriptionExceptionallyInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        // given...
        String testQueryName = "chatMessages";
        List<String> expectedUpdates = Collections.singletonList(TEST_QUERY_PAYLOAD);
        UnitOfWork testUoW = UnitOfWorkTestUtils.aUnitOfWork();
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, TEST_QUERY_PAYLOAD, String.class);
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType(testQueryName), TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        // when staging the subscription query and updates...
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);
        testUoW.runOnInvocation(context -> {
            queryBus.emitUpdate(testFilter, () -> testUpdate, context);
            queryBus.completeSubscriptionsExceptionally(testFilter, new RuntimeException(), context);
        });
        // then before we commit we don't have anything yet...
        Flux<SubscriptionQueryUpdateMessage> emittedUpdates = result.updates();
        List<String> updateList = new ArrayList<>();
        result.updates().mapNotNull(m -> m.payloadAs(String.class)).subscribe(updateList::add);
        assertTrue(updateList.isEmpty());
        // when we execute the UoW, it commits...
        testUoW.execute().join();
        // then...
        assertEquals(expectedUpdates, updateList);
        StepVerifier.create(emittedUpdates)
                    .expectNextMatches(updateMessage -> TEST_QUERY_PAYLOAD.equals(updateMessage.payload()))
                    .verifyError(RuntimeException.class);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void completingSubscriptionQuery() {
        // given
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        // when
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);
        try (ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.schedule(() -> {
                queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
                queryBus.completeSubscriptions(testFilter, testContext);
                queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
            }, 500, TimeUnit.MILLISECONDS);
        }
        // then
        StepVerifier.create(result.initialResult().mapNotNull(Message::payload))
                    .expectNext("Message1", "Message2", "Message3")
                    .verifyComplete();
        StepVerifier.create(result.updates().mapNotNull(Message::payload))
                    .expectNext("Update1")
                    .verifyComplete();
    }

    @Test
    void completingSubscriptionInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        // given...
        List<String> expectedUpdates = Collections.singletonList(TEST_UPDATE_PAYLOAD);
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, TEST_UPDATE_PAYLOAD, String.class);
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        UnitOfWork testUoW = UnitOfWorkTestUtils.aUnitOfWork();
        // when...
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);
        testUoW.runOnInvocation(context -> {
            queryBus.emitUpdate(testFilter, () -> testUpdate, context);
            queryBus.completeSubscriptions(testFilter, context);
        });
        // when...
        testUoW.onInvocation(context -> queryBus.emitUpdate(testFilter, () -> testUpdate, context));
        // then before we commit we don't have anything yet...
        List<String> updateList = new ArrayList<>();
        result.updates().mapNotNull(m -> m.payloadAs(String.class)).subscribe(updateList::add);
        assertTrue(updateList.isEmpty());
        // when we execute the UoW, it commits...
        testUoW.execute().join();
        // then...
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(50))
               .untilAsserted(() -> assertEquals(expectedUpdates, updateList));
        StepVerifier.create(result.updates())
                    .expectNextMatches(updateMessage -> TEST_UPDATE_PAYLOAD.equals(updateMessage.payload()))
                    .verifyComplete();
    }

    @Disabled("TODO fix in #3488")
    @Test
    void orderingOfOperationOnUpdateHandler() {
        // given
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("emitFirstThenReturnInitial"), TEST_QUERY_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );
        // when
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // then
        StepVerifier.create(result.initialResult().mapNotNull(Message::payload))
                    .expectNext("Initial")
                    .expectComplete()
                    .verify();
        StepVerifier.create(result.updates().mapNotNull(Message::payload))
                    .expectNext("Update1", "Update2")
                    .verifyComplete();
    }

    @Test
    void subscribingQueryHandlerFailing() {
        // given
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("failingQuery"), TEST_QUERY_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );
        // when
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // then
        StepVerifier.create(result.initialResult())
                    .expectErrorMatches(exception -> {
                        if (exception instanceof QueryExecutionException qee) {
                            return queryHandlingComponent.toBeThrown.equals(qee.getCause());
                        }
                        return false;
                    })
                    .verify();
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void severalSubscriptions() {
        // given...
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update10", String.class);
        SubscriptionQueryUpdateMessage testUpdateThree =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update11", String.class);
        ProcessingContext testContext = null;
        // when...
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 8);
        List<String> initial1 = new ArrayList<>();
        List<String> initial2 = new ArrayList<>();
        List<String> update1 = new ArrayList<>();
        List<String> update2 = new ArrayList<>();
        List<String> update3 = new ArrayList<>();
        result.initialResult().mapNotNull(m -> m.payloadAs(String.class)).subscribe(initial1::add);
        result.initialResult().mapNotNull(m -> m.payloadAs(String.class)).subscribe(initial2::add);
        queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
        result.updates().mapNotNull(m -> m.payloadAs(String.class)).subscribe(update1::add);
        result.updates().mapNotNull(m -> m.payloadAs(String.class)).subscribe(update2::add);
        for (int i = 2; i < 10; i++) {
            int number = i;
            queryBus.emitUpdate(testFilter,
                                () -> new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update" + number),
                                testContext);
        }
        result.updates().mapNotNull(m -> m.payloadAs(String.class)).subscribe(update3::add);
        queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        queryBus.emitUpdate(testFilter, () -> testUpdateThree, testContext);
        queryBus.completeSubscriptions(testFilter, testContext);
        // then...
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initial1);
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initial2);
        assertEquals(Arrays.asList(
                "Update1", "Update2", "Update3", "Update4", "Update5",
                "Update6", "Update7", "Update8", "Update9", "Update10", "Update11"
        ), update1);
        assertEquals(Arrays.asList(
                "Update1", "Update2", "Update3", "Update4", "Update5",
                "Update6", "Update7", "Update8", "Update9", "Update10", "Update11"
        ), update2);
        assertEquals(Arrays.asList(
                "Update2", "Update3", "Update4", "Update5", "Update6",
                "Update7", "Update8", "Update9", "Update10", "Update11"
        ), update3);
    }

    @Test
    void doubleSubscriptionMessage() {
        // given...
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        // when...
        queryBus.subscriptionQuery(queryMessage, null, 50);
        // then...
        assertThrows(SubscriptionQueryAlreadyRegisteredException.class,
                     () -> queryBus.subscriptionQuery(queryMessage, null, 50));
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void replayBufferOverflow() {
        // given...
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        ProcessingContext testContext = null;
        // when...
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 100);
        for (int i = 0; i <= 200; i++) {
            int number = i;
            queryBus.emitUpdate(testFilter,
                                () -> new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update" + number),
                                testContext);
        }
        queryBus.completeSubscriptions(testFilter, testContext);
        // then...
        StepVerifier.create(result.updates())
                    .recordWith(LinkedList::new)
                    .thenConsumeWhile(x -> true)
                    .expectRecordedMatches(AbstractSubscriptionQueryTestSuite::assertRecorded)
                    .verifyComplete();
    }

    private static boolean assertRecorded(Collection<SubscriptionQueryUpdateMessage> elements) {
        LinkedList<SubscriptionQueryUpdateMessage> recordedMessages = new LinkedList<>(elements);
        assert recordedMessages.peekFirst() != null;
        boolean firstIs101 = "Update101".equals(recordedMessages.peekFirst().payload());
        assert recordedMessages.peekLast() != null;
        boolean lastIs200 = "Update200".equals(recordedMessages.peekLast().payload());
        return elements.size() == 100 && firstIs101 && lastIs200;
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void onBackpressureError() {
        // given...
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        ProcessingContext testContext = null;
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 100);
        // when...
        Flux<SubscriptionQueryUpdateMessage> updates = result.updates().onBackpressureBuffer(100);
        // then...
        StepVerifier.create(updates, StepVerifierOptions.create().initialRequest(0))
                    .expectSubscription()
                    .then(() -> {
                        for (int i = 0; i < 200; i++) {
                            int number = i;
                            queryBus.emitUpdate(
                                    testFilter,
                                    () -> new GenericSubscriptionQueryUpdateMessage(
                                            TEST_UPDATE_TYPE, "Update" + number
                                    ),
                                    testContext
                            );
                        }
                        queryBus.completeSubscriptions(testFilter, testContext);
                    })
                    .expectNoEvent(Duration.ofMillis(100))
                    .thenRequest(100)
                    .expectNextCount(100)
                    .expectErrorMatches(Exceptions::isOverflow)
                    .verify(Duration.ofSeconds(5));
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void subscriptionDisposal() {
        // given...
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        SubscriptionQueryResponseMessages result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // when...
        queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
        result.close();
        queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        // then...
        StepVerifier.create(result.updates().mapNotNull(Message::payload))
                    .expectNext("Update1")
                    .verifyComplete();
    }

    @Disabled("TODO fix in #3488")
    @Test
    void subscriptionQueryResultHandle() throws InterruptedException {
        // given...
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("emitFirstThenReturnInitial"), TEST_QUERY_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );
        // when...
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        queryGateway.subscriptionQuery(queryMessage, String.class, String.class, null, 50)
                    .handle(initial -> {
                                initialResult.add(initial);
                                latch.countDown();
                            },
                            update -> {
                                updates.add(update);
                                latch.countDown();
                            },
                            exception -> {
                                // do nothing
                            }
                    );
        // then...
        assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertEquals(Collections.singletonList("Initial"), initialResult);
        assertEquals(Arrays.asList("Update1", "Update2"), updates);
    }

    @Disabled("TODO fix in #3488")
    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorConsumingAnInitialResult() throws InterruptedException {
        // given
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("emitFirstThenReturnInitial"), TEST_QUERY_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );
        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        queryGateway.subscriptionQuery(queryMessage, String.class, String.class, null, 50)
                    .handle(initial -> {
                                initialResult.add(initial);
                                latch.countDown();
                                throw new IllegalStateException("oops");
                            },
                            update -> {
                                updates.add(update);
                                latch.countDown();
                            },
                            exception -> {
                                // do nothing
                            }
                    );
        // then
        assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertEquals(Collections.singletonList("Initial"), initialResult);
        assertTrue(updates.isEmpty());
    }

    @Disabled("TODO fix in #3488")
    @SuppressWarnings("ConstantValue")
    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorConsumingAnUpdate() {
        // given
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryGateway.subscriptionQuery(queryMessage, String.class, String.class, null, 1)
                    .handle(
                            initialResult::add,
                            update -> {
                                updates.add(update);
                                throw new IllegalStateException("oops");
                            }, exception -> {
                                // do nothing
                            }
                    );
        queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
        queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        // then
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initialResult);
        assertEquals(Collections.singletonList("Update1"), updates);
    }

    @Disabled("TODO fix in #3488")
    @SuppressWarnings("ConstantValue")
    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorConsumingABufferedUpdate() {
        // given
        AtomicBoolean invoked = new AtomicBoolean(false);
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD,
                multipleInstancesOf(String.class), instanceOf(String.class)
        );
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryGateway.subscriptionQuery(queryMessage, String.class, String.class, null, 1)
                    .handle(initial -> {
                                // Making sure update is emitted before subscribing and that emitting occurs once.
                                if (invoked.compareAndSet(false, true)) {
                                    queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
                                }
                                initialResult.add(initial);
                            },
                            update -> {
                                updates.add(update);
                                throw new IllegalStateException("oops");
                            },
                            exception -> {
                                // do nothing
                            }
                    );
        queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        // then
        assertEquals(Arrays.asList("Message1", "Message2", "Message3"), initialResult);
        assertEquals(Collections.singletonList("Update1"), updates);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorOnInitialResult() {
        // given
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("failingQuery"), TEST_QUERY_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryGateway.subscriptionQuery(queryMessage, String.class, String.class, null, 50)
                    .handle(
                            initialResult::add,
                            updates::add,
                            exception -> {
                                // Do Nothing
                            }
                    );
        queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
        queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        queryBus.completeSubscriptions(testFilter, testContext);
        // then
        assertTrue(initialResult.isEmpty());
        assertTrue(updates.isEmpty());
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorOnUpdate() {
        // given
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("failingQuery"), TEST_QUERY_PAYLOAD,
                instanceOf(String.class), instanceOf(String.class)
        );
        Predicate<SubscriptionQueryMessage> testFilter =
                message -> TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_TYPE, "Update1", String.class);
        ProcessingContext testContext = null;
        // when
        List<String> initialResult = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        queryGateway.subscriptionQuery(queryMessage, String.class, String.class, null, 50)
                    .handle(
                            initialResult::add,
                            updates::add,
                            exception -> {
                                // Do nothing
                            }
                    );
        queryBus.completeSubscriptionsExceptionally(testFilter, new RuntimeException(), testContext);
        queryBus.emitUpdate(testFilter, () -> testUpdate, testContext);
        // then
        assertTrue(initialResult.isEmpty());
        assertTrue(updates.isEmpty());
    }

    @Test
    void queryGatewayCorrectlyReturnsNullOnSubscriptionQueryWithNullInitialResult()
            throws ExecutionException,
                   InterruptedException {
        assertNull(queryGateway.subscriptionQuery(new SomeQuery("not " + FOUND), String.class, String.class, null)
                               .initialResult()
                               .next()
                               .toFuture()
                               .get());
    }

    @Test
    void queryGatewayCorrectlyReturnsOnSubscriptionQuery() throws ExecutionException, InterruptedException {
        String result = queryGateway.subscriptionQuery(new SomeQuery(FOUND), String.class, String.class, null)
                                    .initialResult()
                                    .next()
                                    .toFuture()
                                    .get();
        assertEquals(FOUND, result);
    }

    private record SomeQuery(String filter) {

    }

    private static class ChatQueryHandler {

        private final RuntimeException toBeThrown = new RuntimeException("oops");

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "chatMessages")
        public List<String> chatMessages(String chatRoom) {
            return Arrays.asList("Message1", "Message2", "Message3");
        }

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "numberOfMessages")
        public Integer numberOfMessages(Integer i) {
            return 0;
        }

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "failingQuery")
        public String failingQuery(String criteria) {
            throw toBeThrown;
        }

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "emitFirstThenReturnInitial")
        public String emitFirstThenReturnInitial(String criteria,
                                                 QueryUpdateEmitter emitter) throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                executor.submit(() -> {
                    emitter.emit(String.class, TEST_QUERY_PAYLOAD::equals, "Update1");
                    emitter.emit(String.class, TEST_QUERY_PAYLOAD::equals, "Update2");
                    emitter.complete(String.class, TEST_QUERY_PAYLOAD::equals);
                    latch.countDown();
                });
            }
            latch.await();
            return "Initial";
        }

        @SuppressWarnings("unused")
        @QueryHandler
        public String someQueryHandler(SomeQuery query) {
            return FOUND.equals(query.filter()) ? FOUND : null;
        }
    }
}
