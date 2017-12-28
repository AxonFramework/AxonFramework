/*
 * Copyright (c) 2010-2017. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.queryhandling;

import org.axonframework.common.MockException;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.monitoring.MessageMonitor;
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
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * Author: marc
 */
public class SimpleQueryBusTest {
    private static final String TRACE_ID = "traceId";
    private static final String CORRELATION_ID = "correlationId";

    private SimpleQueryBus testSubject;
    private MessageMonitor<QueryMessage<?, ?>> messageMonitor;
    private QueryInvocationErrorHandler errorHandler;
    private MessageMonitor.MonitorCallback monitorCallback;

    @Before
    public void setUp() throws Exception {
        messageMonitor = mock(MessageMonitor.class);
        errorHandler = mock(QueryInvocationErrorHandler.class);
        monitorCallback = mock(MessageMonitor.MonitorCallback.class);
        when(messageMonitor.onMessageIngested(any())).thenReturn(monitorCallback);

        testSubject = new SimpleQueryBus(messageMonitor, null, errorHandler);
        testSubject.registerHandlerInterceptor(new CorrelationDataInterceptor<>(new MessageOriginProvider(CORRELATION_ID, TRACE_ID)));
    }

    @Test
    public void subscribe() {
        testSubject.subscribe("test", String.class, Message::getPayload);
        assertEquals(1, testSubject.getSubscriptions().size());
        assertEquals(1, testSubject.getSubscriptions().values().iterator().next().size());
        testSubject.subscribe("test", String.class, (q) -> "aa" + q.getPayload());
        assertEquals(1, testSubject.getSubscriptions().size());
        assertEquals(2, testSubject.getSubscriptions().values().iterator().next().size());
        testSubject.subscribe("test2", String.class, (q) -> "aa" + q.getPayload());
        assertEquals(2, testSubject.getSubscriptions().size());
    }

