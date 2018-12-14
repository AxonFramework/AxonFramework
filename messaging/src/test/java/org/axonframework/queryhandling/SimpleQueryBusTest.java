/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.utils.MockException;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;
import static org.axonframework.common.ReflectionUtils.methodOf;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SimpleQueryBusTest {

    private static final String TRACE_ID = "traceId";
    private static final String CORRELATION_ID = "correlationId";

    private SimpleQueryBus testSubject;
    private MessageMonitor<QueryMessage<?, ?>> messageMonitor;
    private QueryInvocationErrorHandler errorHandler;
    private MessageMonitor.MonitorCallback monitorCallback;

    private ResponseType<String> singleStringResponse = ResponseTypes.instanceOf(String.class);

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        messageMonitor = mock(MessageMonitor.class);
        errorHandler = mock(QueryInvocationErrorHandler.class);
        monitorCallback = mock(MessageMonitor.MonitorCallback.class);
        when(messageMonitor.onMessageIngested(any())).thenReturn(monitorCallback);

        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .errorHandler(errorHandler)
                                    .build();

        MessageHandlerInterceptor<QueryMessage<?, ?>> correlationDataInterceptor =
                new CorrelationDataInterceptor<>(new MessageOriginProvider(CORRELATION_ID, TRACE_ID));
        testSubject.registerHandlerInterceptor(correlationDataInterceptor);
    }

    @Test
    public void testSubscribe() {
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
    public void testSubscribingSameHandlerTwiceInvokedOnce() throws Exception {
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

    /*
     * This test ensures that the QueryResponseMessage is created inside the scope of the Unit of Work, and therefore
     * contains the correlation data registered with the Unit of Work
     */
    @Test
    public void testQueryResultContainsCorrelationData() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse)
                .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue("SimpleQueryBus should resolve CompletableFutures directly", result.isDone());
        assertEquals("hello1234", result.get().getPayload());
        assertEquals(
                MetaData.with(CORRELATION_ID, testQueryMessage.getIdentifier()).and(TRACE_ID, "fakeTraceId"),
                result.get().getMetaData()
        );
    }

    @Test
    public void testNullResponseProperlyReturned() throws ExecutionException, InterruptedException {
        testSubject.subscribe(String.class.getName(), String.class, p -> null);
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse)
                .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertTrue("SimpleQueryBus should resolve CompletableFutures directly", result.isDone());
        assertNull(result.get().getPayload());
        assertEquals(String.class, result.get().getPayloadType());
        assertEquals(
                MetaData.with(CORRELATION_ID, testQueryMessage.getIdentifier()).and(TRACE_ID, "fakeTraceId"),
                result.get().getMetaData()
        );
    }

    @Test
    public void testQueryWithTransaction() throws Exception {
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
    public void testQuerySingleWithTransaction() throws Exception {
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
    public void testQueryForSingleResultWithUnsuitableHandlers() throws Exception {
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
        //noinspection Convert2MethodRef
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
    public void testQueryWithOnlyUnsuitableResultsInException() throws Exception {
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
    public void testQueryReturnsResponseMessageFromHandlerAsIs() throws Exception {
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
    public void testQueryWithHandlersResultsInException() throws Exception {
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
    public void testQueryForSingleResultWillReportErrors() throws Exception {
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
    public void testQueryWithInterceptors() throws Exception {
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
    public void testQueryDoesNotArriveAtUnsubscribedHandler() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<String> result = testSubject.query(testQueryMessage)
                                                      .thenApply(QueryResponseMessage::getPayload);

        assertEquals("1234", result.get());
    }

    @Test
    public void testQueryReturnsException() throws Exception {
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
    public void testQueryUnknown() throws Exception {
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<?> result = testSubject.query(testQueryMessage);

        try {
            result.get();
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertEquals(NoHandlerForQueryException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testQueryUnsubscribedHandlers() throws Exception {
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
    public void testScatterGather() {
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
    public void testScatterGatherOnArrayQueryHandlers() throws NoSuchMethodException {
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
    public void testScatterGatherWithTransaction() {
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
    public void testScatterGatherWithTransactionRollsBackOnFailure() {
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
        Set<Object> resulst = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(1, resulst.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(1)).commit();
        verify(mockTx, times(1)).rollback();
    }

    @Test
    public void testQueryFirstFromScatterGatherWillCommitUnitOfWork() {
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
    public void testScatterGatherWithInterceptors() {
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
    public void testScatterGatherReturnsEmptyStreamWhenNoHandlersAvailable() {
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<Object> allResults = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(0, allResults.size());
        verify(messageMonitor).onMessageIngested(any());
        verify(monitorCallback).reportIgnored();
    }

    @Test
    public void testScatterGatherReportsExceptionsWithErrorHandler() {
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
    public void testQueryResponseMessageCorrelationData() throws ExecutionException, InterruptedException {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.registerHandlerInterceptor(new CorrelationDataInterceptor<>(new MessageOriginProvider()));
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        QueryResponseMessage<String> queryResponseMessage = testSubject.query(testQueryMessage).get();
        assertEquals(testQueryMessage.getIdentifier(), queryResponseMessage.getMetaData().get("traceId"));
        assertEquals(testQueryMessage.getIdentifier(), queryResponseMessage.getMetaData().get("correlationId"));
        assertEquals("Hello, World1234", queryResponseMessage.getPayload());
    }
}
