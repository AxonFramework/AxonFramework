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
import org.axonframework.common.Registration;
import org.axonframework.common.TypeReference;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.registration.DuplicateQueryHandlerResolution;
import org.axonframework.queryhandling.registration.DuplicateQueryHandlerSubscriptionException;
import org.axonframework.tracing.TestSpanFactory;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;
import static org.axonframework.common.ReflectionUtils.methodOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.axonframework.messaging.responsetypes.ResponseTypes.multipleInstancesOf;
import static org.axonframework.queryhandling.registration.DuplicateQueryHandlerResolution.silentlyAdd;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleQueryBus}.
 *
 * @author Marc Gathier
 */
class SimpleQueryBusTest {
    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {};
    private static final String TRACE_ID = "traceId";
    private static final String CORRELATION_ID = "correlationId";

    private final ResponseType<String> singleStringResponse = instanceOf(String.class);
    private final ResponseType<List<String>> multipleStringResponse = multipleInstancesOf(String.class);
    private SimpleQueryBus testSubject;
    private MessageMonitor<QueryMessage> messageMonitor;
    private QueryInvocationErrorHandler errorHandler;
    private MessageMonitor.MonitorCallback monitorCallback;
    private TestSpanFactory spanFactory;
    private QueryBusSpanFactory queryBusSpanFactory;
    private QueryUpdateEmitterSpanFactory queryUpdateEmitterSpanFactory;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        queryBusSpanFactory = DefaultQueryBusSpanFactory.builder().spanFactory(spanFactory).build();
        queryUpdateEmitterSpanFactory = DefaultQueryUpdateEmitterSpanFactory.builder().spanFactory(spanFactory).build();
        //noinspection unchecked
        messageMonitor = mock(MessageMonitor.class);
        errorHandler = mock(QueryInvocationErrorHandler.class);
        monitorCallback = mock(MessageMonitor.MonitorCallback.class);
        when(messageMonitor.onMessageIngested(any())).thenReturn(monitorCallback);

        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .errorHandler(errorHandler)
                                    .spanFactory(queryBusSpanFactory)
                                    .queryUpdateEmitter(SimpleQueryUpdateEmitter.builder()
                                                                                .spanFactory(
                                                                                        queryUpdateEmitterSpanFactory)
                                                                                .build())
                                    .duplicateQueryHandlerResolver(silentlyAdd())
                                    .build();

