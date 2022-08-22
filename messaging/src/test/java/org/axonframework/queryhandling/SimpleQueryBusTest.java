/*
 * Copyright (c) 2010-2022. Axon Framework
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
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.registration.DuplicateQueryHandlerResolution;
import org.axonframework.queryhandling.registration.DuplicateQueryHandlerSubscriptionException;
import org.axonframework.tracing.TestSpanFactory;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleQueryBus}.
 *
 * @author Marc Gathier
 */
class SimpleQueryBusTest {

    private static final String TRACE_ID = "traceId";
    private static final String CORRELATION_ID = "correlationId";
    private final ResponseType<String> singleStringResponse = ResponseTypes.instanceOf(String.class);
    private final ResponseType<List<String>> multipleStringResponse = ResponseTypes.multipleInstancesOf(String.class);
    private SimpleQueryBus testSubject;
    private MessageMonitor<QueryMessage<?, ?>> messageMonitor;
    private QueryInvocationErrorHandler errorHandler;
    private MessageMonitor.MonitorCallback monitorCallback;
    private TestSpanFactory spanFactory;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        messageMonitor = mock(MessageMonitor.class);
        errorHandler = mock(QueryInvocationErrorHandler.class);
        monitorCallback = mock(MessageMonitor.MonitorCallback.class);
        when(messageMonitor.onMessageIngested(any())).thenReturn(monitorCallback);

        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .errorHandler(errorHandler)
                                    .spanFactory(spanFactory)
                                    .queryUpdateEmitter(SimpleQueryUpdateEmitter.builder()
                                                                                .spanFactory(spanFactory)
                                                                                .build())
                                    .build();

