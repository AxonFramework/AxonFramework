/*
 * Copyright (c) 2010. Axon Framework
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
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.SagaManager;
import org.axonframework.saga.repository.SagaRepository;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.junit.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaTest {

    private SagaRepository sagaRepository;
    private SagaManager manager;

    @Before
    public void setUp() throws Exception {
        sagaRepository = new InMemorySagaRepository();
        manager = new AnnotatedSagaManager(sagaRepository, new SimpleEventBus(), MyTestSaga.class);
    }

    @Test
    public void testCreationPolicy_NoneExists() {
        manager.handle(new StartingEvent("123"));
        assertEquals(1, repositoryContents("123").size());
    }

    @Test
    public void testCreationPolicy_OneAlreadyExists() {
        manager.handle(new StartingEvent("123"));
        manager.handle(new StartingEvent("123"));
        assertEquals(1, repositoryContents("123").size());
    }

    @Test
    public void testCreationPolicy_CreationForced() {
        StartingEvent startingEvent = new StartingEvent("123");
        manager.handle(startingEvent);
        manager.handle(new ForcingStartEvent("123"));
        Set<MyTestSaga> sagas = repositoryContents("123");
        assertEquals(2, sagas.size());
        for (MyTestSaga saga : sagas) {
            if (saga.getCapturedEvents().contains(startingEvent)) {
                assertEquals(2, saga.getCapturedEvents().size());
            }
            assertTrue(saga.getCapturedEvents().size() >= 1);
        }
    }

    @Test
    public void testCreationPolicy_SagaNotCreated() {
        manager.handle(new MiddleEvent("123"));
        assertEquals(0, repositoryContents("123").size());
    }

    @Test
    public void testLifecycle_DestroyedOnEnd() {
        manager.handle(new StartingEvent("12"));
        manager.handle(new StartingEvent("23"));
        manager.handle(new MiddleEvent("12"));
        manager.handle(new MiddleEvent("23"));
        assertEquals(1, repositoryContents("12").size());
        assertEquals(1, repositoryContents("23").size());
        manager.handle(new EndingEvent("12"));
        assertEquals(1, repositoryContents("23").size());
        assertEquals(0, repositoryContents("12").size());
        manager.handle(new EndingEvent("23"));
        assertEquals(0, repositoryContents("23").size());
        assertEquals(0, repositoryContents("12").size());
    }

    @Test
    public void testLifeCycle_ExistingInstanceIgnoresEvent() {
        manager.handle(new StartingEvent("12"));
        manager.handle(new StubDomainEvent());
        assertEquals(1, repositoryContents("12").size());
        assertEquals(1, repositoryContents("12").iterator().next().getCapturedEvents().size());
    }

    @Test
    public void testLifeCycle_IgnoredEventDoesNotCreateInstance() {
        manager.handle(new StubDomainEvent());
        assertEquals(0, repositoryContents("12").size());
    }

    private Set<MyTestSaga> repositoryContents(String lookupValue) {
        return sagaRepository.find(MyTestSaga.class, new AssociationValue("myIdentifier", lookupValue));
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
