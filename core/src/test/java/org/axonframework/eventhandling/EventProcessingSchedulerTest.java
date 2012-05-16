/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.testutils.MockException;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.AdditionalMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;

/**
 * @author Allard Buijze
 */
public class EventProcessingSchedulerTest {

    private EventProcessingScheduler<EventMessage> testSubject;

    @Test
    public void testEventProcessingSchedule_EventBatchRetried() {
        MockEventListener listener = executeEventProcessing(RetryPolicy.RETRY_TRANSACTION);

        // each event is handled twice, since we retry the entire batch
        assertEquals(5, listener.handledEvents.size());
        assertEquals(2, listener.transactionsStarted);
        assertEquals(1, listener.transactionsSucceeded);
        assertEquals(1, listener.transactionsFailed);
    }

    @Test
    public void testEventProcessingSchedule_SingleEventRetried() {
        MockEventListener listener = executeEventProcessing(RetryPolicy.RETRY_LAST_EVENT);

        // each event is handled twice, since we retry the entire batch
        assertEquals(4, listener.handledEvents.size());
        assertEquals(2, listener.transactionsStarted);
        assertEquals(1, listener.transactionsSucceeded);
        assertEquals(1, listener.transactionsFailed);
    }

    @Test
    public void testEventProcessingSchedule_FailedEventIgnored() {
        MockEventListener listener = executeEventProcessing(RetryPolicy.SKIP_FAILED_EVENT);

        // each event is handled twice, since we retry the entire batch
        assertEquals(3, listener.handledEvents.size());
        assertEquals(2, listener.transactionsStarted);
        assertEquals(1, listener.transactionsSucceeded);
        assertEquals(1, listener.transactionsFailed);
    }

