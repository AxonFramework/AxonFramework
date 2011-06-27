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

package org.axonframework.saga.annotation;

import org.axonframework.domain.Event;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.TransactionManager;
import org.axonframework.eventhandling.TransactionStatus;
import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.SagaFactory;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaManagerTest_Asynchronous {

    private AnnotatedSagaManager manager;
    private InMemorySagaRepository sageRepository;
    private SimpleEventBus eventBus;
    private ScheduledExecutorService executor;
    private SagaFactory sagaFactory;
    private TransactionManager transactionManager;
    private static CountDownLatch endingLatch;

    @Before
    public void setUp() throws Exception {
        sageRepository = new InMemorySagaRepository();
        eventBus = new SimpleEventBus();
        executor = new ScheduledThreadPoolExecutor(4);
        sagaFactory = new GenericSagaFactory();
        transactionManager = mock(TransactionManager.class);
        manager = new AnnotatedSagaManager(sageRepository, sagaFactory, eventBus, executor, transactionManager,
                                           MyTestSaga.class);
        manager.subscribe();
        endingLatch = new CountDownLatch(2);
    }

    @Test
    public void testDispatchEvent_NormalEventLifecycle() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger(0);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                TransactionStatus status = (TransactionStatus) invocation.getArguments()[0];
                counter.getAndAdd(status.getEventsProcessedInTransaction());
                return Void.TYPE;
            }
        }).when(transactionManager).afterTransaction(isA(TransactionStatus.class));
        eventBus.publish(new StartingEvent("saga1"));
        eventBus.publish(new ForcingStartEvent("saga1"));
        eventBus.publish(new EndingEvent("saga1"));

        endingLatch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        // expect 8 invocations: 3x for lookup, 1x for StartingEvent, 2x for ForceStartEvent, 2x for EndingEvent
        assertEquals(3, counter.get());
        verify(transactionManager, times(3)).afterTransaction(isA(TransactionStatus.class));
        verify(transactionManager, times(3)).beforeTransaction(isA(TransactionStatus.class));
    }

    public static class MyTestSaga extends AbstractAnnotatedSaga {

        private List<Event> capturedEvents = new LinkedList<Event>();
        private static final long serialVersionUID = -1562911263884220240L;

        @StartSaga
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(StartingEvent event) {
            capturedEvents.add(event);
        }

        @StartSaga(forceNew = true)
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(ForcingStartEvent event) {
            capturedEvents.add(event);
        }

        @EndSaga
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(EndingEvent event) {
            capturedEvents.add(event);
            endingLatch.countDown();
        }

        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleMiddleEvent(MiddleEvent event) {
            capturedEvents.add(event);
        }

        public List<Event> getCapturedEvents() {
            return capturedEvents;
        }
    }

    public static abstract class MyIdentifierEvent extends StubDomainEvent {

        private String myIdentifier;

        protected MyIdentifierEvent(String myIdentifier) {
            this.myIdentifier = myIdentifier;
        }

        public String getMyIdentifier() {
            return myIdentifier;
        }
    }

    public static class StartingEvent extends MyIdentifierEvent {

        protected StartingEvent(String myIdentifier) {
            super(myIdentifier);
        }
    }

    public static class ForcingStartEvent extends MyIdentifierEvent {

        protected ForcingStartEvent(String myIdentifier) {
            super(myIdentifier);
        }
    }

    public static class EndingEvent extends MyIdentifierEvent {

        protected EndingEvent(String myIdentifier) {
            super(myIdentifier);
        }
    }

    public static class MiddleEvent extends MyIdentifierEvent {

        protected MiddleEvent(String myIdentifier) {
            super(myIdentifier);
        }
    }
}
