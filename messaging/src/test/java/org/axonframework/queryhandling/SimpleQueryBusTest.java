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
package org.axonframework.queryhandling;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.TypeReference;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleQueryBus}.
 *
 * @author Marc Gathier
 */
class SimpleQueryBusTest {

    private static final QualifiedName QUERY_NAME = new QualifiedName("query");
    private static final MessageType QUERY_TYPE = new MessageType(QUERY_NAME);
    private static final QualifiedName RESPONSE_NAME = new QualifiedName(String.class);
    private static final MessageType RESPONSE_TYPE = new MessageType(RESPONSE_NAME);
    private static final QueryHandler SINGLE_RESPONSE_HANDLER = (query, context) -> {
        QueryResponseMessage response = new GenericQueryResponseMessage(RESPONSE_TYPE, query.payload() + "1234");
        return MessageStream.just(response);
    };
    private static final QueryHandler MULTI_RESPONSE_HANDLER = (query, context) -> {
        QueryResponseMessage responseOne = new GenericQueryResponseMessage(RESPONSE_TYPE, query.payload() + "1234");
        QueryResponseMessage responseTwo = new GenericQueryResponseMessage(RESPONSE_TYPE, query.payload() + "5678");
        return MessageStream.fromIterable(List.of(responseOne, responseTwo));
    };
    private static final ResponseType<String> SINGLE_STRING_RESPONSE = instanceOf(String.class);
    private static final ResponseType<List<String>> MULTI_STRING_RESPONSE = multipleInstancesOf(String.class);

    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };
    private static final String TRACE_ID = "traceId";
    private static final String CORRELATION_ID = "correlationId";

    private final ResponseType<String> singleStringResponse = instanceOf(String.class);
    private final ResponseType<List<String>> multipleStringResponse = multipleInstancesOf(String.class);
    private SimpleQueryBus testSubject;
    private QueryInvocationErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        errorHandler = mock(QueryInvocationErrorHandler.class);
        testSubject = new SimpleQueryBus(UnitOfWorkTestUtils.SIMPLE_FACTORY,
                                         SimpleQueryUpdateEmitter.builder().build());
    }

    @Nested
    class HandlerSubscriptions {

        @Test
        void subscribeAddsQueryHandlers() {
            // given...
            MockComponentDescriptor testDescriptor = new MockComponentDescriptor();
            // when first subscription...
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // then...
            testSubject.describeTo(testDescriptor);
            Map<QueryHandlerName, QueryHandler> subscriptions = testDescriptor.getProperty("subscriptions");
            assertThat(subscriptions.size()).isEqualTo(1);
            assertThat(subscriptions).containsValue(SINGLE_RESPONSE_HANDLER);
            // when second subscription with different query  name...
            testSubject.subscribe(new QualifiedName("test2"), RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // then...
            testSubject.describeTo(testDescriptor);
            subscriptions = testDescriptor.getProperty("subscriptions");
            assertThat(subscriptions.size()).isEqualTo(2);
            assertThat(subscriptions).containsValue(SINGLE_RESPONSE_HANDLER);
            // when third subscription with different response name...
            testSubject.subscribe(QUERY_NAME, new QualifiedName(Integer.class), SINGLE_RESPONSE_HANDLER);
            // then...
            testSubject.describeTo(testDescriptor);
            subscriptions = testDescriptor.getProperty("subscriptions");
            assertThat(subscriptions.size()).isEqualTo(3);
        }

        @Test
        void subscribingSameHandlerTwiceDoesNotThrowDuplicateQueryHandlerSubscriptionException() {
            // given...
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // when/then...
            assertDoesNotThrow(() -> testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER));
        }

        @Test
        void subscribingDifferentHandlerForSameNamesThrowsDuplicateQueryHandlerSubscriptionException() {
            // given...
            QueryHandler testHandler = (query, context) -> MessageStream.empty().cast();
            testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, SINGLE_RESPONSE_HANDLER);
            // when/then...
            assertThatThrownBy(() -> testSubject.subscribe(QUERY_NAME, RESPONSE_NAME, testHandler))
                    .isInstanceOf(DuplicateQueryHandlerSubscriptionException.class);
        }
    }

    /*
     * This test ensures that the QueryResponseMessage is created inside the scope of the Unit of Work, and therefore
     * contains the correlation data registered with the Unit of Work
     */
    @Disabled("TODO: reintegrate as part of #3079")
    @Test
    void queryResultContainsCorrelationData() throws Exception {
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");

        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType(String.class), "hello", singleStringResponse)
                        .andMetadata(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertEquals("hello1234", result.get().payload());
        assertEquals(
                Metadata.with(CORRELATION_ID, testQuery.identifier()).and(TRACE_ID, "fakeTraceId"),
                result.get().metadata()
        );
    }

    @Disabled("TODO: reintegrate as part of #3079")
    @Test
    void nullResponseProperlyReturned() throws ExecutionException, InterruptedException {
//        testSubject.subscribe(String.class.getName(), String.class, (p, ctx) -> null);
        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType(String.class), "hello", singleStringResponse)
                        .andMetadata(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertNull(result.get().payload());
        assertEquals(String.class, result.get().payloadType());
        assertEquals(
                // TODO: this assumes the correlation and tracing data gets into response
                // but this is done via interceptors, which are currently not integrated
                Metadata.with(CORRELATION_ID, testQuery.identifier()).and(TRACE_ID, "fakeTraceId"),
                result.get().metadata()
        );
    }

    @Disabled("TODO: reintegrate as part of #3079")
    @Test
    void queryWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(
                new TransactionalUnitOfWorkFactory(mockTxManager, UnitOfWorkTestUtils.SIMPLE_FACTORY),
                SimpleQueryUpdateEmitter.builder().build()
        );