        MessageHandlerInterceptor<QueryMessage> correlationDataInterceptor =
                new CorrelationDataInterceptor<>(new MessageOriginProvider(CORRELATION_ID, TRACE_ID));
        testSubject.registerHandlerInterceptor(correlationDataInterceptor);
    }

    @Disabled("TODO: reintegrate as part of #3079")
    @Test
    public void handlerInterceptorThrowsException() throws ExecutionException, InterruptedException {
        testSubject.subscribe("test", String.class, (q, ctx) -> q.payload().toString());
        testSubject.registerHandlerInterceptor(( message,context, chain) ->
            MessageStream.failed(new RuntimeException("Faking"))
        );
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("test"), "hello", instanceOf(String.class)
        );

        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone());
        assertTrue(result.get().isExceptional());
    }

    @Test
    void subscribe() {
        testSubject.subscribe("test", String.class, (m, ctx) -> m.payload());

        assertEquals(1, testSubject.getSubscriptions().size());
        assertEquals(1, testSubject.getSubscriptions().values().iterator().next().size());

        testSubject.subscribe("test", String.class, (q, c) -> "aa" + q.payload());

        assertEquals(1, testSubject.getSubscriptions().size());
        assertEquals(2, testSubject.getSubscriptions().values().iterator().next().size());

        testSubject.subscribe("test2", String.class, (q, c) -> "aa" + q.payload());

        assertEquals(2, testSubject.getSubscriptions().size());
    }

    @Test
    void subscribingSameHandlerTwiceInvokedOnce() throws Exception {
        AtomicInteger invocationCount = new AtomicInteger();
        MessageHandler<QueryMessage, QueryResponseMessage> handler = (message, ctx) -> {
            invocationCount.incrementAndGet();
            return "reply";
        };
        Registration subscription = testSubject.subscribe("test", String.class, handler);
        testSubject.subscribe("test", String.class, handler);

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType("test"), "request", singleStringResponse
        );
        Object result = testSubject.query(testQuery).thenApply(QueryResponseMessage::payload).get();

        assertEquals("reply", result);
        assertEquals(1, invocationCount.get());
        assertTrue(subscription.cancel());
        assertTrue(testSubject.query(testQuery).isDone());
        assertTrue(testSubject.query(testQuery).isCompletedExceptionally());
    }

    @Test
    void subscribingSameQueryTwiceWithThrowingDuplicateResolver() {
        // Modify query bus with failing duplicate resolver
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .errorHandler(errorHandler)
                                    .duplicateQueryHandlerResolver(DuplicateQueryHandlerResolution.rejectDuplicates())
                                    .build();
        MessageHandler<QueryMessage, QueryResponseMessage> handlerOne = (message, ctx) -> "reply";
        MessageHandler<QueryMessage, QueryResponseMessage> handlerTwo = (message, ctx) -> "reply";
        testSubject.subscribe("test", String.class, handlerOne);
        assertThrows(DuplicateQueryHandlerSubscriptionException.class,
                     () -> testSubject.subscribe("test", String.class, handlerTwo));
    }

    /*
     * This test ensures that the QueryResponseMessage is created inside the scope of the Unit of Work, and therefore
     * contains the correlation data registered with the Unit of Work
     */
    @Disabled("TODO: reintegrate as part of #3079")
    @Test
    void queryResultContainsCorrelationData() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");

        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType(String.class), "hello", singleStringResponse)
                        .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertEquals("hello1234", result.get().payload());
        assertEquals(
                MetaData.with(CORRELATION_ID, testQuery.identifier()).and(TRACE_ID, "fakeTraceId"),
                result.get().metaData()
        );
    }

    @Disabled("TODO: reintegrate as part of #3079")
    @Test
    void nullResponseProperlyReturned() throws ExecutionException, InterruptedException {
        testSubject.subscribe(String.class.getName(), String.class, (p, ctx) -> null);
        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType(String.class), "hello", singleStringResponse)
                        .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertNull(result.get().payload());
        assertEquals(String.class, result.get().payloadType());
        assertEquals(
                // TODO: this assumes the correlation and tracing data gets into response
                // but this is done via interceptors, which are currently not integrated
                MetaData.with(CORRELATION_ID, testQuery.identifier()).and(TRACE_ID, "fakeTraceId"),
                result.get().metaData()
        );
    }

    @Disabled("TODO: reintegrate as part of #3079")
    @Test
    void queryWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .transactionManager(mockTxManager)
                                    .build();

        testSubject.subscribe(String.class.getName(),
                              methodOf(this.getClass(), "stringListQueryHandler").getGenericReturnType(),
                              (q, ctx) -> asList(q.payload() + "1234", q.payload() + "567"));

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
        testSubject = SimpleQueryBus.builder()
                                    .transactionManager(mockTxManager)
                                    .build();

        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");

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
    void querySingleIsTraced() throws ExecutionException, InterruptedException {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", singleStringResponse
        );
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
            spanFactory.verifySpanActive("QueryBus.query", testQuery);
            return q.payload() + "1234";
        });

        testSubject.query(testQuery).get();

        spanFactory.verifySpanCompleted("QueryBus.query", testQuery);
    }

    @Test
    void ScatterGatherIsTraced() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", multipleStringResponse
        );

        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery", testQuery);
            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery-0");
            return q.payload() + "1234";
        });
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery", testQuery);
            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery-1");
            return q.payload() + "12345678";
        });

        //noinspection ResultOfMethodCallIgnored
        testSubject.scatterGather(testQuery, 500, TimeUnit.MILLISECONDS).toList();

        spanFactory.verifySpanCompleted("QueryBus.scatterGatherQuery", testQuery);
        spanFactory.verifySpanCompleted("QueryBus.scatterGatherHandler-0");
        spanFactory.verifySpanCompleted("QueryBus.scatterGatherHandler-1");
    }


    @Test
    void queryListWithSingleHandlerReturnsSingleAsList() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", multipleStringResponse
        );
        CompletableFuture<List<String>> result = testSubject.query(testQuery)
                                                            .thenApply(m -> m.payloadAs(LIST_OF_STRINGS));

        assertEquals(1, result.get().size());
        assertEquals("hello1234", result.get().getFirst());
    }


    @Test
    void queryListWithBothSingleHandlerAndListHandlerReturnsListResult() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
        testSubject.subscribe(String.class.getName(), String[].class, (q, c) -> Arrays.asList(
                q.payload() + "1234", q.payload() + "5678"
        ));

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
        testSubject.subscribe("query", String.class, failingHandler);
        //noinspection FunctionalExpressionCanBeFolded,Convert2MethodRef,Convert2MethodRef
        testSubject.subscribe("query", String.class, (message, ctx) -> failingHandler.handleSync(message, ctx));
        testSubject.subscribe("query", String.class, passingHandler);

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
    void queryWithOnlyUnsuitableResultsInException() throws Exception {
        testSubject.subscribe("query", String.class, (message, ctx) -> {
            throw new NoHandlerForQueryException("Mock");
        });

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
    void queryReturnsResponseMessageFromHandlerAsIs() throws Exception {
        GenericQueryResponseMessage soleResult =
                new GenericQueryResponseMessage(new MessageType(String.class), "soleResult");
        testSubject.subscribe("query", String.class, (message, ctx) -> soleResult);

        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType("query"),
                                          "query",
                                          singleStringResponse);
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone());
        assertSame(result.get(), soleResult);
    }

    @Test
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
    void queryForSingleResultWillReportErrors() throws Exception {
        MessageHandler<? super QueryMessage, ? extends QueryResponseMessage> failingHandler = (message, ctx) -> {
            throw new MockException("Mock");
        };
        testSubject.subscribe("query", String.class, failingHandler);

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

    @Disabled("TODO: reintegrate as part of #3079")
    @Test
    void queryWithInterceptors() throws Exception {
        testSubject.registerDispatchInterceptor(
                (message, context, chain) ->
                        chain.proceed(message.andMetaData(Collections.singletonMap("key", "value")), context)
        );
        testSubject.registerHandlerInterceptor((message, context, chain) -> {
            if (message.metaData().containsKey("key")) {
                return MessageStream.just(new GenericQueryResponseMessage<>(new MessageType("response"), "fakeReply"));
            }
            return chain.proceed(message, context);
        });
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", singleStringResponse
        );
        CompletableFuture<Object> result = testSubject.query(testQuery)
                                                      .thenApply(QueryResponseMessage::payload);

        assertEquals("fakeReply", result.get());
    }

    @Test
    void queryDoesNotArriveAtUnsubscribedHandler() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + " is not here!").cancel();

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "hello", singleStringResponse
        );
        CompletableFuture<Object> result = testSubject.query(testQuery)
                                                      .thenApply(QueryResponseMessage::payload);

        assertEquals("1234", result.get());
    }

    @Test
    void queryReturnsException() throws Exception {
        MockException mockException = new MockException();
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
            throw mockException;
        });

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
        spanFactory.verifySpanHasException("QueryBus.query", NoHandlerForQueryException.class);
    }

    @Test
    void queryUnsubscribedHandlers() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + " is not here!").cancel();
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + " is not here!").cancel();

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
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportFailure(any());
    }

    @Test
    void scatterGather() {
        int expectedResults = 3;

        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "5678");
        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "90");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<QueryResponseMessage> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS)
                                                       .collect(toSet());

        assertEquals(expectedResults, results.size());
        Set<Object> resultSet = results.stream().map(Message::payload).collect(toSet());
        assertEquals(expectedResults, resultSet.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(3)).reportSuccess();
    }

    @Test
    void scatterGatherOnArrayQueryHandlers() throws NoSuchMethodException {
        int expectedQueryResponses = 3;
        int expectedResults = 6;

        testSubject.subscribe(String.class.getName(),
                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
                              (q, ctx) -> new String[]{q.payload() + "12", q.payload() + "34"});
        testSubject.subscribe(String.class.getName(),
                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
                              (q, ctx) -> new String[]{q.payload() + "56", q.payload() + "78"});
        testSubject.subscribe(String.class.getName(),
                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
                              (q, ctx) -> new String[]{q.payload() + "9", q.payload() + "0"});

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
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(3)).reportSuccess();
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
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .transactionManager(mockTxManager)
                                    .errorHandler(errorHandler)
                                    .duplicateQueryHandlerResolver(silentlyAdd())
                                    .build();

        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "567");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<Object> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(2, results.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
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
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .transactionManager(mockTxManager)
                                    .errorHandler(errorHandler)
                                    .duplicateQueryHandlerResolver(silentlyAdd())
                                    .build();

        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
            throw new MockException();
        });

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<Object> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(1, results.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
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
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .transactionManager(mockTxManager)
                                    .errorHandler(errorHandler)
                                    .duplicateQueryHandlerResolver(silentlyAdd())
                                    .build();

        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "567");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Optional<QueryResponseMessage> firstResult =
                testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).findFirst();

        assertTrue(firstResult.isPresent());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, atMost(2)).reportSuccess();
        // TODO reintegrate with #3079
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    @Disabled("TODO: reintegrate as part of #3079")
    void scatterGatherWithInterceptors() {
        testSubject.registerDispatchInterceptor(
                (message, context, chain) ->
                        chain.proceed(message.andMetaData(Collections.singletonMap("key", "value")), context)
        );
        testSubject.registerHandlerInterceptor((message, context, chain) -> {
            if (message.metaData().containsKey("key")) {
                return MessageStream.just(new GenericResultMessage<>(new MessageType("response"), "fakeReply"));
            }
            return chain.proceed(message, context);
        });
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "567");

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        List<Object> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS)
                                          .map(Message::payload)
                                          .collect(Collectors.toList());

        assertEquals(2, results.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        assertEquals(asList("fakeReply", "fakeReply"), results);
    }

    @Test
    void scatterGatherReturnsEmptyStreamWhenNoHandlersAvailable() {
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<Object> allResults = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(0, allResults.size());
        verify(messageMonitor).onMessageIngested(any());
        verify(monitorCallback).reportIgnored();
    }

    @Test
    void scatterGatherReportsExceptionsWithErrorHandler() {
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> {
            throw new MockException();
        });

        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        Set<Object> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(1, results.size());
        verify(errorHandler).onError(isA(MockException.class), eq(testQuery), isA(MessageHandler.class));
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
    }

    @Test
    @Disabled("TODO: reintegrate as part of #3079")
    void queryResponseMessageCorrelationData() throws ExecutionException, InterruptedException {
        testSubject.subscribe(String.class.getName(), String.class, (q, c) -> q.payload() + "1234");
        testSubject.registerHandlerInterceptor(new CorrelationDataInterceptor<>(new MessageOriginProvider()));
        QueryMessage testQuery = new GenericQueryMessage(
                new MessageType(String.class), "Hello, World", singleStringResponse
        );
        QueryResponseMessage queryResponseMessage = testSubject.query(testQuery).get();
        assertEquals(testQuery.identifier(), queryResponseMessage.metaData().get("traceId"));
        assertEquals(testQuery.identifier(), queryResponseMessage.metaData().get("correlationId"));
        assertEquals("Hello, World1234", queryResponseMessage.payload());
    }

    @Test
    void subscriptionQueryReportsExceptionInInitialResult() {
        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> {
            throw new MockException();
        });

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
        testSubject.subscribe("queryName", Long.class, (q, ctx) -> value.get());
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
                                    .doOnComplete(() -> updateEmitter.complete(query -> "queryName".equals(query.type().name())))
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
    void subscriptionQueryIsTraced() throws InterruptedException {
        CountDownLatch updatedLatch = new CountDownLatch(2);
        final AtomicLong value = new AtomicLong();
        testSubject.subscribe("queryName", Long.class, (q, ctx) -> value.get());
        QueryUpdateEmitter updateEmitter = testSubject.queryUpdateEmitter();
        Disposable disposable = Flux.interval(Duration.ofMillis(0), Duration.ofMillis(20))
                                    .doOnNext(next -> {
                                        updatedLatch.countDown();
                                        updateEmitter.emit(query -> "queryName".equals(query.type().name()), next);
                                    })
                                    .doOnComplete(() -> updateEmitter.complete(query -> "queryName".equals(query.type().name())))
                                    .subscribe();


        SubscriptionQueryMessage<String, Long, Long> testQuery = new GenericSubscriptionQueryMessage<>(
                new MessageType("queryName"), "test",
                instanceOf(Long.class), instanceOf(Long.class)
        );
        try {
            SubscriptionQueryResult<QueryResponseMessage, SubscriptionQueryUpdateMessage> result =
                    testSubject.subscriptionQuery(testQuery);
            Mono<QueryResponseMessage> initialResult = result.initialResult();
            Objects.requireNonNull(initialResult.block()).payload();
            spanFactory.verifySpanCompleted("QueryBus.query");
            updatedLatch.await();
            Objects.requireNonNull(result.updates().next().block()).payload();
            spanFactory.verifySpanCompleted("QueryUpdateEmitter.emitQueryUpdateMessage");
        } finally {
            disposable.dispose();
        }
    }

    @Test
    void queryReportsExceptionInResponseMessage() throws ExecutionException, InterruptedException {
        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> {
            throw new MockException();
        });

        CompletableFuture<QueryResponseMessage> result = testSubject.query(
                new GenericQueryMessage(new MessageType(String.class), "test", instanceOf(String.class))
        );
        assertFalse(result.thenApply(r -> false).exceptionally(MockException.class::isInstance).get(),
                    "Exception by handler should be reported in result, not on Mono");
        assertTrue(result.get().isExceptional());
    }

    @Test
    void queryHandlerDeclaresFutureResponseType() throws Exception {
        Type responseType = ReflectionUtils.methodOf(getClass(), "futureMethod").getGenericReturnType();
        testSubject.subscribe(String.class.getName(),
                              responseType,
                              (q, c) -> CompletableFuture.completedFuture(q.payload() + "1234"));

        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType(String.class), "hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertEquals("hello1234", result.get().payload());
    }

    @Test
    void queryHandlerDeclaresCompletableFutureResponseType() throws Exception {
        Type responseType = ReflectionUtils.methodOf(getClass(), "completableFutureMethod").getGenericReturnType();
        testSubject.subscribe(String.class.getName(),
                              responseType,
                              (q, c) -> CompletableFuture.completedFuture(q.payload() + "1234"));

        QueryMessage testQuery =
                new GenericQueryMessage(new MessageType(String.class), "hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage> result = testSubject.query(testQuery);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertEquals("hello1234", result.get().payload());
    }

    @Test
    void onSubscriptionQueryCancelTheActiveSubscriptionIsRemovedFromTheEmitterIfFluxIsNotSubscribed() {
        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "1234");

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
