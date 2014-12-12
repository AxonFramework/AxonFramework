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

package org.axonframework.eventhandling.replay;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.ClusteringEventBus;
import org.axonframework.eventhandling.DefaultClusterSelector;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.jpa.criteria.JpaCriteriaBuilder;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.testutils.MockException;
import org.axonframework.unitofwork.TransactionManager;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class ReplayingClusterTest {

    private ReplayingCluster testSubject;
    private IncomingMessageHandler mockMessageHandler;
    private TransactionManager mockTransactionManager;
    private EventStoreManagement mockEventStore;
    private Cluster delegateCluster;
    private List<DomainEventMessage> messages;

    @Before
    public void setUp() throws Exception {
        mockMessageHandler = mock(IncomingMessageHandler.class);
        mockTransactionManager = mock(TransactionManager.class);
        mockEventStore = mock(EventStoreManagement.class);
        delegateCluster = spy(new SimpleCluster("simpleCluster"));

        testSubject = new ReplayingCluster(delegateCluster, mockEventStore, mockTransactionManager, -1,
                                           mockMessageHandler);

        messages = new ArrayList<DomainEventMessage>();
        for (int i = 0; i < 10; i++) {
            messages.add(new GenericDomainEventMessage<String>("id", i, "Message Payload"));
        }
    }

    @Test
    public void testSubscribeAndUnsubscribeListeners() {
        final EventListener listener = mock(EventListener.class);
        final EventListener replayAware = mock(ReplayAwareListener.class);
        testSubject.subscribe(listener);
        testSubject.subscribe(replayAware);
        verify(delegateCluster).subscribe(listener);
        verify(delegateCluster).subscribe(replayAware);

        testSubject.unsubscribe(listener);
        testSubject.unsubscribe(replayAware);
        verify(delegateCluster).unsubscribe(listener);
        verify(delegateCluster).unsubscribe(replayAware);
    }

    @Test
    public void testAnnotatedHandlersRecognized() {
        EventBus eventBus = new ClusteringEventBus(new DefaultClusterSelector(testSubject));
        MyReplayAwareListener annotatedBean = new MyReplayAwareListener();
        AnnotationEventListenerAdapter.subscribe(annotatedBean, eventBus);

        testSubject.startReplay();

        assertEquals(0, annotatedBean.counter);
        assertEquals(1, annotatedBean.before);
        assertEquals(1, annotatedBean.after);
    }

    @Test
    public void testRegularMethodsDelegated() {
        testSubject.getMembers();
        verify(delegateCluster).getMembers();

        testSubject.getName();
        verify(delegateCluster).getName();

        testSubject.getMetaData();
        verify(delegateCluster).getMetaData();
    }

    @Test
    public void testReplay() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                EventVisitor visitor = (EventVisitor) invocation.getArguments()[0];
                for (DomainEventMessage message : messages) {
                    visitor.doWithEvent(message);
                }
                return null;
            }
        }).when(mockEventStore).visitEvents(isA(EventVisitor.class));

        testSubject.startReplay();

        InOrder inOrder = inOrder(mockEventStore, mockTransactionManager, delegateCluster, mockMessageHandler);

        inOrder.verify(mockMessageHandler).prepareForReplay(isA(Cluster.class));
        inOrder.verify(mockTransactionManager).startTransaction();
        inOrder.verify(mockEventStore).visitEvents(isA(EventVisitor.class));
        for (int i = 0; i < 10; i++) {
            inOrder.verify(delegateCluster).publish(isA(DomainEventMessage.class));
            inOrder.verify(mockMessageHandler).releaseMessage(eq(delegateCluster), isA(DomainEventMessage.class));
        }
        inOrder.verify(mockMessageHandler).processBacklog(delegateCluster);
        inOrder.verify(mockTransactionManager).commitTransaction(anyObject());
    }

    @Test
    public void testReplay_HandlersSubscribedTwice() {
        final ReplayAwareListener replayAwareListener = mock(ReplayAwareListener.class);
        testSubject.subscribe(replayAwareListener);
        testSubject.subscribe(replayAwareListener);

        testSubject.startReplay();

        verify(replayAwareListener, times(1)).beforeReplay();
        verify(replayAwareListener, times(1)).afterReplay();
    }

    @Test
    public void testPartialReplay_withCriteria() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                EventVisitor visitor = (EventVisitor) invocation.getArguments()[1];
                for (DomainEventMessage message : messages) {
                    visitor.doWithEvent(message);
                }
                return null;
            }
        }).when(mockEventStore).visitEvents(isA(Criteria.class), isA(EventVisitor.class));

        when(mockEventStore.newCriteriaBuilder()).thenReturn(new JpaCriteriaBuilder());
        Criteria criteria = testSubject.newCriteriaBuilder().property("abc").isNot(false);
        testSubject.startReplay(criteria);

        InOrder inOrder = inOrder(mockEventStore, mockTransactionManager, delegateCluster, mockMessageHandler);

        inOrder.verify(mockMessageHandler).prepareForReplay(isA(Cluster.class));
        inOrder.verify(mockTransactionManager).startTransaction();
        inOrder.verify(mockEventStore).visitEvents(refEq(criteria), isA(EventVisitor.class));
        for (int i = 0; i < 10; i++) {
            inOrder.verify(delegateCluster).publish(isA(DomainEventMessage.class));
            inOrder.verify(mockMessageHandler).releaseMessage(eq(delegateCluster), isA(DomainEventMessage.class));
        }
        inOrder.verify(mockMessageHandler).processBacklog(delegateCluster);
        inOrder.verify(mockTransactionManager).commitTransaction(anyObject());
    }

    @Test
    public void testTransactionRolledBackOnException() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                EventVisitor visitor = (EventVisitor) invocation.getArguments()[0];
                for (DomainEventMessage message : messages) {
                    visitor.doWithEvent(message);
                }
                return null;
            }
        }).when(mockEventStore).visitEvents(isA(EventVisitor.class));

        final MockException toBeThrown = new MockException();
        doThrow(toBeThrown).when(delegateCluster).publish(messages.get(5));

        try {
            testSubject.startReplay();
            fail("Expected exception");
        } catch (ReplayFailedException e) {
            assertSame("Got an exception, but the wrong one", toBeThrown, e.getCause());
        }

        InOrder inOrder = inOrder(mockEventStore, mockTransactionManager, delegateCluster, mockMessageHandler);

        inOrder.verify(mockMessageHandler).prepareForReplay(isA(Cluster.class));
        inOrder.verify(mockTransactionManager).startTransaction();
        inOrder.verify(mockEventStore).visitEvents(isA(EventVisitor.class));
        for (int i = 0; i < 5; i++) {
            inOrder.verify(delegateCluster).publish(isA(DomainEventMessage.class));
            inOrder.verify(mockMessageHandler).releaseMessage(eq(delegateCluster), isA(DomainEventMessage.class));
        }
        inOrder.verify(mockMessageHandler).onReplayFailed(delegateCluster, toBeThrown);
        inOrder.verify(mockTransactionManager).rollbackTransaction(anyObject());

        verify(mockMessageHandler, never()).processBacklog(delegateCluster);
        assertFalse(testSubject.isInReplayMode());
    }

    @Test
    public void testEventReceivedDuringReplay() {
        final GenericEventMessage<String> concurrentMessage = new GenericEventMessage<String>("Concurrent MSG");
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                EventVisitor visitor = (EventVisitor) invocation.getArguments()[0];
                assertTrue(testSubject.isInReplayMode());
                testSubject.publish(concurrentMessage);
                for (DomainEventMessage message : messages) {
                    visitor.doWithEvent(message);
                }
                return null;
            }
        }).when(mockEventStore).visitEvents(isA(EventVisitor.class));

        final ReplayAwareListener listener = mock(ReplayAwareListener.class);
        testSubject.subscribe(listener);
        testSubject.startReplay();

        InOrder inOrder = inOrder(mockEventStore, mockTransactionManager, delegateCluster, mockMessageHandler,
                                  listener);

        inOrder.verify(mockMessageHandler).prepareForReplay(isA(Cluster.class));
        inOrder.verify(mockTransactionManager).startTransaction();
        inOrder.verify(listener).beforeReplay();
        inOrder.verify(mockEventStore).visitEvents(isA(EventVisitor.class));
        inOrder.verify(mockMessageHandler).onIncomingMessages(delegateCluster, concurrentMessage);
        for (int i = 0; i < 10; i++) {
            inOrder.verify(delegateCluster).publish(isA(DomainEventMessage.class));
            inOrder.verify(mockMessageHandler).releaseMessage(eq(delegateCluster), isA(DomainEventMessage.class));
        }
        inOrder.verify(listener).afterReplay();
        inOrder.verify(mockMessageHandler).processBacklog(delegateCluster);
        inOrder.verify(mockTransactionManager).commitTransaction(anyObject());

        verify(delegateCluster, never()).publish(concurrentMessage);
        verify(delegateCluster).subscribe(listener);
    }

    @Test(timeout = 4000)
    public void testAfterReplayInvokedAfterHandlingOfLastEvent() throws Exception {
        final ReplayAwareListener listener = mock(ReplayAwareListener.class);
        final DomainEventMessage event1 = new GenericDomainEventMessage<String>("id1", 0, "event1");
        final DomainEventMessage event2 = new GenericDomainEventMessage<String>("id1", 0, "event2");
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        doAnswer(new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // slow down second event, to make sure replay thread waits for it...
                            Thread.sleep(200);
                            invocation.callRealMethod();
                        } catch (Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    }
                });
                return null;
            }
        }).when(delegateCluster).publish(event2);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((EventVisitor) invocation.getArguments()[0]).doWithEvent(event1);
                ((EventVisitor) invocation.getArguments()[0]).doWithEvent(event2);
                return null;
            }
        }).when(mockEventStore).visitEvents(isA(EventVisitor.class));

        testSubject.subscribe(listener);
        testSubject.startReplay();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).beforeReplay();
        inOrder.verify(listener).handle(event1);
        inOrder.verify(listener).handle(event2);
        inOrder.verify(listener).afterReplay();
    }

    @Test
    public void testIllegalTimeoutValuesAreIgnored() throws Exception {
        testSubject.getMetaData().setProperty(ReplayingCluster.AFTER_REPLAY_TIMEOUT, "not a val");
        testAfterReplayInvokedAfterHandlingOfLastEvent();
    }

    @Test(timeout = 1000)
    public void testAfterReplayInvokedWhenTimeoutExpires() throws Exception {
        testSubject.getMetaData().setProperty(ReplayingCluster.AFTER_REPLAY_TIMEOUT, "0");
        final ReplayAwareListener listener = mock(ReplayAwareListener.class);
        final DomainEventMessage event1 = new GenericDomainEventMessage<String>("id1", 0, "event1");
        final DomainEventMessage event2 = new GenericDomainEventMessage<String>("id1", 0, "event2");
        doNothing().when(delegateCluster).publish(event2);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((EventVisitor) invocation.getArguments()[0]).doWithEvent(event1);
                ((EventVisitor) invocation.getArguments()[0]).doWithEvent(event2);
                return null;
            }
        }).when(mockEventStore).visitEvents(isA(EventVisitor.class));

        testSubject.subscribe(listener);
        testSubject.startReplay();

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).beforeReplay();
        inOrder.verify(listener).handle(event1);
        inOrder.verify(listener, never()).handle(event2);
        inOrder.verify(listener).afterReplay();
    }

    @Test
    public void testIntermediateTransactionsCommitted() {
        testSubject = new ReplayingCluster(delegateCluster, mockEventStore, mockTransactionManager, 5,
                                           mockMessageHandler);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                EventVisitor visitor = (EventVisitor) invocation.getArguments()[0];
                for (DomainEventMessage message : messages) {
                    visitor.doWithEvent(message);
                }
                return null;
            }
        }).when(mockEventStore).visitEvents(isA(EventVisitor.class));

        testSubject.startReplay();

        InOrder inOrder = inOrder(mockEventStore, mockTransactionManager, delegateCluster, mockMessageHandler);

        inOrder.verify(mockMessageHandler).prepareForReplay(isA(Cluster.class));
        inOrder.verify(mockTransactionManager).startTransaction();
        inOrder.verify(mockEventStore).visitEvents(isA(EventVisitor.class));
        for (int i = 0; i < 5; i++) {
            inOrder.verify(delegateCluster).publish(isA(DomainEventMessage.class));
            inOrder.verify(mockMessageHandler).releaseMessage(eq(delegateCluster), isA(DomainEventMessage.class));
        }
        inOrder.verify(mockTransactionManager).commitTransaction(anyObject());
        inOrder.verify(mockTransactionManager).startTransaction();

        for (int i = 5; i < 10; i++) {
            inOrder.verify(delegateCluster).publish(isA(DomainEventMessage.class));
            inOrder.verify(mockMessageHandler).releaseMessage(eq(delegateCluster), isA(DomainEventMessage.class));
        }
        inOrder.verify(mockMessageHandler).processBacklog(delegateCluster);
        inOrder.verify(mockTransactionManager).commitTransaction(anyObject());
        inOrder.verify(mockTransactionManager, never()).startTransaction();
    }

    interface ReplayAwareListener extends ReplayAware, EventListener {

    }

    private static class MyReplayAwareListener implements ReplayAware {

        public int counter;
        public int before;
        public int after;
        public int failed;

        @EventHandler
        public void handleAll(Object payload) {
            counter++;
        }

        @Override
        public void beforeReplay() {
            before++;
        }

        @Override
        public void afterReplay() {
            after++;
        }

        @Override
        public void onReplayFailed(Throwable cause) {
            failed++;
        }
    }
}