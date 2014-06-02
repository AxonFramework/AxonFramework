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

package org.axonframework.eventsourcing.annotation;

import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.persistence.Id;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AbstractAnnotatedAggregateRootTest {

    private SimpleAggregateRoot testSubject;

    @Test
    public void testApplyEvent() {
        testSubject = new SimpleAggregateRoot();

        assertNotNull(testSubject.getIdentifier());
        // the first applied event applies another one
        assertEquals(2, testSubject.getUncommittedEventCount());
        // this proves that a newly added entity is also notified of an event
        assertEquals(2, testSubject.getEntity().invocationCount);

        testSubject.doSomething();

        assertEquals(3, testSubject.invocationCount);
        assertEquals(3, testSubject.getEntity().invocationCount);

        // the nested handler must be invoked second
        assertFalse(testSubject.entity.appliedEvents.get(0).nested);
        assertTrue(testSubject.entity.appliedEvents.get(1).nested);
        assertFalse(testSubject.entity.appliedEvents.get(2).nested);

        DomainEventStream uncommittedEvents = testSubject.getUncommittedEvents();
        int i=0;
        while (uncommittedEvents.hasNext()) {
            assertSame(testSubject.entity.appliedEvents.get(i), uncommittedEvents.next().getPayload());
            i++;
        }
    }

    @Test
    public void testInitializeWithIdentifier() {
        testSubject = new SimpleAggregateRoot(UUID.randomUUID());
        assertEquals(0, testSubject.getUncommittedEventCount());
    }

    @Test
    public void testIdentifierInitialization_LateInitialization() {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate();
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", aggregate.getUncommittedEvents().peek().getAggregateIdentifier());

        DomainEventStream uncommittedEvents = aggregate.getUncommittedEvents();
        assertFalse(((StubDomainEvent)uncommittedEvents.next().getPayload()).nested);
        assertTrue(((StubDomainEvent)uncommittedEvents.next().getPayload()).nested);
    }

    @Test
    public void testIdentifierInitialization_JavaxPersistenceId() {
        JavaxPersistenceIdIdentifiedAggregate aggregate = new JavaxPersistenceIdIdentifiedAggregate();
        assertEquals("lateIdentifier", aggregate.getIdentifier());
        assertEquals("lateIdentifier", aggregate.getUncommittedEvents().peek().getAggregateIdentifier());
    }

    @Test
    public void testSerializationSetsLiveStateToTrue() throws Exception {
        LateIdentifiedAggregate aggregate = new LateIdentifiedAggregate();
        aggregate.commitEvents();
        final XStreamSerializer serializer = new XStreamSerializer();
        SerializedObject<String> serialized = serializer.serialize(aggregate, String.class);

        LateIdentifiedAggregate deserializedAggregate = serializer.deserialize(serialized);
        assertTrue(deserializedAggregate.isLive());
    }

    @Test
    public void testEventNotAppliedInReplayMode() {
        final UUID id = UUID.randomUUID();
        testSubject = new SimpleAggregateRoot(id);
        testSubject.initializeState(new SimpleDomainEventStream(
                new GenericDomainEventMessage<StubDomainEvent>(id.toString(), 0, new StubDomainEvent(false)),
                new GenericDomainEventMessage<StubDomainEvent>(id.toString(), 1, new StubDomainEvent(true))));

        assertEquals(0, testSubject.getUncommittedEventCount());
        assertEquals((Long) 1L, testSubject.getVersion());
        assertEquals(2, testSubject.invocationCount);

        // the nested handler must be invoked second
        assertFalse(testSubject.entity.appliedEvents.get(0).nested);
        assertTrue(testSubject.entity.appliedEvents.get(1).nested);
    }

    private static class LateIdentifiedAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String aggregateIdentifier;

        private LateIdentifiedAggregate() {
            apply(new StubDomainEvent(false));
        }

        @Override
        public boolean isLive() {
            return super.isLive();
        }

        @EventSourcingHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            aggregateIdentifier = "lateIdentifier";
            if (!event.nested) {
                apply(new StubDomainEvent(true));
            }
        }
    }

    private static class JavaxPersistenceIdIdentifiedAggregate extends AbstractAnnotatedAggregateRoot {

        @Id
        private String aggregateIdentifier;

        private JavaxPersistenceIdIdentifiedAggregate() {
            apply(new StubDomainEvent(false));
        }

        @EventSourcingHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            aggregateIdentifier = "lateIdentifier";
        }
    }

    private static class SimpleAggregateRoot extends AbstractAnnotatedAggregateRoot {

        private int invocationCount;
        @EventSourcedMember
        private SimpleEntity entity;
        @AggregateIdentifier
        private final UUID identifier;

        private SimpleAggregateRoot() {
            identifier = UUID.randomUUID();
            apply(new StubDomainEvent(false));
        }

        private SimpleAggregateRoot(UUID identifier) {
            this.identifier = identifier;
        }

        @EventSourcingHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
            if (entity == null) {
                entity = new SimpleEntity();
                apply(new StubDomainEvent(true));
            }
        }

        public SimpleEntity getEntity() {
            return entity;
        }

        public void doSomething() {
            apply(new StubDomainEvent(false));
        }
    }

    private static class SimpleEntity extends AbstractAnnotatedEntity {

        private int invocationCount;
        private List<StubDomainEvent> appliedEvents = new ArrayList<StubDomainEvent>();

        @EventSourcingHandler
        public void myEventHandlerMethod(StubDomainEvent event) {
            this.invocationCount++;
            appliedEvents.add(event);
        }
    }

    private static class StubDomainEvent implements Serializable {

        private static final long serialVersionUID = 834667054977749990L;

        private final boolean nested;

        private StubDomainEvent(boolean nested) {
            this.nested = nested;
        }

        @Override
        public String toString() {
            return "StubDomainEvent";
        }
    }
}