//        testSubject.subscribe(String.class.getName(),
//                              methodOf(this.getClass(), "stringListQueryHandler").getGenericReturnType(),
//                              (q, ctx) -> asList(q.payload() + "1234", q.payload() + "567"));

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", multipleInstancesOf(String.class)
        );
        CompletableFuture<List<String>> result = testSubject.query(testQuery)
                                                            .thenApply(m -> m.payloadAs(LIST_OF_STRINGS));

        assertTrue(result.isDone());
        List<String> completedResult = result.get();
        assertTrue(completedResult.contains("hello1234"));
        assertTrue(completedResult.contains("hello567"));
        // TODO reintegrate with #3079
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @SuppressWarnings("unused") // Used by 'testQueryWithTransaction()' to generate query handler response type
    public List<String> stringListQueryHandler() {
        return new ArrayList<>();
    }

    @Disabled("TODO reintegrate with #3079")
    @Test
    void querySingleWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(
                new TransactionalUnitOfWorkFactory(mockTxManager, UnitOfWorkTestUtils.SIMPLE_FACTORY),
                SimpleQueryUpdateEmitter.builder().build()
        );

//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", singleStringResponse
        );
        CompletableFuture<Object> result = testSubject.query(testQuery)
                                                      .thenApply(QueryResponseMessage::payload);

        assertEquals("hello1234", result.get());
        // TODO reintegrate with #3079
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    @Disabled("TODO #3488")
    void queryListWithSingleHandlerReturnsSingleAsList() throws Exception {
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", multipleStringResponse
        );
        CompletableFuture<List<String>> result = testSubject.query(testQuery)
                                                            .thenApply(m -> m.payloadAs(LIST_OF_STRINGS));

        assertEquals(1, result.get().size());
        assertEquals("hello1234", result.get().getFirst());
    }

    @Test
    @Disabled("TODO #3488")
    void queryListWithBothSingleHandlerAndListHandlerReturnsListResult() throws Exception {
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
//        testSubject.subscribe(String.class.getName(), String[].class, (q, c) -> Arrays.asList(
//                q.payload() + "1234", q.payload() + "5678"
//        ));

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", multipleStringResponse
        );
        CompletableFuture<List<String>> result = testSubject.query(testQuery)
                                                            .thenApply(m -> m.payloadAs(LIST_OF_STRINGS));

        assertEquals(2, result.get().size());
        assertEquals("hello1234", result.get().get(0));
        assertEquals("hello5678", result.get().get(1));
    }

    @Test
    @Disabled("TODO #3488")
    void queryForSingleResultWithUnsuitableHandlers() throws Exception {
        AtomicInteger invocationCount = new AtomicInteger();
        MessageHandler<? super QueryMessage, ? extends QueryResponseMessage> failingHandler = (message, ctx) -> {
            invocationCount.incrementAndGet();
            throw new NoHandlerForQueryException("Mock");
        };
        MessageHandler<? super QueryMessage, ? extends QueryResponseMessage> passingHandler = (message, ctx) -> {
            invocationCount.incrementAndGet();
            return "reply";
        };
//        testSubject.subscribe("query", String.class, failingHandler);
        //noinspection FunctionalExpressionCanBeFolded,Convert2MethodRef,Convert2MethodRef
//        testSubject.subscribe("query", String.class, (message, ctx) -> failingHandler.handleSync(message, ctx));
//        testSubject.subscribe("query", String.class, passingHandler);

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("query"), "query", singleStringResponse
        );
        CompletableFuture<Object> result = testSubject.query(testQuery)
                                                      .thenApply(QueryResponseMessage::payload);

        assertTrue(result.isDone());
        assertEquals("reply", result.get());
        assertEquals(3, invocationCount.get());
    }

    @Test
    @Disabled("TODO #3488")
    void queryWithOnlyUnsuitableResultsInException() throws Exception {
//        testSubject.subscribe("query", String.class, (message, ctx) -> {
//            throw new NoHandlerForQueryException("Mock");
//        });

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "query", singleStringResponse
        );
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals("NoHandlerForQueryException", result.thenApply(QueryResponseMessage::payload)
                                                         .exceptionally(e -> e.getCause().getClass().getSimpleName())
                                                         .get());
    }

    @Test
    @Disabled("TODO #3488")
    void queryReturnsResponseMessageFromHandlerAsIs() throws Exception {
        GenericQueryResponseMessage soleResult =
                new GenericQueryResponseMessage(new MessageType(String.class), "soleResult");
//        testSubject.subscribe("query", String.class, (message, ctx) -> soleResult);

        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType("query"),
                                        "query",
                                        singleStringResponse);
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone());
        assertSame(result.get(), soleResult);
    }

    @Test
    @Disabled("TODO #3488")
    void queryWithHandlersResultsInException() throws Exception {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("query"), "query", singleStringResponse
        );
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals("NoHandlerForQueryException", result.thenApply(QueryResponseMessage::payload)
                                                         .exceptionally(e -> e.getCause().getClass().getSimpleName())
                                                         .get());
    }

    @Test
    @Disabled("TODO #3488")
    void queryForSingleResultWillReportErrors() throws Exception {
        MessageHandler<? super QueryMessage, ? extends QueryResponseMessage> failingHandler = (message, ctx) -> {
            throw new MockException("Mock");
        };
//        testSubject.subscribe("query", String.class, failingHandler);

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("query"), "query", singleStringResponse
        );
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        QueryResponseMessage queryResponseMessage = result.get();
        assertTrue(queryResponseMessage.isExceptional());
        assertEquals("Mock", queryResponseMessage.exceptionResult().getMessage());
    }

    @Test
    @Disabled("TODO #3488")
    void queryDoesNotArriveAtUnsubscribedHandler() throws Exception {
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> "1234");
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + " is not here!").cancel();

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", singleStringResponse
        );
        CompletableFuture<Object> result = testSubject.query(testQuery)
                                                      .thenApply(QueryResponseMessage::payload);

        assertEquals("1234", result.get());
    }

    @Test
    @Disabled("TODO #3488")
    void queryReturnsException() throws Exception {
        MockException mockException = new MockException();
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
//            throw mockException;
//        });

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", singleStringResponse
        );
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        QueryResponseMessage queryResponseMessage = result.get();
        assertTrue(queryResponseMessage.isExceptional());
        assertEquals(mockException, queryResponseMessage.exceptionResult());
    }

    @Test
    @Disabled("TODO #3488")
    void queryUnknown() throws Exception {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", singleStringResponse
        );
        CompletableFuture<?> result = testSubject.query(testQuery);

        try {
            result.get();
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertEquals(NoHandlerForQueryException.class, e.getCause().getClass());
        }
    }

    @Test
    @Disabled("TODO #3488")
    void scatterGather() {
        int expectedResults = 3;

//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "1234");
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "5678");
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "90");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<QueryResponseMessage> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS)
                                                       .collect(toSet());

        assertEquals(expectedResults, results.size());
        Set<Object> resultSet = results.stream().map(Message::payload).collect(toSet());
        assertEquals(expectedResults, resultSet.size());
    }

    @Test
    @Disabled("TODO #3488")
    void scatterGatherOnArrayQueryHandlers() throws NoSuchMethodException {
        int expectedQueryResponses = 3;
        int expectedResults = 6;

//        testSubject.subscribe(String.class.getName(),
//                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
//                              (q, ctx) -> new String[]{q.payload() + "12", q.payload() + "34"});
//        testSubject.subscribe(String.class.getName(),
//                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
//                              (q, ctx) -> new String[]{q.payload() + "56", q.payload() + "78"});
//        testSubject.subscribe(String.class.getName(),
//                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
//                              (q, ctx) -> new String[]{q.payload() + "9", q.payload() + "0"});

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", multipleInstancesOf(String.class)
        );
        Set<QueryResponseMessage> results =
                testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS)
                           .collect(toSet());

        assertEquals(expectedQueryResponses, results.size());
        Set<String> resultSet = results.stream()
                                       .map(m -> m.payloadAs(LIST_OF_STRINGS))
                                       .flatMap(Collection::stream)
                                       .collect(toSet());
        assertEquals(expectedResults, resultSet.size());
    }

    @SuppressWarnings("unused")// Used by 'testScatterGatherOnArrayQueryHandlers' to generate queryHandler responseType
    public String[] stringArrayQueryHandler() {
        return new String[]{};
    }

    @Disabled("TODO reintegrate with #3079")
    @Test
    void scatterGatherWithTransaction() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(
                new TransactionalUnitOfWorkFactory(mockTxManager, UnitOfWorkTestUtils.SIMPLE_FACTORY),
                SimpleQueryUpdateEmitter.builder().build()
        );