    @Test
    public void testEventProcessingDelayed_ScheduledExecutorService() {
        EventMessage<? extends StubDomainEvent> event1 = new GenericEventMessage<StubDomainEvent>(new StubDomainEvent());
        EventMessage<? extends StubDomainEvent> event2 = new GenericEventMessage<StubDomainEvent>(new StubDomainEvent());
        final TransactionalEventListener listener = mock(TransactionalEventListener.class);
        ScheduledExecutorService mockExecutorService = mock(ScheduledExecutorService.class);
        testSubject = new EventProcessingScheduler<EventMessage>(listener, mockExecutorService,
                                                                 new NullShutdownCallback()) {
            @Override
            protected void doHandle(EventMessage event) {
                listener.handle(event);
            }
        };

        doThrow(new MockException()).doNothing().when(listener).handle(event1);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                TransactionStatus status = (TransactionStatus) invocation.getArguments()[0];
                status.setRetryInterval(500);
                status.setRetryPolicy(RetryPolicy.RETRY_TRANSACTION);
                return null;
            }
        }).when(listener).afterTransaction(isA(TransactionStatus.class));
        testSubject.scheduleEvent(event1);
        testSubject.scheduleEvent(event2);
        testSubject.run();
        verify(mockExecutorService).schedule(eq(testSubject), gt(400L), eq(TimeUnit.MILLISECONDS));
        // since the scheduler is a mock, we simulate the execution:
        testSubject.run();
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).beforeTransaction(isA(TransactionStatus.class));
        inOrder.verify(listener).handle(event1);
        inOrder.verify(listener).afterTransaction(isA(TransactionStatus.class));
        inOrder.verify(listener).beforeTransaction(isA(TransactionStatus.class));
        inOrder.verify(listener).handle(event1);
        inOrder.verify(listener).handle(event2);
        inOrder.verify(listener).afterTransaction(isA(TransactionStatus.class));
    }

    @Test
    public void testEventProcessingDelayed_ExecutorDoesNotSupportScheduling() {
        EventMessage<? extends StubDomainEvent> event1 = new GenericEventMessage<StubDomainEvent>(new StubDomainEvent());
        EventMessage<? extends StubDomainEvent> event2 = new GenericEventMessage<StubDomainEvent>(new StubDomainEvent());
        final TransactionalEventListener listener = mock(TransactionalEventListener.class);
        ExecutorService mockExecutorService = mock(ExecutorService.class);
        testSubject = new EventProcessingScheduler<EventMessage>(listener, mockExecutorService,
                                                                 new NullShutdownCallback()) {
            @Override
            protected void doHandle(EventMessage event) {
                listener.handle(event);
            }
        };

        doThrow(new MockException()).doNothing().when(listener).handle(event1);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                TransactionStatus status = (TransactionStatus) invocation.getArguments()[0];
                status.setRetryInterval(500);
                status.setRetryPolicy(RetryPolicy.RETRY_TRANSACTION);
                return null;
            }
        }).when(listener).afterTransaction(isA(TransactionStatus.class));
        testSubject.scheduleEvent(event1);
        testSubject.scheduleEvent(event2);
        long t1 = System.currentTimeMillis();
        testSubject.run();
        // we simulate the immediate scheduling of the yielded task by executing run again
        testSubject.run();
        long t2 = System.currentTimeMillis();
        // we allow some slack, because thread scheduling doesn't give us much guarantees about timing
        long waitTime = t2 - t1;
        assertTrue("Wait time was too short: " + waitTime, waitTime > 480);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).beforeTransaction(isA(TransactionStatus.class));
        inOrder.verify(listener).handle(event1);
        inOrder.verify(listener).afterTransaction(isA(TransactionStatus.class));
        inOrder.verify(listener).beforeTransaction(isA(TransactionStatus.class));
        inOrder.verify(listener).handle(event1);
        inOrder.verify(listener).handle(event2);
        inOrder.verify(listener).afterTransaction(isA(TransactionStatus.class));
    }

    /**
     * This test verifies issue #15 (http://code.google.com/p/axonframework/issues/detail?id=15)
     */
    @Test
    public void testEventProcessingRetried_BeforeTransactionFails() {
        EventMessage<? extends StubDomainEvent> event1 = new GenericEventMessage<StubDomainEvent>(new StubDomainEvent());
        EventMessage<? extends StubDomainEvent> event2 = new GenericEventMessage<StubDomainEvent>(new StubDomainEvent());
        final TransactionalEventListener listener = mock(TransactionalEventListener.class);
        ScheduledExecutorService mockExecutorService = mock(ScheduledExecutorService.class);
        testSubject = new EventProcessingScheduler<EventMessage>(listener, mockExecutorService,
                                                                 new NullShutdownCallback()) {
            @Override
            protected void doHandle(EventMessage event) {
                listener.handle(event);
            }
        };

        doThrow(new MockException()).doNothing().when(listener)
                .beforeTransaction(isA(TransactionStatus.class));
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                TransactionStatus status = (TransactionStatus) invocation.getArguments()[0];
                status.setRetryInterval(500);
                status.setRetryPolicy(RetryPolicy.RETRY_TRANSACTION);
                return null;
            }
        }).when(listener).afterTransaction(isA(TransactionStatus.class));
        testSubject.scheduleEvent(event1);
        testSubject.scheduleEvent(event2);
        testSubject.run();
        verify(mockExecutorService).schedule(eq(testSubject), gt(400L), eq(TimeUnit.MILLISECONDS));
        // since the scheduler is a mock, we simulate the execution:
        testSubject.run();
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).beforeTransaction(isA(TransactionStatus.class));
        // the afterTransaction call must be done, event if the before failed.
        inOrder.verify(listener).afterTransaction(isA(TransactionStatus.class));
        inOrder.verify(listener).beforeTransaction(isA(TransactionStatus.class));
        // make sure the first event is not skipped by verifying that event1 is handled
        inOrder.verify(listener).handle(event1);
        inOrder.verify(listener).handle(event2);
        inOrder.verify(listener).afterTransaction(isA(TransactionStatus.class));
    }

    private MockEventListener executeEventProcessing(RetryPolicy policy) {
        ExecutorService mockExecutorService = mock(ExecutorService.class);
        final MockEventListener listener = new MockEventListener(policy);
        testSubject = new EventProcessingScheduler<EventMessage>(listener, mockExecutorService,
                                                                 new NullShutdownCallback()) {
            @Override
            protected void doHandle(EventMessage event) {
                listener.handle(event);
            }
        };

        doNothing().doThrow(new RejectedExecutionException()).when(mockExecutorService).execute(isA(Runnable.class));
        testSubject.scheduleEvent(new GenericEventMessage<StubDomainEvent>(new StubDomainEvent()));
        listener.failOnEvent = 2;
        testSubject.scheduleEvent(new GenericEventMessage<StubDomainEvent>(new StubDomainEvent()));
        testSubject.scheduleEvent(new GenericEventMessage<StubDomainEvent>(new StubDomainEvent()));

        testSubject.run();
        return listener;
    }

    private class MockEventListener implements EventListener, TransactionManager {

        private int failOnEvent;
        private List<EventMessage<?>> handledEvents = new LinkedList<EventMessage<?>>();
        private RetryPolicy retryPolicy;
        private int transactionsStarted;
        private int transactionsSucceeded;
        private int transactionsFailed;

        public MockEventListener(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
        }

        @Override
        public void handle(EventMessage event) {
            handledEvents.add(event);
            if (--failOnEvent == 0) {
                throw new MockException();
            }
        }

        @Override
        public void beforeTransaction(TransactionStatus transactionStatus) {
            transactionStatus.setRetryPolicy(retryPolicy);
            transactionsStarted++;
        }

        @Override
        public void afterTransaction(TransactionStatus transactionStatus) {
            transactionStatus.setRetryInterval(100);
            assertEquals(failOnEvent == 0, !transactionStatus.isSuccessful());
            if (transactionStatus.isSuccessful()) {
                transactionsSucceeded++;
            } else {
                transactionsFailed++;
            }
        }
    }

    private static class NullShutdownCallback implements EventProcessingScheduler.ShutdownCallback {

        @Override
        public void afterShutdown(EventProcessingScheduler scheduler) {
        }
    }

    private interface TransactionalEventListener extends TransactionManager, EventListener {

    }
}