        MessageHandlerInterceptor<QueryMessage<?, ?>> correlationDataInterceptor =
                new CorrelationDataInterceptor<>(new MessageOriginProvider(CORRELATION_ID, TRACE_ID));
        testSubject.registerHandlerInterceptor(correlationDataInterceptor);
    }

    @Test
    public void testHandlerInterceptorThrowsException() throws ExecutionException, InterruptedException {
        testSubject.subscribe("test", String.class, q -> q.getPayload().toString());

        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            throw new RuntimeException("Faking");
        });

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(
                new GenericQueryMessage<>("hello", "test", ResponseTypes.instanceOf(String.class))
        );

        assertTrue(result.isDone());
        assertTrue(result.get().isExceptional());
    }

    @Test
    void testSubscribe() {
        testSubject.subscribe("test", String.class, Message::getPayload);

        assertEquals(1, testSubject.getSubscriptions().size());
        assertEquals(1, testSubject.getSubscriptions().values().iterator().next().size());

        testSubject.subscribe("test", String.class, (q) -> "aa" + q.getPayload());

        assertEquals(1, testSubject.getSubscriptions().size());
        assertEquals(2, testSubject.getSubscriptions().values().iterator().next().size());

        testSubject.subscribe("test2", String.class, (q) -> "aa" + q.getPayload());

        assertEquals(2, testSubject.getSubscriptions().size());
    }

    @Test
    void testSubscribingSameHandlerTwiceInvokedOnce() throws Exception {
        AtomicInteger invocationCount = new AtomicInteger();
        MessageHandler<QueryMessage<?, String>> handler = message -> {
            invocationCount.incrementAndGet();
            return "reply";
        };
        Registration subscription = testSubject.subscribe("test", String.class, handler);
        testSubject.subscribe("test", String.class, handler);

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("request", "test", singleStringResponse);
        String result = testSubject.query(testQueryMessage).thenApply(QueryResponseMessage::getPayload).get();

        assertEquals("reply", result);
        assertEquals(1, invocationCount.get());
        assertTrue(subscription.cancel());
        assertTrue(testSubject.query(testQueryMessage).isDone());
        assertTrue(testSubject.query(testQueryMessage).isCompletedExceptionally());
    }

    @Test
    void testSubscribingSameQueryTwiceWithThrowingDuplicateResolver() throws Exception {
        // Modify query bus with failing duplicate resolver
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .errorHandler(errorHandler)
                                    .duplicateQueryHandlerResolver(DuplicateQueryHandlerResolution.rejectDuplicates())
                                    .build();
        MessageHandler<QueryMessage<?, String>> handlerOne = message -> "reply";
        MessageHandler<QueryMessage<?, String>> handlerTwo = message -> "reply";
        testSubject.subscribe("test", String.class, handlerOne);
        assertThrows(DuplicateQueryHandlerSubscriptionException.class, () -> {
            testSubject.subscribe("test", String.class, handlerTwo);
        });
    }

    /*
     * This test ensures that the QueryResponseMessage is created inside the scope of the Unit of Work, and therefore
     * contains the correlation data registered with the Unit of Work
     */
    @Test
    void testQueryResultContainsCorrelationData() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse)
                .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertEquals("hello1234", result.get().getPayload());
        assertEquals(
                MetaData.with(CORRELATION_ID, testQueryMessage.getIdentifier()).and(TRACE_ID, "fakeTraceId"),
                result.get().getMetaData()
        );
    }

    @Test
    void testNullResponseProperlyReturned() throws ExecutionException, InterruptedException {
        testSubject.subscribe(String.class.getName(), String.class, p -> null);
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse)
                .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertNull(result.get().getPayload());
        assertEquals(String.class, result.get().getPayloadType());
        assertEquals(
                MetaData.with(CORRELATION_ID, testQueryMessage.getIdentifier()).and(TRACE_ID, "fakeTraceId"),
                result.get().getMetaData()
        );
    }

    @Test
    void testQueryWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .transactionManager(mockTxManager)
                                    .build();

        testSubject.subscribe(String.class.getName(),
                              methodOf(this.getClass(), "stringListQueryHandler").getGenericReturnType(),
                              q -> asList(q.getPayload() + "1234", q.getPayload() + "567"));

        QueryMessage<String, List<String>> testQueryMessage =
                new GenericQueryMessage<>("hello", ResponseTypes.multipleInstancesOf(String.class));
        CompletableFuture<List<String>> result = testSubject.query(testQueryMessage)
                                                            .thenApply(QueryResponseMessage::getPayload);

        assertTrue(result.isDone());
        List<String> completedResult = result.get();
        assertTrue(completedResult.contains("hello1234"));
        assertTrue(completedResult.contains("hello567"));
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @SuppressWarnings("unused") // Used by 'testQueryWithTransaction()' to generate query handler response type
    public List<String> stringListQueryHandler() {
        return new ArrayList<>();
    }

    @Test
    void testQuerySingleWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .transactionManager(mockTxManager)
                                    .build();

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<String> result = testSubject.query(testQueryMessage)
                                                      .thenApply(QueryResponseMessage::getPayload);

        assertEquals("hello1234", result.get());
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    void testQuerySingleIsTraced() throws ExecutionException, InterruptedException {
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            spanFactory.verifySpanActive("SimpleQueryBus.query", testQueryMessage);
            return q.getPayload() + "1234";
        });

        testSubject.query(testQueryMessage).get();

        spanFactory.verifySpanCompleted("SimpleQueryBus.query", testQueryMessage);
    }

    @Test
    void testScatterGatherIsTraced() {
        QueryMessage<String, List<String>> testQueryMessage = new GenericQueryMessage<>("hello",
                                                                                        multipleStringResponse);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            spanFactory.verifySpanActive("SimpleQueryBus.scatterGather(0)", testQueryMessage);
            return q.getPayload() + "1234";
        });
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            spanFactory.verifySpanActive("SimpleQueryBus.scatterGather(1)", testQueryMessage);
            return q.getPayload() + "12345678";
        });

        testSubject.scatterGather(testQueryMessage, 500, TimeUnit.MILLISECONDS).collect(Collectors.toList());

        spanFactory.verifySpanCompleted("SimpleQueryBus.scatterGather(0)", testQueryMessage);
        spanFactory.verifySpanCompleted("SimpleQueryBus.scatterGather(1)", testQueryMessage);
    }


    @Test
    void testQueryListWithSingleHandlerReturnsSingleAsList() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");

        QueryMessage<String, List<String>> testQueryMessage = new GenericQueryMessage<>("hello",
                                                                                        multipleStringResponse);
        CompletableFuture<List<String>> result = testSubject.query(testQueryMessage)
                                                            .thenApply(QueryResponseMessage::getPayload);

        assertEquals(1, result.get().size());
        assertEquals("hello1234", result.get().get(0));
    }


    @Test
    void testQueryListWithBothSingleHandlerAndListHandlerReturnsListResult() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String[].class, (q) -> Arrays.asList(
                q.getPayload() + "1234", q.getPayload() + "5678"
        ));

        QueryMessage<String, List<String>> testQueryMessage = new GenericQueryMessage<>("hello",
                                                                                        multipleStringResponse);
        CompletableFuture<List<String>> result = testSubject.query(testQueryMessage)
                                                            .thenApply(QueryResponseMessage::getPayload);

        assertEquals(2, result.get().size());
        assertEquals("hello1234", result.get().get(0));
        assertEquals("hello5678", result.get().get(1));
    }

    @Test
    void testQueryForSingleResultWithUnsuitableHandlers() throws Exception {
        AtomicInteger invocationCount = new AtomicInteger();
        MessageHandler<? super QueryMessage<?, ?>> failingHandler = message -> {
            invocationCount.incrementAndGet();
            throw new NoHandlerForQueryException("Mock");
        };
        MessageHandler<? super QueryMessage<?, String>> passingHandler = message -> {
            invocationCount.incrementAndGet();
            return "reply";
        };
        testSubject.subscribe("query", String.class, failingHandler);
        //noinspection FunctionalExpressionCanBeFolded,Convert2MethodRef,Convert2MethodRef
        testSubject.subscribe("query", String.class, message -> failingHandler.handle(message));
        testSubject.subscribe("query", String.class, passingHandler);

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<String> result = testSubject.query(testQueryMessage)
                                                      .thenApply(QueryResponseMessage::getPayload);

        assertTrue(result.isDone());
        assertEquals("reply", result.get());
        assertEquals(3, invocationCount.get());
    }

    @Test
    void testQueryWithOnlyUnsuitableResultsInException() throws Exception {
        testSubject.subscribe("query", String.class, message -> {
            throw new NoHandlerForQueryException("Mock");
        });

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals("NoHandlerForQueryException", result.thenApply(QueryResponseMessage::getPayload)
                                                         .exceptionally(e -> e.getCause().getClass().getSimpleName())
                                                         .get());
    }

    @Test
    void testQueryReturnsResponseMessageFromHandlerAsIs() throws Exception {
        GenericQueryResponseMessage<String> soleResult =
                new GenericQueryResponseMessage<>("soleResult");
        testSubject.subscribe("query", String.class, message -> soleResult);

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue(result.isDone());
        assertSame(result.get(), soleResult);
    }

    @Test
    void testQueryWithHandlersResultsInException() throws Exception {
        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals("NoHandlerForQueryException", result.thenApply(QueryResponseMessage::getPayload)
                                                         .exceptionally(e -> e.getCause().getClass().getSimpleName())
                                                         .get());
    }

    @Test
    void testQueryForSingleResultWillReportErrors() throws Exception {
        MessageHandler<? super QueryMessage<?, ?>> failingHandler = message -> {
            throw new MockException("Mock");
        };
        testSubject.subscribe("query", String.class, failingHandler);

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        QueryResponseMessage<String> queryResponseMessage = result.get();
        assertTrue(queryResponseMessage.isExceptional());
        assertEquals("Mock", queryResponseMessage.exceptionResult().getMessage());
    }

    @Test
    void testQueryWithInterceptors() throws Exception {
        testSubject.registerDispatchInterceptor(
                messages -> (i, m) -> m.andMetaData(Collections.singletonMap("key", "value"))
        );
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (unitOfWork.getMessage().getMetaData().containsKey("key")) {
                return "fakeReply";
            }
            return interceptorChain.proceed();
        });
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<String> result = testSubject.query(testQueryMessage)
                                                      .thenApply(QueryResponseMessage::getPayload);

        assertEquals("fakeReply", result.get());
    }

    @Test
    void testQueryDoesNotArriveAtUnsubscribedHandler() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<String> result = testSubject.query(testQueryMessage)
                                                      .thenApply(QueryResponseMessage::getPayload);

        assertEquals("1234", result.get());
    }

    @Test
    void testQueryReturnsException() throws Exception {
        MockException mockException = new MockException();
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw mockException;
        });

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        QueryResponseMessage<String> queryResponseMessage = result.get();
        assertTrue(queryResponseMessage.isExceptional());
        assertEquals(mockException, queryResponseMessage.exceptionResult());
    }

    @Test
    void testQueryUnknown() throws Exception {
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<?> result = testSubject.query(testQueryMessage);

        try {
            result.get();
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertEquals(NoHandlerForQueryException.class, e.getCause().getClass());
        }
        spanFactory.verifySpanHasException("SimpleQueryBus.query", NoHandlerForQueryException.class);
    }

    @Test
    void testQueryUnsubscribedHandlers() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<?> result = testSubject.query(testQueryMessage);

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
    void testScatterGather() {
        int expectedResults = 3;

        testSubject.subscribe(String.class.getName(), String.class, q -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, q -> q.getPayload() + "5678");
        testSubject.subscribe(String.class.getName(), String.class, q -> q.getPayload() + "90");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<QueryResponseMessage<String>> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS)
                                                               .collect(toSet());

        assertEquals(expectedResults, results.size());
        Set<String> resultSet = results.stream().map(Message::getPayload).collect(toSet());
        assertEquals(expectedResults, resultSet.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(3)).reportSuccess();
    }

    @Test
    void testScatterGatherOnArrayQueryHandlers() throws NoSuchMethodException {
        int expectedQueryResponses = 3;
        int expectedResults = 6;

        testSubject.subscribe(String.class.getName(),
                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
                              q -> new String[]{q.getPayload() + "12", q.getPayload() + "34"});
        testSubject.subscribe(String.class.getName(),
                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
                              q -> new String[]{q.getPayload() + "56", q.getPayload() + "78"});
        testSubject.subscribe(String.class.getName(),
                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
                              q -> new String[]{q.getPayload() + "9", q.getPayload() + "0"});

        QueryMessage<String, List<String>> testQueryMessage =
                new GenericQueryMessage<>("Hello, World", ResponseTypes.multipleInstancesOf(String.class));
        Set<QueryResponseMessage<List<String>>> results =
                testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS)
                           .collect(toSet());

        assertEquals(expectedQueryResponses, results.size());
        Set<String> resultSet = results.stream()
                                       .map(Message::getPayload)
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

    @Test
    void testScatterGatherWithTransaction() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .transactionManager(mockTxManager)
                                    .errorHandler(errorHandler)
                                    .build();

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<Object> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(2, results.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(2)).commit();
    }

    @Test
    void testScatterGatherWithTransactionRollsBackOnFailure() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .transactionManager(mockTxManager)
                                    .errorHandler(errorHandler)
                                    .build();

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw new MockException();
        });

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<Object> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(1, results.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(1)).commit();
        verify(mockTx, times(1)).rollback();
    }

    @Test
    void testQueryFirstFromScatterGatherWillCommitUnitOfWork() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .transactionManager(mockTxManager)
                                    .errorHandler(errorHandler)
                                    .build();

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Optional<QueryResponseMessage<String>> firstResult =
                testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).findFirst();

        assertTrue(firstResult.isPresent());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, atMost(2)).reportSuccess();
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    void testScatterGatherWithInterceptors() {
        testSubject.registerDispatchInterceptor(
                messages -> (i, m) -> m.andMetaData(Collections.singletonMap("key", "value"))
        );
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (unitOfWork.getMessage().getMetaData().containsKey("key")) {
                return "fakeReply";
            }
            return interceptorChain.proceed();
        });
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        List<String> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS)
                                          .map(Message::getPayload)
                                          .collect(Collectors.toList());

        assertEquals(2, results.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        assertEquals(asList("fakeReply", "fakeReply"), results);
    }

    @Test
    void testScatterGatherReturnsEmptyStreamWhenNoHandlersAvailable() {
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<Object> allResults = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(0, allResults.size());
        verify(messageMonitor).onMessageIngested(any());
        verify(monitorCallback).reportIgnored();
    }

    @Test
    void testScatterGatherReportsExceptionsWithErrorHandler() {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw new MockException();
        });

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<Object> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(1, results.size());
        verify(errorHandler).onError(isA(MockException.class), eq(testQueryMessage), isA(MessageHandler.class));
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
    }

    @Test
    void testQueryResponseMessageCorrelationData() throws ExecutionException, InterruptedException {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.registerHandlerInterceptor(new CorrelationDataInterceptor<>(new MessageOriginProvider()));
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        QueryResponseMessage<String> queryResponseMessage = testSubject.query(testQueryMessage).get();
        assertEquals(testQueryMessage.getIdentifier(), queryResponseMessage.getMetaData().get("traceId"));
        assertEquals(testQueryMessage.getIdentifier(), queryResponseMessage.getMetaData().get("correlationId"));
        assertEquals("Hello, World1234", queryResponseMessage.getPayload());
    }

    @Test
    void testSubscriptionQueryReportsExceptionInInitialResult() {
        testSubject.subscribe(String.class.getName(), String.class, q -> {
            throw new MockException();
        });

        SubscriptionQueryResult<QueryResponseMessage<String>, SubscriptionQueryUpdateMessage<String>> result = testSubject
                .subscriptionQuery(new GenericSubscriptionQueryMessage<>("test",
                                                                         ResponseTypes.instanceOf(String.class),
                                                                         ResponseTypes.instanceOf(String.class)));
        Mono<QueryResponseMessage<String>> initialResult = result.initialResult();
        //noinspection ConstantConditions
        assertFalse(initialResult.map(r -> false).onErrorReturn(MockException.class::isInstance, true).block(),
                    "Exception by handler should be reported in result, not on Mono");
        //noinspection ConstantConditions
        assertTrue(initialResult.block().isExceptional());
    }

    @Test
    void testSubscriptionQueryIncreasingProjection() throws InterruptedException {
        CountDownLatch ten = new CountDownLatch(1);
        CountDownLatch hundred = new CountDownLatch(1);
        CountDownLatch thousand = new CountDownLatch(1);
        final AtomicLong value = new AtomicLong();
        testSubject.subscribe("queryName", Long.class, q -> value.get());
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
                                        updateEmitter.emit(query -> "queryName".equals(query.getQueryName()), next);
                                    })
                                    .doOnComplete(() -> updateEmitter.complete(query -> "queryName".equals(query.getQueryName())))
                                    .subscribe();


        SubscriptionQueryResult<QueryResponseMessage<Long>, SubscriptionQueryUpdateMessage<Long>> result = testSubject
                .subscriptionQuery(new GenericSubscriptionQueryMessage<>("test",
                                                                         "queryName",
                                                                         ResponseTypes.instanceOf(Long.class),
                                                                         ResponseTypes.instanceOf(Long.class)));
        Mono<QueryResponseMessage<Long>> initialResult = result.initialResult();
        ten.await();
        Long firstInitialResult = Objects.requireNonNull(initialResult.block()).getPayload();
        hundred.await();
        Long fistUpdate = Objects.requireNonNull(result.updates().next().block()).getPayload();
        thousand.await();
        Long anotherInitialResult = Objects.requireNonNull(initialResult.block()).getPayload();
        assertTrue(fistUpdate <= firstInitialResult + 1);
        assertTrue(firstInitialResult <= anotherInitialResult);
        disposable.dispose();
    }

    @Test
    void testSubscriptionQueryIsTraced() throws InterruptedException {
        CountDownLatch updatedLatch = new CountDownLatch(2);
        final AtomicLong value = new AtomicLong();
        testSubject.subscribe("queryName", Long.class, q -> value.get());
        QueryUpdateEmitter updateEmitter = testSubject.queryUpdateEmitter();
        Disposable disposable = Flux.interval(Duration.ofMillis(0), Duration.ofMillis(20))
                                    .doOnNext(next -> {
                                        updatedLatch.countDown();
                                        updateEmitter.emit(query -> "queryName".equals(query.getQueryName()), next);
                                    })
                                    .doOnComplete(() -> updateEmitter.complete(query -> "queryName".equals(query.getQueryName())))
                                    .subscribe();


        SubscriptionQueryResult<QueryResponseMessage<Long>, SubscriptionQueryUpdateMessage<Long>> result = testSubject
                .subscriptionQuery(new GenericSubscriptionQueryMessage<>("test",
                                                                         "queryName",
                                                                         ResponseTypes.instanceOf(Long.class),
                                                                         ResponseTypes.instanceOf(Long.class)));
        Mono<QueryResponseMessage<Long>> initialResult = result.initialResult();
        Objects.requireNonNull(initialResult.block()).getPayload();
        spanFactory.verifySpanCompleted("SimpleQueryBus.query");
        updatedLatch.await();
        Objects.requireNonNull(result.updates().next().block()).getPayload();
        spanFactory.verifySpanCompleted("QueryUpdateEmitter.emit queryName");
        disposable.dispose();
    }

    @Test
    void testQueryReportsExceptionInResponseMessage() throws ExecutionException, InterruptedException {
        testSubject.subscribe(String.class.getName(), String.class, q -> {
            throw new MockException();
        });

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(
                new GenericQueryMessage<>("test", ResponseTypes.instanceOf(String.class))
        );
        assertFalse(result.thenApply(r -> false).exceptionally(MockException.class::isInstance).get(),
                    "Exception by handler should be reported in result, not on Mono");
        assertTrue(result.get().isExceptional());
    }

    @Test
    void testQueryHandlerDeclaresFutureResponseType() throws Exception {
        Type responseType = ReflectionUtils.methodOf(getClass(), "futureMethod").getGenericReturnType();
        testSubject.subscribe(String.class.getName(),
                              responseType,
                              (q) -> CompletableFuture.completedFuture(q.getPayload() + "1234"));

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertEquals("hello1234", result.get().getPayload());
    }

    @Test
    void testQueryHandlerDeclaresCompletableFutureResponseType() throws Exception {
        Type responseType = ReflectionUtils.methodOf(getClass(), "completableFutureMethod").getGenericReturnType();
        testSubject.subscribe(String.class.getName(),
                              responseType,
                              (q) -> CompletableFuture.completedFuture(q.getPayload() + "1234"));

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue(result.isDone(), "SimpleQueryBus should resolve CompletableFutures directly");
        assertEquals("hello1234", result.get().getPayload());
    }

    @Test
    void testOnSubscriptionQueryCancelTheActiveSubscriptionIsRemovedFromTheEmitterIfFluxIsNotSubscribed() {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, q -> q.getPayload() + "1234");

        SubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                "test", ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class)
        );

        //noinspection resource
        SubscriptionQueryResult<QueryResponseMessage<String>, SubscriptionQueryUpdateMessage<String>> result =
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