//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "567");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<Object> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(2, results.size());
        // TODO reintegrate with #3079
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(2)).commit();
    }

    @Disabled("TODO reintegrate with #3079")
    @Test
    void scatterGatherWithTransactionRollsBackOnFailure() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(
                new TransactionalUnitOfWorkFactory(mockTxManager, UnitOfWorkTestUtils.SIMPLE_FACTORY),
                SimpleQueryUpdateEmitter.builder().build()
        );

//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
//            throw new MockException();
//        });

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<Object> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(1, results.size());
        // TODO reintegrate with #3079
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(1)).commit();
        verify(mockTx, times(1)).rollback();
    }

    @Disabled("TODO reintegrate with #3079")
    @Test
    void queryFirstFromScatterGatherWillCommitUnitOfWork() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(
                new TransactionalUnitOfWorkFactory(mockTxManager, UnitOfWorkTestUtils.SIMPLE_FACTORY),
                SimpleQueryUpdateEmitter.builder().build()
        );

//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "567");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Optional<QueryResponseMessage> firstResult =
                testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).findFirst();

        assertTrue(firstResult.isPresent());
        // TODO reintegrate with #3079
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    @Disabled("TODO #3488")
    void scatterGatherReturnsEmptyStreamWhenNoHandlersAvailable() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<Object> allResults = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(0, allResults.size());
    }

    @Test
    @Disabled("TODO #3488")
    void scatterGatherReportsExceptionsWithErrorHandler() {
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
//        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
//            throw new MockException();
//        });

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<Object> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(1, results.size());
        verify(errorHandler).onError(isA(MockException.class), eq(testQuery), isA(MessageHandler.class));
    }

    @Test
    @Disabled("TODO #3488")
    void subscriptionQueryReportsExceptionInInitialResult() {
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> {
//            throw new MockException();
//        });

        SubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType(String.class), "test", instanceOf(String.class), instanceOf(String.class)
        );
        SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result =
                testSubject.subscriptionQuery(testQuery);
        Mono<QueryResponseMessage> initialResult = result.initialResult();
        //noinspection ConstantConditions
        assertFalse(initialResult.map(r -> false).onErrorReturn(MockException.class::isInstance, true).block(),
                    "Exception by handler should be reported in result, not on Mono");
        //noinspection ConstantConditions
        assertTrue(initialResult.block().isExceptional());
    }

    @Disabled("TODO together with #3079")
    @Test
    void subscriptionQueryIncreasingProjection() throws InterruptedException {
        CountDownLatch ten = new CountDownLatch(1);
        CountDownLatch hundred = new CountDownLatch(1);
        CountDownLatch thousand = new CountDownLatch(1);
        final AtomicLong value = new AtomicLong();
//        testSubject.subscribe("queryName", Long.class, (q, ctx) -> value.get());
        QueryUpdateEmitter updateEmitter = testSubject.queryUpdateEmitter();
        Disposable disposable = Flux.interval(Duration.ofMillis(0), Duration.ofMillis(3))
                                    .doOnNext(next -> {
                                        if (next == 10L) {
                                            ten.countDown();
                                        }
                                        if (next == 100L) {
                                            hundred.countDown();
                                        }
                                        if (next == 1000L) {
                                            thousand.countDown();
                                        }
                                        value.set(next);
                                        updateEmitter.emit(query -> "queryName".equals(query.type().name()), next);
                                    })
                                    .doOnComplete(() -> updateEmitter.complete(query -> "queryName".equals(query.type()
                                                                                                                .name())))
                                    .subscribe();


        SubscriptionQueryMessage<String, Long, Long> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("queryName"), "test",
                instanceOf(Long.class), instanceOf(Long.class)
        );
        SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result =
                testSubject.subscriptionQuery(testQuery);
        Mono<QueryResponseMessage> initialResult = result.initialResult();
        ten.await();
        Long firstInitialResult = Objects.requireNonNull(initialResult.block()).payloadAs(Long.class);
        hundred.await();
        Long fistUpdate = Objects.requireNonNull(result.updates().next().block()).payloadAs(Long.class);
        thousand.await();
        Long anotherInitialResult = Objects.requireNonNull(initialResult.block()).payloadAs(Long.class);
        assertTrue(fistUpdate <= firstInitialResult + 1);
        assertTrue(firstInitialResult <= anotherInitialResult);
        disposable.dispose();
    }

    @Test
    @Disabled("TODO #3488")
    void queryReportsExceptionInResponseMessage() throws ExecutionException, InterruptedException {
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> {
//            throw new MockException();
//        });

        CompletableFuture<QueryResponseMessage> result = testSubject.query(
                new GenericQueryMessage(new MessageType(String.class), "test", instanceOf(String.class))
        );
        assertFalse(result.thenApply(r -> false).exceptionally(MockException.class::isInstance).get(),
                    "Exception by handler should be reported in result, not on Mono");
        assertTrue(result.get().isExceptional());
    }

    @Test
    @Disabled("TODO #3488")
    void queryHandlerDeclaresFutureResponseType() throws Exception {
        Type responseType = ReflectionUtils.methodOf(getClass(), "futureMethod").getGenericReturnType();
//        testSubject.subscribe(String.class.getName(),
//                              responseType,
//                              (q, c) -> CompletableFuture.completedFuture(q.payload() + "1234"));

        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType(String.class), "hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertEquals("hello1234", result.get().payload());
    }

    @Test
    @Disabled("TODO #3488")
    void queryHandlerDeclaresCompletableFutureResponseType() throws Exception {
        Type responseType = ReflectionUtils.methodOf(getClass(), "completableFutureMethod").getGenericReturnType();
//        testSubject.subscribe(String.class.getName(),
//                              responseType,
//                              (q, c) -> CompletableFuture.completedFuture(q.payload() + "1234"));

        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType(String.class), "hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertEquals("hello1234", result.get().payload());
    }

    @Test
    @Disabled("TODO #3488")
    void onSubscriptionQueryCancelTheActiveSubscriptionIsRemovedFromTheEmitterIfFluxIsNotSubscribed() {
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "1234");

        SubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType(String.class), "test", instanceOf(String.class), instanceOf(String.class)
        );

        SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result =
                testSubject.subscriptionQuery(testQuery);

        result.cancel();
        assertEquals(0, testSubject.queryUpdateEmitter().activeSubscriptions().size());
    }

    @SuppressWarnings("unused")
    public Future<String> futureMethod() {
        return null;
    }

    @SuppressWarnings("unused")
    public CompletableFuture<String> completableFutureMethod() {
        return null;
    }
}
