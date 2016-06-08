/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.saga;

import org.axonframework.common.annotation.MetaData;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventsourcing.StubDomainEvent;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static java.util.Collections.singletonMap;
import static junit.framework.TestCase.fail;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaManagerTest {

    private AnnotatedSagaRepository<MyTestSaga> sagaRepository;
    private AnnotatedSagaManager<MyTestSaga> manager;
    private InMemorySagaStore sagaStore;

    @Before
    public void setUp() throws Exception {
        sagaStore = new InMemorySagaStore();
        sagaRepository = spy(new AnnotatedSagaRepository<>(MyTestSaga.class, sagaStore));
        manager = new AnnotatedSagaManager<>(MyTestSaga.class, sagaRepository, MyTestSaga::new, t -> true);
    }

    @Test
    public void testCreationPolicy_NoneExists() throws Exception {
        manager.accept(new GenericEventMessage<>(new StartingEvent("123")));
        assertEquals(1, repositoryContents("123").size());
    }

    @Test
    public void testCreationPolicy_OneAlreadyExists() throws Exception {
        manager.accept(new GenericEventMessage<>(new StartingEvent("123")));
        manager.accept(new GenericEventMessage<>(new StartingEvent("123")));
        assertEquals(1, repositoryContents("123").size());
    }

    @Test
    public void testHandleUnrelatedEvent() throws Exception {
        manager.accept(new GenericEventMessage<>("Unrelated"));
        verify(sagaRepository, never()).find(isNull(AssociationValue.class));
    }

    @Test
    public void testCreationPolicy_CreationForced() throws Exception {
        StartingEvent startingEvent = new StartingEvent("123");
        manager.accept(new GenericEventMessage<>(startingEvent));
        manager.accept(new GenericEventMessage<>(new ForcingStartEvent("123")));
        Collection<MyTestSaga> sagas = repositoryContents("123");
        assertEquals(2, sagas.size());
        for (MyTestSaga saga : sagas) {
            if (saga.getCapturedEvents().contains(startingEvent)) {
                assertEquals(2, saga.getCapturedEvents().size());
            }
            assertTrue(saga.getCapturedEvents().size() >= 1);
        }
    }

    @Test
    public void testCreationPolicy_SagaNotCreated() throws Exception {
        manager.accept(new GenericEventMessage<>(new MiddleEvent("123")));
        assertEquals(0, repositoryContents("123").size());
    }

    @Test
    public void testMostSpecificHandlerEvaluatedFirst() throws Exception {
        manager.accept(new GenericEventMessage<>(new StartingEvent("12")));
        manager.accept(new GenericEventMessage<>(new StartingEvent("23")));
        assertEquals(1, repositoryContents("12").size());
        assertEquals(1, repositoryContents("23").size());

        manager.accept(new GenericEventMessage<>(new MiddleEvent("12")));
        manager.accept(new GenericEventMessage<>(new MiddleEvent("23"), singletonMap("catA", "value")));
        assertEquals(0, (int) repositoryContents("12").iterator().next().getSpecificHandlerInvocations());
        assertEquals(1, (int) repositoryContents("23").iterator().next().getSpecificHandlerInvocations());
    }

    @Test
    public void testLifecycle_DestroyedOnEnd() throws Exception {
        manager.accept(new GenericEventMessage<>(new StartingEvent("12")));
        manager.accept(new GenericEventMessage<>(new StartingEvent("23")));
        manager.accept(new GenericEventMessage<>(new MiddleEvent("12")));
        manager.accept(new GenericEventMessage<>(new MiddleEvent("23"), singletonMap("catA",
                                                                                     "value")));
        assertEquals(1, repositoryContents("12").size());
        assertEquals(1, repositoryContents("23").size());
        assertEquals(0, (int) repositoryContents("12").iterator().next().getSpecificHandlerInvocations());
        assertEquals(1, (int) repositoryContents("23").iterator().next().getSpecificHandlerInvocations());
        manager.accept(new GenericEventMessage<>(new EndingEvent("12")));
        assertEquals(1, repositoryContents("23").size());
        assertEquals(0, repositoryContents("12").size());
        manager.accept(new GenericEventMessage<>(new EndingEvent("23")));
        assertEquals(0, repositoryContents("23").size());
        assertEquals(0, repositoryContents("12").size());
    }

    @Test
    public void testNullAssociationValueDoesNotThrowNullPointer() throws Exception {
        manager.accept(asEventMessage(new StartingEvent(null)));
    }

    @Test
    public void testLifeCycle_ExistingInstanceIgnoresEvent() throws Exception {
        manager.accept(new GenericEventMessage<>(new StartingEvent("12")));
        manager.accept(new GenericEventMessage<>(new StubDomainEvent()));
        assertEquals(1, repositoryContents("12").size());
        assertEquals(1, repositoryContents("12").iterator().next().getCapturedEvents().size());
    }

    @Test
    public void testLifeCycle_IgnoredEventDoesNotCreateInstance() throws Exception {
        manager.accept(new GenericEventMessage<>(new StubDomainEvent()));
        assertEquals(0, repositoryContents("12").size());
    }

    private Collection<MyTestSaga> repositoryContents(String lookupValue) {
        return sagaStore.findSagas(MyTestSaga.class, new AssociationValue("myIdentifier", lookupValue))
                .stream()
                .map(id -> sagaStore.loadSaga(MyTestSaga.class, id))
                .map(SagaStore.Entry::saga)
                .collect(Collectors.toList());
    }

    public static class MyTestSaga {

        private static final long serialVersionUID = -1562911263884220240L;
        private List<Object> capturedEvents = new LinkedList<>();
        private int specificHandlerInvocations = 0;

        @StartSaga
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(StartingEvent event) throws InterruptedException {
            capturedEvents.add(event);
        }

        @StartSaga
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(SlowStartingEvent event) throws InterruptedException {
            event.getStartCdl().countDown();
            capturedEvents.add(event);
            Thread.sleep(event.getDuration());
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
        }

        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleMiddleEvent(MiddleEvent event) {
            capturedEvents.add(event);
        }

        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSpecificMiddleEvent(MiddleEvent event, @MetaData(value = "catA", required = true) String category) {
            // this handler is more specific, but requires meta data that not all events might have
            capturedEvents.add(event);
            specificHandlerInvocations++;
        }

        public List<Object> getCapturedEvents() {
            return capturedEvents;
        }

        public int getSpecificHandlerInvocations() {
            return specificHandlerInvocations;
        }
    }

    public static abstract class MyIdentifierEvent {

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

    public static class OtherStartingEvent extends MyIdentifierEvent {

        private final CountDownLatch countDownLatch;

        protected OtherStartingEvent(String myIdentifier) {
            this(myIdentifier, null);
        }

        public OtherStartingEvent(String id, CountDownLatch countDownLatch) {
            super(id);
            this.countDownLatch = countDownLatch;
        }
    }

    public static class SlowStartingEvent extends StartingEvent {


        private final CountDownLatch startCdl;
        private final long duration;

        protected SlowStartingEvent(String myIdentifier, CountDownLatch startCdl, long duration) {
            super(myIdentifier);
            this.startCdl = startCdl;
            this.duration = duration;
        }

        public long getDuration() {
            return duration;
        }

        public CountDownLatch getStartCdl() {
            return startCdl;
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

    private class HandleEventTask implements Runnable {

        private final EventMessage<?> eventMessage;

        public HandleEventTask(EventMessage<?> eventMessage) {
            this.eventMessage = eventMessage;
        }

        @Override
        public void run() {
            try {
                manager.accept(eventMessage);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                fail("The handler failed to handle the message");
            }
        }
    }
}
