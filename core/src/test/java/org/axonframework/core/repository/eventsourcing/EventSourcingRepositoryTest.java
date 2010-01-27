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

package org.axonframework.core.repository.eventsourcing;

import org.axonframework.core.AbstractEventSourcedAggregateRoot;
import org.axonframework.core.AggregateDeletedEvent;
import org.axonframework.core.DomainEvent;
import org.axonframework.core.DomainEventStream;
import org.axonframework.core.Event;
import org.axonframework.core.SimpleDomainEventStream;
import org.axonframework.core.StubAggregateDeletedEvent;
import org.axonframework.core.StubDomainEvent;
import org.axonframework.core.eventhandler.EventBus;
import org.junit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventSourcingRepositoryTest {

    private EventStore mockEventStore;
    private EventBus mockEventBus;
    private EventSourcingRepository<TestAggregate> testSubject;

    @Before
    public void setUp() {
        mockEventStore = mock(EventStore.class);
        mockEventBus = mock(EventBus.class);
        testSubject = new EventSourcingRepositoryImpl();
        testSubject.setEventBus(mockEventBus);
        testSubject.setEventStore(mockEventStore);
    }

    @Test
    public void testLoadAndSaveAggregate() {
        UUID identifier = UUID.randomUUID();
        StubDomainEvent event1 = new StubDomainEvent(identifier, 1);
        StubDomainEvent event2 = new StubDomainEvent(identifier, 2);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(new SimpleDomainEventStream(event1, event2));

        TestAggregate aggregate = testSubject.load(identifier);

        assertEquals(0, aggregate.getUncommittedEventCount());
        assertEquals(2, aggregate.getHandledEvents().size());
        assertSame(event1, aggregate.getHandledEvents().get(0));
        assertSame(event2, aggregate.getHandledEvents().get(1));

        // now the aggregate is loaded (and hopefully correctly locked)
        StubDomainEvent event3 = new StubDomainEvent(identifier);

        aggregate.apply(event3);

        testSubject.save(aggregate);

        verify(mockEventBus).publish(event3);
        verify(mockEventBus, never()).publish(event1);
        verify(mockEventBus, never()).publish(event2);
        verify(mockEventStore, times(1)).appendEvents(eq("test"), isA(DomainEventStream.class));
        assertEquals(0, aggregate.getUncommittedEventCount());
    }

    private static class EventSourcingRepositoryImpl extends EventSourcingRepository<TestAggregate> {

        @Override
        protected TestAggregate instantiateAggregate(UUID aggregateIdentifier) {
            return new TestAggregate(aggregateIdentifier);
        }

        @Override
        protected String getTypeIdentifier() {
            return "test";
        }
    }

    private static class TestAggregate extends AbstractEventSourcedAggregateRoot {

        private List<Event> handledEvents = new ArrayList<Event>();

        private TestAggregate(UUID identifier) {
            super(identifier);
        }

        @Override
        protected void apply(DomainEvent event) {
            super.apply(event);
        }

        @Override
        protected void handle(DomainEvent event) {
            handledEvents.add(event);
        }

        public List<Event> getHandledEvents() {
            return handledEvents;
        }

        @Override
        protected AggregateDeletedEvent createDeletedEvent() {
            return new StubAggregateDeletedEvent();
        }
    }
}
