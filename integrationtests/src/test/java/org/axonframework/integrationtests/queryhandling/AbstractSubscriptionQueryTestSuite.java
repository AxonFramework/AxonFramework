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

import jakarta.annotation.Nonnull;
import org.assertj.core.util.Strings;
import org.awaitility.Awaitility;
import org.axonframework.messaging.FluxUtils;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.conversion.MessageConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryExecutionException;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryAlreadyRegisteredException;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.serialization.json.JacksonConverter;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract test suite for the
 * {@link QueryBus#subscriptionQuery(QueryMessage, org.axonframework.messaging.unitofwork.ProcessingContext, int)}
 * functionality.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 */
public abstract class AbstractSubscriptionQueryTestSuite extends AbstractQueryTestSuite {

    private static final String TEST_QUERY_PAYLOAD = "axonFrameworkCR";
    private static final String TEST_UPDATE_PAYLOAD = "some-update";
    private static final String FOUND = "found";

    private static final MessageConverter CONVERTER = new DelegatingMessageConverter(new JacksonConverter());

    // Unique query name using UUID for the commonly used chat messages query
    private final QualifiedName CHAT_MESSAGES_QUERY_NAME = new QualifiedName("test.chatMessages." + UUID.randomUUID());
    private final MessageType CHAT_MESSAGES_QUERY_TYPE = new MessageType(CHAT_MESSAGES_QUERY_NAME.fullName());

    private static final MessageType TEST_RESPONSE_TYPE = new MessageType(String.class);
    private static final MessageType TEST_UPDATE_PAYLOAD_TYPE = new MessageType("update");

    private QueryBus queryBus;
    private RuntimeException toBeThrown;

    @BeforeEach
    void setUp() {
        queryBus = queryBus();
        toBeThrown = new RuntimeException("oops");

        // Register the commonly used chat messages handler
        queryBus.subscribe(CHAT_MESSAGES_QUERY_NAME, (query, context) -> MessageStream.fromItems(
                new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, "Message1"),
                new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, "Message2"),
                new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, "Message3")
        ));

        Hooks.onErrorDropped(error -> {/*Ignore these exceptions for these test cases*/});
    }

    @AfterEach
    void tearDown() {
        Hooks.resetOnErrorDropped();
    }

    private static boolean assertRecorded(Collection<QueryResponseMessage> elements) {
        LinkedList<QueryResponseMessage> recordedMessages = new LinkedList<>(elements);
        assert recordedMessages.peekFirst() != null;
        boolean firstIs101 = "Update0".equals(recordedMessages.peekFirst().payloadAs(String.class, CONVERTER));
        assert recordedMessages.peekLast() != null;
        boolean lastIs200 = "Update99".equals(recordedMessages.peekLast().payloadAs(String.class, CONVERTER));
        return elements.size() == 100 && firstIs101 && lastIs200;
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void emittingAnUpdate() {
        // given
        QualifiedName numberOfMessagesQueryName = new QualifiedName("test.numberOfMessages." + UUID.randomUUID());
        MessageType numberOfMessagesQueryType = new MessageType(numberOfMessagesQueryName.fullName());

        queryBus.subscribe(numberOfMessagesQueryName, (query, context) ->
                MessageStream.just(new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, 0))
        );

        QueryMessage queryMessage1 = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        QueryMessage queryMessage2 = new GenericQueryMessage(
                numberOfMessagesQueryType, 5
        );
        ProcessingContext testContext = null;
        Predicate<QueryMessage> stringQueryFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
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
        scheduleAfterDelay(() -> {
            queryBus.emitUpdate(stringQueryFilter, () -> stringUpdateOne, testContext);
            queryBus.completeSubscriptions(stringQueryFilter, testContext);
            queryBus.emitUpdate(stringQueryFilter, () -> stringUpdateTwo, testContext);
        });
        MessageStream<QueryResponseMessage> resultTwo = queryBus.subscriptionQuery(queryMessage2, testContext, 50);
        scheduleAfterDelay(() -> {
            queryBus.emitUpdate(integerQueryFilter, () -> integerUpdateOne, testContext);
            queryBus.completeSubscriptions(integerQueryFilter, testContext);
            queryBus.emitUpdate(integerQueryFilter, () -> integerUpdateTwo, testContext);
        });
        // then
        StepVerifier.create(FluxUtils.of(resultOne).map(MessageStream.Entry::message)
                                     .mapNotNull(m -> m.payloadAs(String.class, CONVERTER)))
                    .expectNext("Message1", "Message2", "Message3", "Update11")
                    .expectComplete()
                    .verify();
        StepVerifier.create(FluxUtils.of(resultTwo).map(MessageStream.Entry::message)
                                     .mapNotNull(m -> m.payloadAs(Integer.class, CONVERTER)))
                    .expectNext(0, 1)
                    .verifyComplete();
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void emittingNullUpdate() {
        // given
        QueryMessage queryMessage = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        ProcessingContext testContext = null;
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, null, String.class);
        // when
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        scheduleAfterDelay(() -> {
            queryBus.emitUpdate(testFilter, () -> testUpdate, testContext);
            queryBus.completeSubscriptions(testFilter, testContext);
        });
        // then
        StepVerifier.create(FluxUtils.of(result).filter(m -> m.message() instanceof SubscriptionQueryUpdateMessage))
                    .expectNextMatches(e -> Strings.isNullOrEmpty(e.message().payloadAs(String.class, CONVERTER)))
                    .verifyComplete();
    }

    @Test
    void emittingUpdateInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        // given...
        List<String> expectedUpdates = Collections.singletonList(TEST_UPDATE_PAYLOAD);
        UnitOfWork testUoW = UnitOfWorkTestUtils.aUnitOfWork();
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, TEST_UPDATE_PAYLOAD, String.class);
        QueryMessage queryMessage = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // when...
        testUoW.runOnInvocation(context -> queryBus.emitUpdate(testFilter, () -> testUpdate, context).join());
        // then, before we commit, we don't have anything yet...
        List<String> updateList = new ArrayList<>();
        FluxUtils.of(result)
                 .filter(e -> e.message() instanceof SubscriptionQueryUpdateMessage)
                 .mapNotNull(e -> e.message().payloadAs(String.class, CONVERTER))
                 .subscribe(updateList::add);
        assertTrue(updateList.isEmpty());
        // when we execute the UoW, it commits...
        testUoW.execute().join();
        // then...
        Awaitility.await()
                  .atMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> assertEquals(expectedUpdates, updateList));
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void completingSubscriptionQueryExceptionally() {
        // given
        QueryMessage queryMessage = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        RuntimeException toBeThrown = new RuntimeException();
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;

        // when
        MessageStream<QueryResponseMessage> result =
                queryBus.subscriptionQuery(queryMessage, null, Queues.SMALL_BUFFER_SIZE);
        scheduleAfterDelay(() -> {
            queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext).join();
            queryBus.completeSubscriptionsExceptionally(testFilter, toBeThrown, testContext).join();
            queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext).join();
        });
        // then
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message)
                                     .mapNotNull(m -> m.payloadAs(String.class, CONVERTER)))
                    .expectNext("Message1", "Message2", "Message3", "Update1")
                    .expectErrorMatches(assertQueryExecutionException(toBeThrown))
                    .verify();
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void completingSubscriptionQueryExceptionallyWhenOneOfSubscriptionFails() throws InterruptedException {
        // given
        QueryMessage queryMessage1 = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        QueryMessage queryMessage2 = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        List<String> queryOneUpdates = new ArrayList<>();
        List<String> queryTwoUpdates = new ArrayList<>();
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
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
        scheduleAfterDelay(() -> {
            queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext).join();
            queryBus.completeSubscriptionsExceptionally(testFilter, new RuntimeException(), testContext).join();
            queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext).join();
        });
        // then
        Awaitility
                .await()
                .untilAsserted(() -> {
                    assertEquals(
                            Arrays.asList("Message1", "Message2", "Message3", "Update1", "Error1"),
                            queryOneUpdates
                    );
                    assertEquals(
                            Arrays.asList("Message1", "Message2", "Message3", "Update1", "Error2"),
                            queryTwoUpdates
                    );
                });
    }

    @Test
    void completingSubscriptionExceptionallyInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        // given...
        UnitOfWork testUoW = UnitOfWorkTestUtils.aUnitOfWork();
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, TEST_QUERY_PAYLOAD, String.class);
        QueryMessage queryMessage = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        // when staging the subscription query and updates...
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        testUoW.runOnInvocation(context -> {
            queryBus.emitUpdate(testFilter, () -> testUpdate, context).join();
            queryBus.completeSubscriptionsExceptionally(testFilter, new RuntimeException(), context).join();
        });
        // then before we commit we don't have any updates yet...
        Optional<MessageStream.Entry<QueryResponseMessage>> peeked = result
                .filter(m -> m.message() instanceof SubscriptionQueryUpdateMessage)
                .peek();
        assertTrue(peeked.isEmpty());
        // when we execute the UoW, it commits...
        testUoW.execute().join();
        // then...
        Awaitility.await()
                  .atMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> {
                      assertEquals(TEST_QUERY_PAYLOAD,
                                   result.next().map(e -> e.message().payloadAs(String.class, CONVERTER)).orElse(null));
                      assertTrue(result.isCompleted() && result.error().isPresent());
                  });
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void completingSubscriptionQuery() {
        // given
        QueryMessage queryMessage = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        // when
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        scheduleAfterDelay(() -> {
            queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
            queryBus.completeSubscriptions(testFilter, testContext);
            queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        });
        // then
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message)
                                     .mapNotNull(m -> m.payloadAs(String.class, CONVERTER)))
                    .expectNext("Message1", "Message2", "Message3", "Update1")
                    .verifyComplete();
    }

    @Test
    void completingSubscriptionInUnitOfWorkLifecycleRunsUpdatesOnAfterCommit() {
        // given...
        List<String> expectedUpdates = Collections.singletonList(TEST_UPDATE_PAYLOAD);
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdate =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, TEST_UPDATE_PAYLOAD, String.class);
        QueryMessage queryMessage = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        UnitOfWork testUoW = UnitOfWorkTestUtils.aUnitOfWork();
        // when...
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        testUoW.runOnInvocation(context -> {
            queryBus.emitUpdate(testFilter, () -> testUpdate, context).join();
            queryBus.completeSubscriptions(testFilter, context).join();
        });
        // when...
        testUoW.runOnInvocation(context -> queryBus.emitUpdate(testFilter, () -> testUpdate, context).join());
        // then before we commit we don't have anything yet...
        List<String> updateList = new ArrayList<>();
        FluxUtils.of(result)
                 .filter(e -> e.message() instanceof SubscriptionQueryUpdateMessage)
                 .mapNotNull(e -> e.message().payloadAs(String.class, CONVERTER))
                 .subscribe(updateList::add);
        assertTrue(updateList.isEmpty());
        // when we execute the UoW, it commits...
        testUoW.execute().join();
        // then...
        await().atMost(Duration.ofSeconds(5))
               .untilAsserted(() -> assertEquals(expectedUpdates, updateList));
        assertTrue(result.isCompleted() && result.error().isEmpty());
    }

    @Test
    void orderingOfOperationOnUpdateHandler() {
        // given
        QualifiedName emitFirstThenReturnInitialQueryName = new QualifiedName(
                "test.emitFirstThenReturnInitial." + UUID.randomUUID());

        queryBus.subscribe(emitFirstThenReturnInitialQueryName, (query, context) -> {
            QueryUpdateEmitter emitter = QueryUpdateEmitter.forContext(context);
            CountDownLatch latch = new CountDownLatch(1);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                executor.submit(() -> {
                    emitter.emit(emitFirstThenReturnInitialQueryName,
                                 AbstractSubscriptionQueryTestSuite::equalsTestQueryPayload, "Update1");
                    emitter.emit(emitFirstThenReturnInitialQueryName,
                                 AbstractSubscriptionQueryTestSuite::equalsTestQueryPayload,
                                 "Update2");
                    emitter.complete(emitFirstThenReturnInitialQueryName,
                                     AbstractSubscriptionQueryTestSuite::equalsTestQueryPayload);
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return MessageStream.failed(e);
            }
            return MessageStream.just(new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, "Initial"));
        });

        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(emitFirstThenReturnInitialQueryName.fullName()), TEST_QUERY_PAYLOAD
        );
        // when
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // then
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message)
                                     .mapNotNull(m -> m.payloadAs(String.class, CONVERTER)))
                    .expectNext("Initial", "Update1", "Update2")
                    .verifyComplete();
    }

    private static boolean equalsTestQueryPayload(Object o) {
        return TEST_QUERY_PAYLOAD.equals(CONVERTER.convert(o, String.class));
    }

    //fixme: SimpleQueryBus throws for duplicated subscriptions, how it should work with AxonServer?
    @Test
    void doubleSubscriptionMessage() {
        // given
        QueryMessage queryMessage = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
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
        QualifiedName failingQueryName = new QualifiedName("test.failingQuery." + UUID.randomUUID());
        MessageType failingQueryType = new MessageType(failingQueryName.fullName());

        queryBus.subscribe(failingQueryName, (query, context) ->
                MessageStream.failed(new QueryExecutionException("Error handling query", toBeThrown, query))
        );

        QueryMessage queryMessage = new GenericQueryMessage(
                failingQueryType, TEST_QUERY_PAYLOAD
        );
        // when
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // then
        StepVerifier.create(FluxUtils.of(result).map(MessageStream.Entry::message))
                    .expectErrorMatches(assertQueryExecutionException(toBeThrown))
                    .verify();
    }

    @Nonnull
    private Predicate<Throwable> assertQueryExecutionException(Throwable toBeThrown) {
        return exception -> {
            if (exception instanceof QueryExecutionException qee) {
                // QueryExecutionException was serialized, so we don't have an original Exception type
                var queryExecutionCause = Optional.ofNullable(toBeThrown.getMessage()).orElse(toBeThrown.getClass()
                                                                                                        .getSimpleName());
                return qee.getCause().getMessage().contains(queryExecutionCause);
            }
            return exception.equals(toBeThrown);
        };
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void replayBufferOverflow() {
        // given...
        QueryMessage queryMessage = new GenericQueryMessage(
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        ProcessingContext testContext = null;
        // when...
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 100);
        scheduleAfterDelay(() -> {
            for (int i = 0; i <= 200; i++) {
                int number = i;
                queryBus.emitUpdate(testFilter,
                                    () -> new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE,
                                                                                    "Update" + number),
                                    testContext).join();
            }
            queryBus.completeSubscriptions(testFilter, testContext).join();
        });
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
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
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
                CHAT_MESSAGES_QUERY_TYPE, TEST_QUERY_PAYLOAD
        );
        Predicate<QueryMessage> testFilter =
                message -> CHAT_MESSAGES_QUERY_TYPE.equals(message.type())
                        && TEST_QUERY_PAYLOAD.equals(message.payloadAs(String.class, CONVERTER));
        SubscriptionQueryUpdateMessage testUpdateOne =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update1", String.class);
        SubscriptionQueryUpdateMessage testUpdateTwo =
                new GenericSubscriptionQueryUpdateMessage(TEST_UPDATE_PAYLOAD_TYPE, "Update2", String.class);
        ProcessingContext testContext = null;
        MessageStream<QueryResponseMessage> result = queryBus.subscriptionQuery(queryMessage, null, 50);
        // when...
        scheduleAfterDelay(() -> queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext));
        scheduleAfterDelay(() -> { // we give the update a time to be processed before disposing the subscription...
            result.close();
            queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
        });
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
        QualifiedName emitFirstThenReturnInitialQueryName = new QualifiedName(
                "test.emitFirstThenReturnInitial." + UUID.randomUUID());

        queryBus.subscribe(emitFirstThenReturnInitialQueryName, (query, context) -> {
            QueryUpdateEmitter emitter = QueryUpdateEmitter.forContext(context);
            CountDownLatch latch = new CountDownLatch(1);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                executor.submit(() -> {
                    emitter.emit(emitFirstThenReturnInitialQueryName,
                                 AbstractSubscriptionQueryTestSuite::equalsTestQueryPayload,
                                 "Update1");
                    emitter.emit(emitFirstThenReturnInitialQueryName,
                                 AbstractSubscriptionQueryTestSuite::equalsTestQueryPayload,
                                 "Update2");
                    emitter.complete(emitFirstThenReturnInitialQueryName,
                                     AbstractSubscriptionQueryTestSuite::equalsTestQueryPayload);
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return MessageStream.failed(e);
            }
            return MessageStream.just(new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, "Initial"));
        });

        QueryMessage queryMessage = new GenericQueryMessage(
                new MessageType(emitFirstThenReturnInitialQueryName.fullName()), TEST_QUERY_PAYLOAD
        );
        // when...
        List<String> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        MessageStream<QueryResponseMessage> stream = queryBus.subscriptionQuery(queryMessage, null, 50);
        FluxUtils.of(stream)
                 .map(MessageStream.Entry::message)
                 .mapNotNull(m -> m.payloadAs(String.class, CONVERTER))
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
        QualifiedName failingQueryName = new QualifiedName("test.failingQuery." + UUID.randomUUID());
        MessageType failingQueryType = new MessageType(failingQueryName.fullName());

        queryBus.subscribe(failingQueryName, (query, context) ->
                MessageStream.failed(new QueryExecutionException("Error handling query", toBeThrown, query))
        );

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
        MessageStream<QueryResponseMessage> stream = queryBus.subscriptionQuery(queryMessage, null, 50);
        FluxUtils.of(stream)
                 .map(MessageStream.Entry::message)
                 .mapNotNull(m -> m.payloadAs(String.class, CONVERTER))
                 .subscribe(initialResult::add);
        scheduleAfterDelay(() -> {
            queryBus.emitUpdate(testFilter, () -> testUpdateOne, testContext);
            queryBus.emitUpdate(testFilter, () -> testUpdateTwo, testContext);
            queryBus.completeSubscriptions(testFilter, testContext);
        });
        // then
        assertTrue(initialResult.isEmpty());
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void subscriptionQueryResultHandleWhenThereIsAnErrorOnUpdate() {
        // given
        QualifiedName failingQueryName = new QualifiedName("test.failingQuery." + UUID.randomUUID());
        MessageType failingQueryType = new MessageType(failingQueryName.fullName());

        queryBus.subscribe(failingQueryName, (query, context) ->
                MessageStream.failed(new QueryExecutionException("Error handling query", toBeThrown, query))
        );

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
        MessageStream<QueryResponseMessage> stream = queryBus.subscriptionQuery(queryMessage, null, 50);
        FluxUtils.of(stream)
                 .map(MessageStream.Entry::message)
                 .mapNotNull(m -> m.payloadAs(String.class, CONVERTER))
                 .subscribe(initialResult::add);

        scheduleAfterDelay(() -> {
            queryBus.completeSubscriptionsExceptionally(testFilter, new RuntimeException(), testContext);
            queryBus.emitUpdate(testFilter, () -> testUpdate, testContext);
        });
        // then
        assertTrue(initialResult.isEmpty());
    }

    @Test
    void queryGatewayCorrectlyReturnsNullOnSubscriptionQueryWithNullInitialResult()
            throws ExecutionException, InterruptedException {
        // given
        QualifiedName someQueryName = new QualifiedName("test.someQuery." + UUID.randomUUID());
        MessageType someQueryType = new MessageType(someQueryName.fullName());

        queryBus.subscribe(someQueryName, (query, context) -> {
            SomeQuery someQuery = query.payloadAs(SomeQuery.class, CONVERTER);
            String result = FOUND.equals(someQuery.filter()) ? FOUND : null;
            return MessageStream.just(new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, result));
        });

        QueryMessage queryMessage = new GenericQueryMessage(someQueryType, new SomeQuery("not " + FOUND));
        MessageStream<QueryResponseMessage> stream = queryBus.subscriptionQuery(queryMessage, null, 50);
        CompletableFuture<String> future = stream.first().asCompletableFuture()
                                                 .thenApply(e -> e.message().payloadAs(String.class, CONVERTER));
        scheduleAfterDelay(() -> queryBus.completeSubscriptions(message -> true, null));

        // todo: there is a difference between Distributed (we convert: null -> bytes[0] -> string) we ends with empty string and the Local (we just pass null)
        assertTrue(Strings.isNullOrEmpty(future.get()));
    }

    @Test
    void queryGatewayCorrectlyReturnsOnSubscriptionQuery() throws ExecutionException, InterruptedException {
        // given
        QualifiedName someQueryName = new QualifiedName("test.someQuery." + UUID.randomUUID());
        MessageType someQueryType = new MessageType(someQueryName.fullName());

        queryBus.subscribe(someQueryName, (query, context) -> {
            SomeQuery someQuery = query.payloadAs(SomeQuery.class, CONVERTER);
            String result = FOUND.equals(someQuery.filter()) ? FOUND : null;
            return MessageStream.just(new GenericQueryResponseMessage(TEST_RESPONSE_TYPE, result));
        });

        QueryMessage queryMessage = new GenericQueryMessage(someQueryType, new SomeQuery(FOUND));
        MessageStream<QueryResponseMessage> stream = queryBus.subscriptionQuery(queryMessage, null, 50);
        CompletableFuture<String> future = stream.first().asCompletableFuture()
                                                 .thenApply(e -> e.message().payloadAs(String.class, CONVERTER));
        String result = future.get();
        assertEquals(FOUND, result);
    }


    private record SomeQuery(String filter) {

    }

    private void scheduleAfterDelay(Runnable task) {
        CountDownLatch latch = new CountDownLatch(1);
        Thread.ofVirtual().start(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
