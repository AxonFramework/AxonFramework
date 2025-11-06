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

import org.axonframework.messaging.core.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.core.FluxUtils;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.core.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.QueryExecutionException;
import org.axonframework.messaging.queryhandling.QueryHandlingComponent;
import org.axonframework.messaging.queryhandling.QueryPriorityCalculator;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitterParameterResolverFactory;
import org.axonframework.messaging.queryhandling.SubscriptionQueryAlreadyRegisteredException;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.messaging.queryhandling.annotation.AnnotatedQueryHandlingComponent;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.queryhandling.gateway.DefaultQueryGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.*;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract test suite for the
 * {@link QueryBus#subscriptionQuery(QueryMessage, ProcessingContext, int)}
 * functionality.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 */
public abstract class AbstractSubscriptionQueryTestSuite extends AbstractQueryTestSuite {

    private static final MessageType TEST_QUERY_TYPE = new MessageType("chatMessages");
    private static final MessageType TEST_RESPONSE_TYPE = new MessageType(String.class);
    private static final MessageType TEST_UPDATE_PAYLOAD_TYPE = new MessageType("update");
    private static final String TEST_QUERY_PAYLOAD = "axonFrameworkCR";
    private static final String TEST_UPDATE_PAYLOAD = "some-update";

    private static final String FOUND = "found";

    private static final MessageConverter CONVERTER = new DelegatingMessageConverter(new JacksonConverter());

    private QueryBus queryBus;
    private QueryGateway queryGateway;
    private ChatQueryHandler queryHandlingComponent;

    @BeforeEach
    void setUp() {
        queryBus = queryBus();
        queryGateway = new DefaultQueryGateway(queryBus,
                                               new ClassBasedMessageTypeResolver(),
                                               QueryPriorityCalculator.defaultCalculator(),
                                               CONVERTER);
        queryHandlingComponent = new ChatQueryHandler();
        ParameterResolverFactory parameterResolverFactory = MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(ChatQueryHandler.class),
                new QueryUpdateEmitterParameterResolverFactory()
        );
        QueryHandlingComponent annotatedQueryHandlingComponent = new AnnotatedQueryHandlingComponent<>(
                queryHandlingComponent, parameterResolverFactory, CONVERTER
        );
        queryBus.subscribe(annotatedQueryHandlingComponent);
        Hooks.onErrorDropped(error -> {/*Ignore these exceptions for these test cases*/});
    }

    @AfterEach
    void tearDown() {
        Hooks.resetOnErrorDropped();
    }


    private static boolean assertRecorded(Collection<QueryResponseMessage> elements) {
        LinkedList<QueryResponseMessage> recordedMessages = new LinkedList<>(elements);
        assert recordedMessages.peekFirst() != null;
        boolean firstIs101 = "Update0".equals(recordedMessages.peekFirst().payload());
        assert recordedMessages.peekLast() != null;
        boolean lastIs200 = "Update99".equals(recordedMessages.peekLast().payload());
        return elements.size() == 100 && firstIs101 && lastIs200;
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void emittingAnUpdate() {
        // given
        QueryMessage queryMessage1 = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        MessageType numberOfMessagesQueryType = new MessageType("numberOfMessages");
        QueryMessage queryMessage2 = new GenericQueryMessage(
                numberOfMessagesQueryType, 5
        );
        ProcessingContext testContext = null;
        Predicate<QueryMessage> stringQueryFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage stringUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(new MessageType("query-string"), "Update11");
        SubscriptionQueryUpdateMessage stringUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(new MessageType("query-string"), "Update12");
        Predicate<QueryMessage> integerQueryFilter =
                message -> numberOfMessagesQueryType.equals(message.type())
                        && Objects.requireNonNull(message.payloadAs(Integer.class, CONVERTER)).equals(5);
        SubscriptionQueryUpdateMessage integerUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(new MessageType("query-integer"), 1);
        SubscriptionQueryUpdateMessage integerUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(new MessageType("query-integer"), 2);
        // when
        MessageStream<QueryResponseMessage> resultOne = queryBus.subscriptionQuery(queryMessage1, testContext, 50);
        queryBus.emitUpdate(stringQueryFilter, () -> stringUpdateOne, testContext);
        queryBus.completeSubscriptions(stringQueryFilter, testContext);
        queryBus.emitUpdate(stringQueryFilter, () -> stringUpdateTwo, testContext);
        MessageStream<QueryResponseMessage> resultTwo = queryBus.subscriptionQuery(queryMessage2, testContext, 50);
        queryBus.emitUpdate(integerQueryFilter, () -> integerUpdateOne, testContext);
        queryBus.completeSubscriptions(integerQueryFilter, testContext);
        queryBus.emitUpdate(integerQueryFilter, () -> integerUpdateTwo, testContext);
        // then
        StepVerifier.create(FluxUtils.of(resultOne).map(MessageStream.Entry::message).mapNotNull(m -> m.payloadAs(String.class, CONVERTER)))
                    .expectNext("Message1", "Message2", "Message3", "Update11")
                    .expectComplete()
                    .verify();
        StepVerifier.create(FluxUtils.of(resultTwo).map(MessageStream.Entry::message).mapNotNull(m -> m.payloadAs(Integer.class, CONVERTER)))
                    .expectNext(0, 1)
                    .verifyComplete();
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void emittingNullUpdate() {
        // given
        QueryMessage queryMessage = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        ProcessingContext testContext = null;
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, null, String.class);
        // when
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        queryBus.emitUpdate(testFilter, () -> testUpdate, testContext);
        queryBus.completeSubscriptions(testFilter, testContext);
        // then
        StepVerifier.create(FluxUtils.of(result).filter(m -> m.message() instanceof SubscriptionQueryUpdateMessage))
                    .expectNextMatches(e -> e.message().payload() == null)
                    .verifyComplete();
    }

    @Test
    void emittingUpdateInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        // given...
        String testQueryName = "chatMessages";
        List<String> expectedUpdates = Collections.singletonList(TEST_UPDATE_PAYLOAD);
        UnitOfWork testUoW = UnitOfWorkTestUtils.aUnitOfWork();
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, TEST_UPDATE_PAYLOAD, String.class);
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(testQueryName), TEST_QUERY_PAYLOAD
        );
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // when...
        testUoW.onInvocation(context -> queryBus.emitUpdate(testFilter, () -> testUpdate, context));
        // then, before we commit, we don't have anything yet...
        List<String> updateList = new ArrayList<>();
        FluxUtils.of(result)
                 .filter(e -> e.message() instanceof SubscriptionQueryUpdateMessage)
                 .mapNotNull(e -> e.message().payloadAs(String.class, CONVERTER)).subscribe(updateList::add);
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
        QueryMessage queryMessage = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        RuntimeException toBeThrown = new RuntimeException();
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;

        // when
        MessageStream<QueryResponseMessage> result =
                queryBus.subscriptionQuery(queryMessage, null, Queues.SMALL_BUFFER_SIZE);
        try (ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.schedule(() -> {
                queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
                queryBus.completeSubscriptionsExceptionally(testFilter, toBeThrown, testContext);
                queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
            }, 500, TimeUnit.MILLISECONDS);
        }
        // then
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message).mapNotNull(m -> m.payloadAs(String.class, CONVERTER)))
                    .expectNext("Message1", "Message2", "Message3", "Update1")
                    .expectErrorMatches(toBeThrown::equals)
                    .verify();
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void completingSubscriptionQueryExceptionallyWhenOneOfSubscriptionFails() {
        // given
        QueryMessage queryMessage1 = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        QueryMessage queryMessage2 = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        List<String> queryOneUpdates = new ArrayList<>();
        List<String> queryTwoUpdates = new ArrayList<>();
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        // when
        MessageStream<QueryResponseMessage> resultOne = queryBus.subscriptionQuery(queryMessage1, null, 50);
        MessageStream<QueryResponseMessage> resultTwo = queryBus.subscriptionQuery(queryMessage2, null, 50);
        FluxUtils.of(resultOne)
                 .map(MessageStream.Entry::message)
                 .mapNotNull(m -> m.payloadAs(String.class, CONVERTER))
                 .subscribe(queryOneUpdates::add, t -> {
                     queryOneUpdates.add("Error1");
                     throw (RuntimeException) t;
                 });
        FluxUtils.of(resultTwo)
                 .map(MessageStream.Entry::message)
                 .mapNotNull(m -> m.payloadAs(String.class, CONVERTER))
                 .subscribe(queryTwoUpdates::add, t -> queryTwoUpdates.add("Error2"));
        queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
        queryBus.completeSubscriptionsExceptionally(testFilter, new RuntimeException(), testContext);
        queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        // then
        assertEquals(Arrays.asList("Message1", "Message2", "Message3", "Update1", "Error1"), queryOneUpdates);
        assertEquals(Arrays.asList("Message1", "Message2", "Message3", "Update1", "Error2"), queryTwoUpdates);
    }

    @Test
    void completingSubscriptionExceptionallyInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        // given...
        String testQueryName = "chatMessages";
        UnitOfWork testUoW = UnitOfWorkTestUtils.aUnitOfWork();
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, TEST_QUERY_PAYLOAD, String.class);
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(testQueryName), TEST_QUERY_PAYLOAD
        );
        // when staging the subscription query and update...
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        testUoW.runOnInvocation(context -> {
            queryBus.emitUpdate(testFilter, () -> testUpdate, context);
            queryBus.completeSubscriptionsExceptionally(testFilter, new RuntimeException(), context);
        });
        // then before we commit we don't have any update yet...
        Optional<MessageStream.Entry<QueryResponseMessage>> peeked = result
                .filter(m -> m.message() instanceof SubscriptionQueryUpdateMessage)
                .peek();
        assertTrue(peeked.isEmpty());
        // when we execute the UoW, it commits...
        testUoW.execute().join();
        // then...
        assertEquals(TEST_QUERY_PAYLOAD,
                     result.next().map(e -> e.message().payloadAs(String.class, CONVERTER)).orElse(null));
        assertTrue(result.isCompleted() && result.error().isPresent());
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void completingSubscriptionQuery() {
        // given
        QueryMessage queryMessage = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        // when
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        try (ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.schedule(() -> {
                queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
                queryBus.completeSubscriptions(testFilter, testContext);
                queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
            }, 500, TimeUnit.MILLISECONDS);
        }
        // then
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message).mapNotNull(m -> m.payloadAs(String.class, CONVERTER)))
                    .expectNext("Message1", "Message2", "Message3", "Update1")
                    .verifyComplete();
    }

    @Test
    void completingSubscriptionInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        // given...
        List<String> expectedUpdates = Collections.singletonList(TEST_UPDATE_PAYLOAD);
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, TEST_UPDATE_PAYLOAD, String.class);
        QueryMessage queryMessage = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        UnitOfWork testUoW = UnitOfWorkTestUtils.aUnitOfWork();
        // when...
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        testUoW.runOnInvocation(context -> {
            queryBus.emitUpdate(testFilter, () -> testUpdate, context);
            queryBus.completeSubscriptions(testFilter, context);
        });
        // when...
        testUoW.onInvocation(context -> queryBus.emitUpdate(testFilter, () -> testUpdate, context));
        // then before we commit we don't have anything yet...
        List<String> updateList = new ArrayList<>();
        FluxUtils.of(result)
                 .filter(e -> e.message() instanceof SubscriptionQueryUpdateMessage)
                 .mapNotNull(e -> e.message().payloadAs(String.class, CONVERTER)).subscribe(updateList::add);
        assertTrue(updateList.isEmpty());
        // when we execute the UoW, it commits...
        testUoW.execute().join();
        // then...
        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(50))
               .untilAsserted(() -> assertEquals(expectedUpdates, updateList));
        assertTrue(result.isCompleted() && result.error().isEmpty());
    }

    @Test
    void orderingOfOperationOnUpdateHandler() {
        // given
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType("emitFirstThenReturnInitial"), TEST_QUERY_PAYLOAD
        );
        // when
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // then
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message).mapNotNull(Message::payload))
                    .expectNext("Initial", "Update1", "Update2")
                    .verifyComplete();
    }

    @Test
    void doubleSubscriptionMessage() {
        // given
        QueryMessage queryMessage = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );

        // when
        queryBus.subscriptionQuery(queryMessage, null, 50);
        MessageStream<QueryResponseMessage> secondSubscription = queryBus.subscriptionQuery(queryMessage, null, 50);

        // then
        assertTrue(secondSubscription.error().isPresent());
        assertInstanceOf(SubscriptionQueryAlreadyRegisteredException.class, secondSubscription.error().get());
    }

    @Test
    void subscribingQueryHandlerFailing() {
        // given
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType("failingQuery"), TEST_QUERY_PAYLOAD
        );
        // when
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // then
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message))
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
    void replayBufferOverflow() {
        // given...
        QueryMessage queryMessage = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        ProcessingContext testContext = null;
        // when...
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 100);
        for (int i = 0; i <= 200; i++) {
            int number = i;
            queryBus.emitUpdate(testFilter,
                                () -> new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE,
                                                                                "Update" + number),
                                testContext);
        }
        queryBus.completeSubscriptions(testFilter, testContext);
        // then...
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message)
                                     .filter(m -> m instanceof SubscriptionQueryUpdateMessage))
                    .recordWith(LinkedList::new)
                    .thenConsumeWhile(x -> true)
                    .expectRecordedMatches(AbstractSubscriptionQueryTestSuite::assertRecorded)
                    .verifyError(QueryExecutionException.class);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void onBackpressureError() {
        // given...
        QueryMessage queryMessage = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        ProcessingContext testContext = null;
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 100);
        // when...
        Flux<QueryResponseMessage> updates = FluxUtils.of(result).map(MessageStream.Entry::message)
                                                      .onBackpressureBuffer(100);
        // then...
        StepVerifier.create(updates, StepVerifierOptions.create().initialRequest(0))
                    .expectSubscription()
                    .then(() -> {
                        for (int i = 0; i < 200; i++) {
                            int number = i;
                            queryBus.emitUpdate(
                                    testFilter,
                                    () -> new GenericSubscriptionQueryUpdateMessage(
                                            TEST_UPDATE_PAYLOAD_TYPE, "Update" + number
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
        QueryMessage queryMessage = new GenericQueryMessage(
                TEST_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> TEST_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // when...
        queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
        result.close();
        queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        // then...
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message)
                                     .filter(SubscriptionQueryUpdateMessage.class::isInstance)
                                     .mapNotNull(m -> m.payloadAs(String.class, CONVERTER)))
                    .expectNext("Update1")
                    .verifyComplete();
    }

    @Test
    void subscriptionQueryResultHandle() throws InterruptedException {
        // given...
        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType("emitFirstThenReturnInitial"), TEST_QUERY_PAYLOAD
        );
        // when...
        List<String> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        Flux.from(queryGateway.subscriptionQuery(queryMessage, String.class, 50))
            .subscribe(element -> {
                results.add(element);
                latch.countDown();
            });
        // then...
        assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertEquals(Arrays.asList("Initial", "Update1", "Update2"), results);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorOnInitialResult() {
        // given
        MessageType failingQueryType = new MessageType("failingQuery");
        QueryMessage queryMessage = new GenericQueryMessage(
                failingQueryType, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> failingQueryType.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        // when
        List<String> initialResult = new ArrayList<>();
        Flux.from(queryGateway.subscriptionQuery(queryMessage, String.class, 50))
            .subscribe(initialResult::add);
        queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
        queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        queryBus.completeSubscriptions(testFilter, testContext);
        // then
        assertTrue(initialResult.isEmpty());
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorOnUpdate() {
        // given
        MessageType failingQueryType = new MessageType("failingQuery");
        QueryMessage queryMessage = new GenericQueryMessage(
                failingQueryType, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> failingQueryType.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update1", String.class);
        ProcessingContext testContext = null;
        // when
        List<String> initialResult = new ArrayList<>();
        Flux.from(queryGateway.subscriptionQuery(queryMessage, String.class, 50))
            .subscribe(initialResult::add);

        queryBus.completeSubscriptionsExceptionally(testFilter, new RuntimeException(), testContext);
        queryBus.emitUpdate(testFilter, () -> testUpdate, testContext);
        // then
        assertTrue(initialResult.isEmpty());
    }

    @Disabled("TODO #3809 - completeSubscriptions doesn't work here")
    @Test
    void queryGatewayCorrectlyReturnsNullOnSubscriptionQueryWithNullInitialResult()
            throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = Mono.from(queryGateway.subscriptionQuery(new SomeQuery("not " + FOUND),
                                                                                    String.class))
                                               .toFuture();
        queryBus.completeSubscriptions(message -> true, null);
        assertNull(future.get());
    }

    @Test
    void queryGatewayCorrectlyReturnsOnSubscriptionQuery() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = Mono.from(queryGateway.subscriptionQuery(new SomeQuery(FOUND),
                                                                                    String.class))
                                               .toFuture();
        String result = future.get();
        assertEquals(FOUND, result);
    }


    private record SomeQuery(String filter) {

    }

    private static class ChatQueryHandler {

        private static final QualifiedName EMIT_THEN_RETURN_NAME = new QualifiedName("emitFirstThenReturnInitial");

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
                    emitter.emit(EMIT_THEN_RETURN_NAME, TEST_QUERY_PAYLOAD::equals, "Update1");
                    emitter.emit(EMIT_THEN_RETURN_NAME, TEST_QUERY_PAYLOAD::equals, "Update2");
                    emitter.complete(EMIT_THEN_RETURN_NAME, TEST_QUERY_PAYLOAD::equals);
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
