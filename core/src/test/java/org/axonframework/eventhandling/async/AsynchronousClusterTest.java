/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling.async;

import org.axonframework.common.DirectExecutor;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventListenerProxy;
import org.axonframework.eventhandling.EventProcessingMonitor;
import org.axonframework.eventhandling.EventProcessingMonitorSupport;
import org.axonframework.eventhandling.OrderResolver;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.testutils.MockException;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.unitofwork.TransactionManager;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AsynchronousClusterTest {

    private TransactionManager mockTransactionManager;
    private Executor executor;
    private AsynchronousCluster testSubject;

    @Before
    public void setUp() throws Exception {
        mockTransactionManager = mock(TransactionManager.class);
        executor = mock(Executor.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // since we need to pretend we run in another thread, we clear the Unit of Work first
                UnitOfWork currentUnitOfWork = null;
                if (CurrentUnitOfWork.isStarted()) {
                    currentUnitOfWork = CurrentUnitOfWork.get();
                    CurrentUnitOfWork.clear(currentUnitOfWork);
                }

                ((Runnable) invocation.getArguments()[0]).run();

                if (currentUnitOfWork != null) {
                    CurrentUnitOfWork.set(currentUnitOfWork);
                }
                return null;
            }
        }).when(executor).execute(isA(Runnable.class));
        testSubject = new AsynchronousCluster("async", executor, mockTransactionManager,
                                              new SequentialPerAggregatePolicy(),
                                              new DefaultErrorHandler(RetryPolicy.proceed()));
    }

    @Test
    public void testSimpleConfig_ProceedOnFailure() {
        testSubject = new AsynchronousCluster("async", executor,
                                              new SequentialPerAggregatePolicy());
        final List<EventMessage> ackedMessages = listenForAcknowledgedMessages();
        final List<EventMessage> failedMessages = listenForFailedMessages();

        EventListener mockEventListener = mock(EventListener.class);
        testSubject.subscribe(mockEventListener);

        final EventMessage<Object> message1 = asEventMessage(new Object());
        final EventMessage<Object> message2 = asEventMessage(new Object());

        doThrow(new MockException()).when(mockEventListener).handle(message1);

        testSubject.publish(message1);
        testSubject.publish(message2);

        verify(mockEventListener, times(2)).handle(isA(EventMessage.class));
        assertEquals(Arrays.<EventMessage>asList(message1), failedMessages);
        assertEquals(Arrays.<EventMessage>asList(message2), ackedMessages);
    }

    @Test
    public void testOrderingOfListeners() {
        testSubject = new AsynchronousCluster("async", new DirectExecutor(),
                                              new DefaultUnitOfWorkFactory(mockTransactionManager),
                                              new SequentialPolicy(), new DefaultErrorHandler(RetryPolicy.proceed()),
                                              new OrderResolver() {
                                                  @Override
                                                  public int orderOf(EventListener listener) {
                                                      if (listener instanceof EventListenerProxy) {
                                                          return ((EventListenerProxy) listener).getTargetType()
                                                                                                .getSuperclass()
                                                                                                .getAnnotation(Order.class)
                                                                                                .value();
                                                      }
                                                      return 0;
                                                  }
                                              });
        final List<EventMessage> ackedMessages = listenForAcknowledgedMessages();

        final FirstHandler handler1 = spy(new FirstHandler());
        final SecondHandler handler2 = spy(new SecondHandler());
        testSubject.subscribe(new AnnotationEventListenerAdapter(handler1));
        testSubject.subscribe(new AnnotationEventListenerAdapter(handler2));

        final EventMessage<Object> eventMessage = GenericEventMessage.asEventMessage("test");
        testSubject.publish(eventMessage);

        InOrder inOrder = Mockito.inOrder(handler1, handler2);
        inOrder.verify(handler1).onEvent("test");
        inOrder.verify(handler2).onEvent("test");

        assertEquals(1, ackedMessages.size());
        assertEquals(eventMessage, ackedMessages.get(0));
    }

    @Test
    public void testSubscriptions() throws Exception {
        EventListener mockEventListener = mock(EventListener.class);
        testSubject.subscribe(mockEventListener);
        assertTrue(testSubject.getMembers().contains(mockEventListener));

        testSubject.unsubscribe(mockEventListener);
        assertFalse(testSubject.getMembers().contains(mockEventListener));
    }

    @Test
    public void testEventsScheduledForHandling() {
        final GenericEventMessage<String> message1 = new GenericEventMessage<String>("Message 1");
        final GenericEventMessage<String> message2 = new GenericEventMessage<String>("Message 2");

        testSubject.publish(message1, message2);

        verify(executor, times(2)).execute(isA(Runnable.class));
        verify(mockTransactionManager, times(2)).startTransaction();
        verify(mockTransactionManager, times(2)).commitTransaction(any());
    }

    @Test
    public void testEventsScheduledForHandlingWhenSurroundingUnitOfWorkCommits() {
        final GenericEventMessage<String> message1 = new GenericEventMessage<String>("Message 1");
        final GenericEventMessage<String> message2 = new GenericEventMessage<String>("Message 2");

        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        uow.registerListener(new UnitOfWorkListenerAdapter() {
            @Override
            public void onPrepareTransactionCommit(UnitOfWork unitOfWork, Object transaction) {
                verify(executor, never()).execute(isA(Runnable.class));
                verify(mockTransactionManager, never()).startTransaction();
                verify(mockTransactionManager, never()).commitTransaction(any());
            }
        });

        testSubject.publish(message1, message2);

        verify(executor, never()).execute(isA(Runnable.class));
        verify(mockTransactionManager, never()).startTransaction();
        verify(mockTransactionManager, never()).commitTransaction(any());

        uow.commit();

        verify(executor, times(2)).execute(isA(Runnable.class));
        verify(mockTransactionManager, times(2)).startTransaction();
        verify(mockTransactionManager, times(2)).commitTransaction(any());
    }

    @Test
    public void testExceptionsIgnoredWhenErrorPolicyIsProceed_IncludesAsyncHandler() {
        final List<EventMessage> ackedMessages = listenForAcknowledgedMessages();
        final List<EventMessage> failedMessages = listenForFailedMessages();

        EventListener mockEventListener1 = mock(EventListener.class);
        EventListener mockEventListener2 = mock(EventListener.class);
        AsyncHandler mockEventListener3 = mock(AsyncHandler.class);

        final ArgumentCaptor<EventProcessingMonitor> argumentCaptor = ArgumentCaptor
                .forClass(EventProcessingMonitor.class);
        doNothing().when(mockEventListener3).subscribeEventProcessingMonitor(argumentCaptor.capture());

        testSubject.subscribe(mockEventListener1);
        testSubject.subscribe(mockEventListener2);
        testSubject.subscribe(mockEventListener3);

        doThrow(new MockException()).when(mockEventListener1).handle(isA(EventMessage.class));
        doThrow(new MockException()).when(mockEventListener2).handle(isA(EventMessage.class));
        doNothing().when(mockEventListener3).handle(isA(EventMessage.class));

        final GenericEventMessage message = new GenericEventMessage("test");
        testSubject.publish(message);

        verify(mockEventListener1).handle(message);
        verify(mockEventListener2).handle(message);
        verify(mockEventListener3).handle(message);

        assertEquals(0, ackedMessages.size());
        assertEquals(0, failedMessages.size());

        // now, the ack of the last one comes in
        argumentCaptor.getValue().onEventProcessingCompleted(Arrays.asList(message));

        assertEquals(0, ackedMessages.size());
        assertEquals(1, failedMessages.size());
        assertEquals(message, failedMessages.get(0));
    }

    private static interface AsyncHandler extends EventListener, EventProcessingMonitorSupport {

    }

    @Order(1)
    private static class FirstHandler {

        @EventHandler
        public void onEvent(String event) {

        }
    }

    @Order(2)
    private static class SecondHandler {

        @EventHandler
        public void onEvent(String event) {

        }
    }

    private List<EventMessage> listenForAcknowledgedMessages() {
        final EventProcessingMonitor monitor = mock(EventProcessingMonitor.class);
        testSubject.subscribeEventProcessingMonitor(monitor);
        final List<EventMessage> ackedMessages = new ArrayList<EventMessage>();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                ackedMessages.addAll((List<EventMessage>) invocationOnMock.getArguments()[0]);
                return null;
            }
        }).when(monitor).onEventProcessingCompleted(isA(List.class));
        return ackedMessages;
    }

    private List<EventMessage> listenForFailedMessages() {
        final EventProcessingMonitor monitor = mock(EventProcessingMonitor.class);
        testSubject.subscribeEventProcessingMonitor(monitor);
        final List<EventMessage> failedMessages = new ArrayList<EventMessage>();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                failedMessages.addAll((List<EventMessage>) invocationOnMock.getArguments()[0]);
                return null;
            }
        }).when(monitor).onEventProcessingFailed(isA(List.class), isA(Throwable.class));
        return failedMessages;
    }
}
