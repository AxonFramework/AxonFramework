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

package org.axonframework.repository;

import org.axonframework.domain.AggregateDeletedEvent;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.Event;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubAggregateDeletedEvent;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.AggregateDeletedException;
import org.axonframework.eventsourcing.AggregateSnapshot;
import org.axonframework.eventsourcing.ConflictResolver;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.SnapshotEventStore;
import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventSourcingRepositoryTest {

    private SnapshotEventStore mockEventStore;
    private EventBus mockEventBus;
    private EventSourcingRepository<TestAggregate> testSubject;

    @Before
    public void setUp() {
        mockEventStore = mock(SnapshotEventStore.class);
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

        TestAggregate aggregate = testSubject.get(identifier, null);

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

    @Test
    public void testLoadDeletedAggregate() {
        UUID identifier = UUID.randomUUID();
        StubDomainEvent event1 = new StubDomainEvent(identifier, 1);
        StubDomainEvent event2 = new StubDomainEvent(identifier, 2);
        AggregateDeletedEvent event3 = new StubAggregateDeletedEvent(identifier, 3);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(event1, event2, event3));

        try {
            testSubject.get(identifier, null);
            fail("Expected AggregateDeletedException");
        } catch (AggregateDeletedException e) {
            assertTrue(e.getMessage().contains(identifier.toString()));
        }
    }

    @Test
    public void testLoadWithAggregateSnapshot() {
        UUID identifier = UUID.randomUUID();
        TestAggregate simpleAggregate = new TestAggregate(identifier);
        simpleAggregate.apply(new StubDomainEvent(identifier, 0));
        simpleAggregate.commitEvents();
        AggregateSnapshot<AbstractEventSourcedAggregateRoot> snapshotEvent =
                new AggregateSnapshot<AbstractEventSourcedAggregateRoot>(simpleAggregate);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(new SimpleDomainEventStream(snapshotEvent,
                                                                                                   new StubDomainEvent(
                                                                                                           identifier,
                                                                                                           1)));
        EventSourcedAggregateRoot actual = testSubject.get(identifier, null);

        assertSame(simpleAggregate, actual);
        assertEquals(Long.valueOf(1), actual.getVersion());
    }

    @Test
    public void testLoadAndSaveWithConflictingChanges() {
        ConflictResolver conflictResolver = mock(ConflictResolver.class);
        UUID identifier = UUID.randomUUID();
        DomainEvent event2 = new StubDomainEvent(identifier, 2);
        DomainEvent event3 = new StubDomainEvent(identifier, 3);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new StubDomainEvent(identifier, 1),
                                            event2,
                                            event3));
        testSubject.setConflictResolver(conflictResolver);
        TestAggregate actual = testSubject.get(identifier, 1L);
        verify(conflictResolver, never()).resolveConflicts(anyList(), anyList());
        DomainEvent appliedEvent = new StubDomainEvent();
        actual.apply(appliedEvent);
        testSubject.save(actual);
        verify(conflictResolver).resolveConflicts(Arrays.asList(appliedEvent), Arrays.asList(event2, event3));
    }

    @Test(expected = ConflictingAggregateVersionException.class)
    public void testLoadWithConflictingChanges_NoConflictResolverSet() {
        ConflictResolver conflictResolver = mock(ConflictResolver.class);
        UUID identifier = UUID.randomUUID();
        DomainEvent event2 = new StubDomainEvent(identifier, 2);
        DomainEvent event3 = new StubDomainEvent(identifier, 3);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new StubDomainEvent(identifier, 1),
                                            event2,
                                            event3));

        testSubject.get(identifier, 1L);
    }

    @Test
    public void testLoadAndSaveWithoutConflictingChanges() {
        ConflictResolver conflictResolver = mock(ConflictResolver.class);
        UUID identifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new StubDomainEvent(identifier, 1),
                                            new StubDomainEvent(identifier, 2),
                                            new StubDomainEvent(identifier, 3)));
        testSubject.setConflictResolver(conflictResolver);
        TestAggregate actual = testSubject.get(identifier, 3L);
        verify(conflictResolver, never()).resolveConflicts(anyList(), anyList());
        actual.apply(new StubDomainEvent());
        testSubject.save(actual);
        verify(conflictResolver, never()).resolveConflicts(anyList(), anyList());
    }

    private static class EventSourcingRepositoryImpl extends EventSourcingRepository<TestAggregate> {

        @Override
        public TestAggregate instantiateAggregate(UUID aggregateIdentifier, DomainEvent event) {
            return new TestAggregate(aggregateIdentifier);
        }

        @Override
        public String getTypeIdentifier() {
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
    }
}
