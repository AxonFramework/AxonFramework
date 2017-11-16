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
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * Author: marc
 */
public class SimpleQueryBusTest {
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
    }

    @Test
    public void subscribe() throws Exception {
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
    public void query() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        CompletableFuture<String> result = testSubject.query(queryMessage);
        assertEquals("hello1234", result.get());
    }

    @Test
    public void queryWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(mockTxManager);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        CompletableFuture<String> result = testSubject.query(queryMessage);
        assertEquals("hello1234", result.get());
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
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
        CompletableFuture<String> result = testSubject.query(queryMessage);
        assertEquals("fakeReply", result.get());
    }

    @Test
    public void queryDoesNotArriveAtUnsubscribedHandler() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("hello", String.class);
        List<String> result = testSubject.queryAll(queryMessage, 1, TimeUnit.SECONDS).collect(Collectors.toList());
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
        CompletableFuture<String> result = testSubject.query(queryMessage);
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
        CompletableFuture<String> result = testSubject.query(queryMessage);
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
        CompletableFuture<String> result = testSubject.query(queryMessage);
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
    public void queryAll() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<Object> allResults = testSubject.queryAll(queryMessage, 0, TimeUnit.SECONDS).collect(Collectors.toSet());
        assertEquals(2, allResults.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
    }

    @Test
    public void queryAllWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(messageMonitor, mockTxManager, errorHandler);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<Object> allResults = testSubject.queryAll(queryMessage, 0, TimeUnit.SECONDS).collect(Collectors.toSet());
        assertEquals(2, allResults.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(2)).commit();
    }

    @Test
    public void queryAllWithTransactionRollsBackOnFailure() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(messageMonitor, mockTxManager, errorHandler);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {throw new MockException();});
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<Object> allResults = testSubject.queryAll(queryMessage, 0, TimeUnit.SECONDS).collect(Collectors.toSet());
        assertEquals(1, allResults.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(1)).commit();
        verify(mockTx, times(1)).rollback();
    }

    @Test
    public void queryFirstFromScatterGatherWillCommitUnitOfWork() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = new SimpleQueryBus(messageMonitor, mockTxManager, errorHandler);

        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Optional<String> firstResult = testSubject.queryAll(queryMessage, 0, TimeUnit.SECONDS).findFirst();
        assertTrue(firstResult.isPresent());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, atMost(2)).reportSuccess();
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    public void queryAllWithInterceptors() throws Exception {
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

        List<Object> allResults = testSubject.queryAll(queryMessage, 0, TimeUnit.SECONDS).collect(Collectors.toList());
        assertEquals(2, allResults.size());
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        assertEquals(asList("fakeReply", "fakeReply"), allResults);
    }



    @Test
    public void queryAllReturnsEmptyStreamWhenNoHandlersAvailable() throws Exception {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<Object> allResults = testSubject.queryAll(queryMessage, 0, TimeUnit.SECONDS).collect(Collectors.toSet());
        assertEquals(0, allResults.size());
        verify(messageMonitor).onMessageIngested(any());
        verify(monitorCallback).reportIgnored();
    }

    @Test
    public void queryAllReportsExceptionsWithErrorHandler() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw new MockException();
        });
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        Set<Object> allResults = testSubject.queryAll(queryMessage, 0, TimeUnit.SECONDS).collect(Collectors.toSet());

        assertEquals(1, allResults.size());
        verify(errorHandler).onError(isA(MockException.class), eq(queryMessage), isA(MessageHandler.class));
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
    }
}
