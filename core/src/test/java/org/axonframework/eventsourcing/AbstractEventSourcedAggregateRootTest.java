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

package org.axonframework.eventsourcing;

import org.axonframework.domain.*;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.testutils.RecordingEventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractEventSourcedAggregateRootTest {

    private CompositeAggregateRoot testSubject;
    private RecordingEventBus eventBus;
    private String identifier = "aggregateIdentifier";

    @Before
    public void setUp() {
        eventBus = new RecordingEventBus();
        UnitOfWork unitOfWork = DefaultUnitOfWork.startAndGet(null);
        unitOfWork.resources().put(EventBus.KEY, eventBus);
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testInitializeWithEvents() {
        testSubject = new CompositeAggregateRoot();
        testSubject.initializeState(new SimpleDomainEventStream(new GenericDomainEventMessage<>(
                identifier,
                (long) 243,
                "Mock contents", MetaData
                .emptyInstance()
        )));

        assertEquals(identifier, testSubject.getIdentifier());
        assertEquals(0, eventBus.getPublishedEventCount());
        assertEquals(1, testSubject.getInvocationCount());
        assertEquals(1, testSubject.getSimpleEntity().getInvocationCount());
        assertEquals(new Long(243), testSubject.getVersion());
    }

    @Test
    public void testApplyEvent() {
        testSubject = new CompositeAggregateRoot(identifier);

        assertNotNull(testSubject.getIdentifier());
        assertEquals(0, eventBus.getPublishedEventCount());
        assertEquals(null, testSubject.getVersion());

        testSubject.apply(new StubDomainEvent());

        assertEquals(1, testSubject.getInvocationCount());
        assertEquals(1, eventBus.getPublishedEventCount());
        assertEquals(1, testSubject.getSimpleEntity().getInvocationCount());
        assertEquals(1, testSubject.getSimpleEntityList().get(0).getInvocationCount());

        testSubject.getSimpleEntity().applyEvent();
        assertEquals(2, testSubject.getInvocationCount());
        assertEquals(2, eventBus.getPublishedEventCount());
        assertEquals(2, testSubject.getSimpleEntity().getInvocationCount());
        assertEquals(2, testSubject.getSimpleEntityList().get(0).getInvocationCount());

        assertEquals(new Long(eventBus.getPublishedEventCount()), testSubject.getVersion());
    }

    /**
     * @author Allard Buijze
     */
    public static class CompositeAggregateRoot extends AbstractEventSourcedAggregateRoot {

        private int invocationCount;
        private SimpleEntity childEntity;
        private List<SimpleEntity> childEntitiesList = new ArrayList<>();
        private String identifier;

        CompositeAggregateRoot(String identifier) {
            this.identifier = identifier;
        }

        public CompositeAggregateRoot() {
        }

        @Override
        protected void handle(EventMessage event) {
            this.identifier = ((DomainEventMessage) event).getAggregateIdentifier();
            this.invocationCount++;
            if (childEntity == null) {
                childEntity = new SimpleEntity();
            }
            childEntitiesList.add(new SimpleEntity());
        }

        public int getInvocationCount() {
            return invocationCount;
        }

        public SimpleEntity getSimpleEntity() {
            return childEntity;
        }

        public List<SimpleEntity> getSimpleEntityList() {
            return childEntitiesList;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        protected Collection<EventSourcedEntity> getChildEntities() {
            List<EventSourcedEntity> children = new ArrayList<>();
            children.add(childEntity);
            children.addAll(childEntitiesList);
            return children;
        }
    }

    /**
     * @author Allard Buijze
     */
    public static class SimpleEntity extends AbstractEventSourcedEntity {

        private int invocationCount;

        @Override
        protected void handle(EventMessage event) {
            this.invocationCount++;
        }

        public int getInvocationCount() {
            return invocationCount;
        }

        public void applyEvent() {
            apply(new StubDomainEvent());
        }

        @Override
        protected Collection<EventSourcedEntity> getChildEntities() {
            return null;
        }
    }
}
