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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
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
    private String identifier = "aggregateIdentifier";

    @Test
    public void testInitializeWithEvents() {
        testSubject = new CompositeAggregateRoot();
        testSubject.initializeState(new SimpleDomainEventStream(new GenericDomainEventMessage<String>(
                identifier,
                (long) 243,
                "Mock contents", MetaData
                .emptyInstance()
        )));

        assertEquals(identifier, testSubject.getIdentifier());
        assertEquals(0, testSubject.getUncommittedEventCount());
        assertEquals(1, testSubject.getInvocationCount());
        assertEquals(1, testSubject.getSimpleEntity().getInvocationCount());
        assertEquals(new Long(243), testSubject.getVersion());
    }

    @Test
    public void testApplyEvent() {
        testSubject = new CompositeAggregateRoot(identifier);

        assertNotNull(testSubject.getIdentifier());
        assertEquals(0, testSubject.getUncommittedEventCount());
        assertEquals(null, testSubject.getVersion());

        testSubject.apply(new StubDomainEvent());

        assertEquals(1, testSubject.getInvocationCount());
        assertEquals(1, testSubject.getUncommittedEventCount());
        assertEquals(1, testSubject.getSimpleEntity().getInvocationCount());
        assertEquals(1, testSubject.getSimpleEntityList().get(0).getInvocationCount());

        testSubject.getSimpleEntity().applyEvent();
        assertEquals(2, testSubject.getInvocationCount());
        assertEquals(2, testSubject.getUncommittedEventCount());
        assertEquals(2, testSubject.getSimpleEntity().getInvocationCount());
        assertEquals(2, testSubject.getSimpleEntityList().get(0).getInvocationCount());

        assertEquals(null, testSubject.getVersion());

        testSubject.commitEvents();
        assertEquals(new Long(1), testSubject.getVersion());
        assertFalse(testSubject.getUncommittedEvents().hasNext());
    }

    /**
     * @author Allard Buijze
     */
    public static class CompositeAggregateRoot extends AbstractEventSourcedAggregateRoot {

        private int invocationCount;
        private SimpleEntity childEntity;
        private List<SimpleEntity> childEntitiesList = new ArrayList<SimpleEntity>();
        private String identifier;

        CompositeAggregateRoot(String identifier) {
            this.identifier = identifier;
        }

        public CompositeAggregateRoot() {
        }

        @Override
        protected void handle(DomainEventMessage event) {
            this.identifier = (String) event.getAggregateIdentifier();
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
            List<EventSourcedEntity> children = new ArrayList<EventSourcedEntity>();
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
        protected void handle(DomainEventMessage event) {
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
