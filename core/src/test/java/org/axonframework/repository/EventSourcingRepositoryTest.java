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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
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
import org.axonframework.eventsourcing.EventStreamDecorator;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        StubDomainEvent event1 = new StubDomainEvent(identifier, 1);
        StubDomainEvent event2 = new StubDomainEvent(identifier, 2);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(new SimpleDomainEventStream(event1, event2));

        TestAggregate aggregate = testSubject.load(identifier, null);

        assertEquals(0, aggregate.getUncommittedEventCount());
        assertEquals(2, aggregate.getHandledEvents().size());
        assertSame(event1, aggregate.getHandledEvents().get(0));
        assertSame(event2, aggregate.getHandledEvents().get(1));

        // now the aggregate is loaded (and hopefully correctly locked)
        StubDomainEvent event3 = new StubDomainEvent(identifier);

        aggregate.apply(event3);

        CurrentUnitOfWork.commit();

        verify(mockEventBus).publish(event3);
        verify(mockEventBus, never()).publish(event1);
        verify(mockEventBus, never()).publish(event2);
        verify(mockEventStore, times(1)).appendEvents(eq("test"), isA(DomainEventStream.class));
        assertEquals(0, aggregate.getUncommittedEventCount());
    }

    @Test
    public void testLoadDeletedAggregate() {
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        StubDomainEvent event1 = new StubDomainEvent(identifier, 1);
        StubDomainEvent event2 = new StubDomainEvent(identifier, 2);
        StubAggregateDeletedEvent event3 = new StubAggregateDeletedEvent(identifier, 3);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(event1, event2, event3));

        try {
            testSubject.load(identifier, null);
            fail("Expected AggregateDeletedException");
        } catch (AggregateDeletedException e) {
            assertTrue(e.getMessage().contains(identifier.toString()));
        }
    }

    @Test
    public void testLoadWithAggregateSnapshot() {
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        TestAggregate simpleAggregate = new TestAggregate(identifier);
        simpleAggregate.apply(new StubDomainEvent(identifier, 0));
        simpleAggregate.commitEvents();
        AggregateSnapshot<AbstractEventSourcedAggregateRoot> snapshotEvent =
                new AggregateSnapshot<AbstractEventSourcedAggregateRoot>(simpleAggregate);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(new SimpleDomainEventStream(snapshotEvent,
                                                                                                   new StubDomainEvent(
                                                                                                           identifier,
                                                                                                           1)));
        EventSourcedAggregateRoot actual = testSubject.load(identifier, null);

        assertSame(simpleAggregate, actual);
        assertEquals(Long.valueOf(1), actual.getVersion());
    }

    @Test
    public void testLoadAndSaveWithConflictingChanges() {
        ConflictResolver conflictResolver = mock(ConflictResolver.class);
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        DomainEvent event2 = new StubDomainEvent(identifier, 2);
        DomainEvent event3 = new StubDomainEvent(identifier, 3);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new StubDomainEvent(identifier, 1),
                                            event2,
                                            event3));
        testSubject.setConflictResolver(conflictResolver);
        TestAggregate actual = testSubject.load(identifier, 1L);
        verify(conflictResolver, never()).resolveConflicts(anyListOf(DomainEvent.class), anyListOf(DomainEvent.class));
        DomainEvent appliedEvent = new StubDomainEvent();
        actual.apply(appliedEvent);

        CurrentUnitOfWork.commit();

        verify(conflictResolver).resolveConflicts(Arrays.asList(appliedEvent), Arrays.asList(event2, event3));
    }

    @Test(expected = ConflictingAggregateVersionException.class)
    public void testLoadWithConflictingChanges_NoConflictResolverSet() {
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        DomainEvent event2 = new StubDomainEvent(identifier, 2);
        DomainEvent event3 = new StubDomainEvent(identifier, 3);
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new StubDomainEvent(identifier, 1),
                                            event2,
                                            event3));

        testSubject.load(identifier, 1L);
    }

    @Test
    public void testLoadAndSaveWithoutConflictingChanges() {
        ConflictResolver conflictResolver = mock(ConflictResolver.class);
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new StubDomainEvent(identifier, 1),
                                            new StubDomainEvent(identifier, 2),
                                            new StubDomainEvent(identifier, 3)));
        testSubject.setConflictResolver(conflictResolver);
        TestAggregate actual = testSubject.load(identifier, 3L);
        verify(conflictResolver, never()).resolveConflicts(anyListOf(DomainEvent.class), anyListOf(DomainEvent.class));
        actual.apply(new StubDomainEvent());

        CurrentUnitOfWork.commit();

        verify(conflictResolver, never()).resolveConflicts(anyListOf(DomainEvent.class), anyListOf(DomainEvent.class));
    }

    @Test
    public void testLoadEventsWithDecorators() {
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        SpyEventStreamDecorator decorator1 = new SpyEventStreamDecorator();
        SpyEventStreamDecorator decorator2 = new SpyEventStreamDecorator();
        testSubject.setEventStreamDecorators(Arrays.asList(decorator1, decorator2));
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new StubDomainEvent(identifier, 1),
                                            new StubDomainEvent(identifier, 2),
                                            new StubDomainEvent(identifier, 3)));
        TestAggregate aggregate = testSubject.load(identifier);
        // loading them in...
        InOrder inOrder = Mockito.inOrder(decorator1.lastSpy, decorator2.lastSpy);
        inOrder.verify(decorator2.lastSpy).next();
        inOrder.verify(decorator1.lastSpy).next();

        inOrder.verify(decorator2.lastSpy).next();
        inOrder.verify(decorator1.lastSpy).next();

        inOrder.verify(decorator2.lastSpy).next();
        inOrder.verify(decorator1.lastSpy).next();
        aggregate.apply(new StubDomainEvent());
        aggregate.apply(new StubDomainEvent());
    }

    @Test
    public void testSaveEventsWithDecorators() {
        SpyEventStreamDecorator decorator1 = new SpyEventStreamDecorator();
        SpyEventStreamDecorator decorator2 = new SpyEventStreamDecorator();
        testSubject.setEventStreamDecorators(Arrays.asList(decorator1, decorator2));
        testSubject.setEventStore(new EventStore() {
            @Override
            public void appendEvents(String type, DomainEventStream events) {
                while (events.hasNext()) {
                    events.next();
                }
            }

            @Override
            public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
                return mockEventStore.readEvents(type, identifier);
            }
        });
        AggregateIdentifier identifier = AggregateIdentifierFactory.randomIdentifier();
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new StubDomainEvent(identifier, 3)));
        TestAggregate aggregate = testSubject.load(identifier);
        aggregate.apply(new StubDomainEvent());
        aggregate.apply(new StubDomainEvent());

        CurrentUnitOfWork.commit();

        InOrder inOrder = Mockito.inOrder(decorator1.lastSpy, decorator2.lastSpy);
        inOrder.verify(decorator1.lastSpy).next();
        inOrder.verify(decorator2.lastSpy).next();

        inOrder.verify(decorator1.lastSpy).next();
        inOrder.verify(decorator2.lastSpy).next();

    }

    private static class EventSourcingRepositoryImpl extends EventSourcingRepository<TestAggregate> {

        @Override
        public TestAggregate instantiateAggregate(AggregateIdentifier aggregateIdentifier,
                                                  DomainEvent event) {
            return new TestAggregate(aggregateIdentifier);
        }

        @Override
        public String getTypeIdentifier() {
            return "test";
        }
    }

    private static class TestAggregate extends AbstractEventSourcedAggregateRoot {

        private List<Event> handledEvents = new ArrayList<Event>();

        private TestAggregate(AggregateIdentifier identifier) {
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

    public static class SpyEventStreamDecorator implements EventStreamDecorator {

        private DomainEventStream lastSpy;

        @Override
        public DomainEventStream decorateForRead(final String aggregateType, AggregateIdentifier aggregateIdentifier,
                                                 final DomainEventStream eventStream) {
            createSpy(eventStream);
            return lastSpy;
        }

        @Override
        public DomainEventStream decorateForAppend(final String aggregateType, EventSourcedAggregateRoot aggregate,
                                                   DomainEventStream eventStream) {
            createSpy(eventStream);
            return lastSpy;
        }

        private void createSpy(final DomainEventStream eventStream) {
            lastSpy = mock(DomainEventStream.class);
            when(lastSpy.next()).thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    return eventStream.next();
                }
            });
            when(lastSpy.hasNext()).thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    return eventStream.hasNext();
                }
            });
            when(lastSpy.peek()).thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    return eventStream.peek();
                }
            });
        }
    }
}