    /*
     * This test ensures that the QueryResponseMessage is created inside the scope of the Unit of Work, and therefore
     * contains the correlation data registered with the Unit of Work
     */
    @Test
    public void queryResultContainsCorrelationData() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class)
                .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(queryMessage);
        assertTrue("SimpleQueryBus should resolve CompletableFutures directly", result.isDone());
        assertEquals("hello1234", result.get().getFirstResult());
        assertEquals(1, result.get().getResults().size());
        assertEquals(MetaData.with(CORRELATION_ID, queryMessage.getIdentifier())
                             .and(TRACE_ID, "fakeTraceId"),
                     result.get().getMetaData());
    }

    @Test
    public void queryWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(mockTxManager);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> Spliterators.spliterator(Arrays.asList(q.getPayload() + "1234",
                                                                                                                  q.getPayload() + "567"), Spliterator.ORDERED));
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        CompletableFuture<Collection<String>> result = testSubject.query(queryMessage).thenApply(QueryResponseMessage::getResults);
        assertEquals(asList("hello1234", "hello567"), result.get());
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    public void querySingleWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(mockTxManager);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        CompletableFuture<String> result = testSubject.query(queryMessage).thenApply(QueryResponseMessage::getFirstResult);
        assertEquals("hello1234", result.get());
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    public void testSubscribingSameHandlerTwiceInvokesOnce() throws Exception {
        AtomicInteger invocationCount = new AtomicInteger();
        MessageHandler<QueryMessage<?, String>> handler = message -> {
            invocationCount.incrementAndGet();
            return "reply";
        };
        Registration subscription = testSubject.subscribe("test", String.class, handler);
        testSubject.subscribe("test", String.class, handler);

        GenericQueryMessage<String, String> query = new GenericQueryMessage<>("request", "test", String.class);
        String actual = testSubject.query(query).thenApply(QueryResponseMessage::getFirstResult).get();
        assertEquals("reply", actual);
        assertEquals(1, invocationCount.get());

        assertTrue(subscription.cancel());

        assertTrue(testSubject.query(query).isDone());
        assertTrue(testSubject.query(query).isCompletedExceptionally());
    }

    @Test
    public void queryForSingleResultWithUnsuitableHandlers() throws Exception {
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

        CompletableFuture<String> result = testSubject.query(new GenericQueryMessage<>("query", "query", String.class))
                                                      .thenApply(QueryResponseMessage::getFirstResult);

        assertTrue(result.isDone());
        assertEquals("reply", result.get());
        assertEquals(3, invocationCount.get());
    }

    @Test
    public void queryWithOnlyUnsuitableResultsInException() throws Exception {
        testSubject.subscribe("query", String.class, message -> {
            throw new NoHandlerForQueryException("Mock");
        });
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(new GenericQueryMessage<>("query", "query", String.class));

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals("NoHandlerForQueryException", result.thenApply(QueryResponseMessage::getFirstResult)
                                                         .exceptionally(e -> e.getCause().getClass().getSimpleName()).get());
    }

    @Test
    public void queryReturnsResponseMessageFromHandlerAsIs() throws Exception {
        GenericQueryResponseMessage<String> soleResult = new GenericQueryResponseMessage<>(Collections.singleton("soleResult"));
        testSubject.subscribe("query", String.class, message -> soleResult);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(new GenericQueryMessage<>("query", "query", String.class));

        assertTrue(result.isDone());
        assertSame(result.get(), soleResult);
    }

    @Test
    public void queryWithHandlersResultsInException() throws Exception {
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(new GenericQueryMessage<>("query", "query", String.class));

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals("NoHandlerForQueryException", result.thenApply(QueryResponseMessage::getFirstResult)
                                                         .exceptionally(e -> e.getCause().getClass().getSimpleName()).get());
    }

    @Test
    public void queryForSingleResultWillReportErrors() throws Exception {
        MessageHandler<? super QueryMessage<?, ?>> failingHandler = message -> {
            throw new MockException("Mock");
        };
        testSubject.subscribe("query", String.class, failingHandler);

        CompletableFuture<String> result = testSubject.query(new GenericQueryMessage<>("query", "query", String.class))
                                                      .thenApply(QueryResponseMessage::getFirstResult);

        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
        assertEquals("Mock", result.exceptionally(e -> e.getCause().getMessage()).get());
    }

    @Test
    public void queryWithInterceptors() throws Exception {
        testSubject.registerDispatchInterceptor(messages -> (i, m) -> m.andMetaData(Collections.singletonMap("key", "value")));
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (unitOfWork.getMessage().getMetaData().containsKey("key")) {
                return "fakeReply";
            }
            return interceptorChain.proceed();
        });
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        CompletableFuture<String> result = testSubject.query(queryMessage)
                                                      .thenApply(QueryResponseMessage::getFirstResult);
        assertEquals("fakeReply", result.get());
    }

    @Test
    public void queryDoesNotArriveAtUnsubscribedHandler() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        List<String> result = testSubject.scatterGather(queryMessage, 1, TimeUnit.SECONDS)
                                         .flatMap(c -> c.getResults().stream())
                                         .collect(Collectors.toList());
        assertEquals(1, result.size());
        assertEquals("1234", result.get(0));
    }

    @Test
    public void queryReturnsException() throws Exception {
        MockException mockException = new MockException();
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw mockException;
        });
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        CompletableFuture<?> result = testSubject.query(queryMessage);
        assertTrue(result.isCompletedExceptionally());
        try {
            result.get();
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertEquals(mockException, e.getCause());
        }
    }

    @Test
    public void queryUnknown() throws Exception {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        CompletableFuture<?> result = testSubject.query(queryMessage);
        try {
            result.get();
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertEquals(NoHandlerForQueryException.class, e.getCause().getClass());
        }
    }

    @Test
    public void queryUnsubscribedHandlers() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        CompletableFuture<?> result = testSubject.query(queryMessage);
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
    public void queryAll() {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> new String[]{q.getPayload() + "567",
                q.getPayload() + "89"});
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<QueryResponseMessage<String>> allMessages = testSubject.scatterGather(queryMessage, 0, TimeUnit.SECONDS).collect(toSet());
        assertEquals(2, allMessages.size());

        Set<String> allResults = allMessages.stream().flatMap(r -> r.getResults().stream()).collect(toSet());
        assertEquals(3, allResults.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
    }

    @Test
    public void queryAllWithTransaction() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(messageMonitor, mockTxManager, errorHandler);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<Object> allResults = testSubject.scatterGather(queryMessage, 0, TimeUnit.SECONDS).collect(toSet());
        assertEquals(2, allResults.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(2)).commit();
    }

    @Test
    public void queryAllWithTransactionRollsBackOnFailure() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(messageMonitor, mockTxManager, errorHandler);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw new MockException();
        });
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<Object> allResults = testSubject.scatterGather(queryMessage, 0, TimeUnit.SECONDS).collect(toSet());
        assertEquals(1, allResults.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(1)).commit();
        verify(mockTx, times(1)).rollback();
    }

    @Test
    public void queryFirstFromScatterGatherWillCommitUnitOfWork() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(messageMonitor, mockTxManager, errorHandler);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Optional<QueryResponseMessage<String>> firstResult = testSubject.scatterGather(queryMessage, 0, TimeUnit.SECONDS).findFirst();
        assertTrue(firstResult.isPresent());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, atMost(2)).reportSuccess();
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    public void queryAllWithInterceptors() {
        testSubject.registerDispatchInterceptor(messages -> (i, m) -> m.andMetaData(Collections.singletonMap("key", "value")));
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (unitOfWork.getMessage().getMetaData().containsKey("key")) {
                return "fakeReply";
            }
            return interceptorChain.proceed();
        });
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        List<String> allResults = testSubject.scatterGather(queryMessage, 0, TimeUnit.SECONDS)
                                             .flatMap(r -> r.getResults().stream())
                                             .collect(Collectors.toList());
        assertEquals(2, allResults.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        assertEquals(asList("fakeReply", "fakeReply"), allResults);
    }

    @Test
    public void queryAllReturnsEmptyStreamWhenNoHandlersAvailable() {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<Object> allResults = testSubject.scatterGather(queryMessage, 0, TimeUnit.SECONDS).collect(toSet());
        assertEquals(0, allResults.size());
        verify(messageMonitor).onMessageIngested(any());
        verify(monitorCallback).reportIgnored();
    }

    @Test
    public void queryAllReportsExceptionsWithErrorHandler() {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw new MockException();
        });
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<Object> allResults = testSubject.scatterGather(queryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertEquals(1, allResults.size());
        verify(errorHandler).onError(isA(MockException.class), eq(queryMessage), isA(MessageHandler.class));
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
    }
}
