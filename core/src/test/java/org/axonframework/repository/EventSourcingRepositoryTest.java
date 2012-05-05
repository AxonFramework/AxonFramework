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

package org.axonframework.repository;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.Message;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.AggregateSnapshot;
import org.axonframework.eventsourcing.ConflictResolver;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.EventStreamDecorator;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

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
    private UnitOfWork unitOfWork;

    @Before
    public void setUp() {
        mockEventStore = mock(SnapshotEventStore.class);
        mockEventBus = mock(EventBus.class);
        testSubject = new EventSourcingRepository<TestAggregate>(new SubtAggregateFactory());
        testSubject.setEventBus(mockEventBus);
        testSubject.setEventStore(mockEventStore);
        unitOfWork = DefaultUnitOfWork.startAndGet();
    }

    @After
    public void tearDown() {
        if (unitOfWork.isStarted()) {
            unitOfWork.rollback();
        }
    }

    @Test
    public void testLoadAndSaveAggregate() {
        UUID identifier = UUID.randomUUID();
        DomainEventMessage event1 = new GenericDomainEventMessage<String>(identifier, (long) 1,
                                                                          "Mock contents", MetaData.emptyInstance());
        DomainEventMessage event2 = new GenericDomainEventMessage<String>(identifier, (long) 2,
                                                                          "Mock contents", MetaData.emptyInstance());
        when(mockEventStore.readEvents("test", identifier)).thenReturn(new SimpleDomainEventStream(event1, event2));

        TestAggregate aggregate = testSubject.load(identifier, null);

        assertEquals(0, aggregate.getUncommittedEventCount());
        assertEquals(2, aggregate.getHandledEvents().size());
        assertSame(event1, aggregate.getHandledEvents().get(0));
        assertSame(event2, aggregate.getHandledEvents().get(1));

        // now the aggregate is loaded (and hopefully correctly locked)
        StubDomainEvent event3 = new StubDomainEvent();

        aggregate.apply(event3);

        CurrentUnitOfWork.commit();

        verify(mockEventBus).publish(isA(DomainEventMessage.class));
        verify(mockEventBus, never()).publish(event1);
        verify(mockEventBus, never()).publish(event2);
        verify(mockEventStore, times(1)).appendEvents(eq("test"), isA(DomainEventStream.class));
        assertEquals(0, aggregate.getUncommittedEventCount());
    }

    @Test
    public void testLoadWithAggregateSnapshot() {
        UUID identifier = UUID.randomUUID();
        TestAggregate simpleAggregate = new TestAggregate(identifier);
        simpleAggregate.apply(new GenericDomainEventMessage<String>(identifier, (long) 0,
                                                                    "Mock contents", MetaData.emptyInstance()));
        simpleAggregate.commitEvents();
        AggregateSnapshot<AbstractEventSourcedAggregateRoot> snapshotEvent =
                new AggregateSnapshot<AbstractEventSourcedAggregateRoot>(simpleAggregate);
        when(mockEventStore.readEvents("test", identifier))
                .thenReturn(new SimpleDomainEventStream(snapshotEvent, new GenericDomainEventMessage<String>(
                        identifier,
                        (long) 1,
                        "Mock contents", MetaData
                        .emptyInstance()
                )));
        EventSourcedAggregateRoot actual = testSubject.load(identifier, null);

        assertSame(simpleAggregate, actual);
        assertEquals(Long.valueOf(1), actual.getVersion());
    }

    @Test
    public void testLoadAndSaveWithConflictingChanges() {
        ConflictResolver conflictResolver = mock(ConflictResolver.class);
        UUID identifier = UUID.randomUUID();
        DomainEventMessage event2 = new GenericDomainEventMessage<String>(identifier, (long) 2,
                                                                          "Mock contents", MetaData.emptyInstance());
        DomainEventMessage event3 = new GenericDomainEventMessage<String>(identifier, (long) 3,
                                                                          "Mock contents", MetaData.emptyInstance());
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new GenericDomainEventMessage<String>(identifier, (long) 1,
                                                                                  "Mock contents",
                                                                                  MetaData.emptyInstance()
                ), event2, event3));
        testSubject.setConflictResolver(conflictResolver);
        TestAggregate actual = testSubject.load(identifier, 1L);
        verify(conflictResolver, never()).resolveConflicts(anyListOf(DomainEventMessage.class), anyListOf(
                DomainEventMessage.class));
        final StubDomainEvent appliedEvent = new StubDomainEvent();
        actual.apply(appliedEvent);

        CurrentUnitOfWork.commit();

        verify(conflictResolver).resolveConflicts(payloadsEqual(appliedEvent), eq(Arrays.asList(event2, event3)));
    }

    private List<DomainEventMessage> payloadsEqual(final StubDomainEvent expectedEvent) {
        return argThat(new BaseMatcher<List<DomainEventMessage>>() {
            @Override
            public boolean matches(Object o) {
                return o instanceof List && ((List) o).size() >= 0
                        && ((Message) ((List) o).get(0)).getPayload().equals(expectedEvent);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("List with an event with a")
                           .appendText(expectedEvent.getClass().getName())
                           .appendText(" payload");
            }
        });
    }

    @Test
    public void testLoadWithConflictingChanges_NoConflictResolverSet() {
        UUID identifier = UUID.randomUUID();
        DomainEventMessage event2 = new GenericDomainEventMessage<String>(identifier, (long) 2,
                                                                          "Mock contents", MetaData.emptyInstance());
        DomainEventMessage event3 = new GenericDomainEventMessage<String>(identifier, (long) 3,
                                                                          "Mock contents", MetaData.emptyInstance());
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new GenericDomainEventMessage<String>(identifier, (long) 1,
                                                                                  "Mock contents",
                                                                                  MetaData.emptyInstance()
                ), event2, event3));

        try {
            testSubject.load(identifier, 1L);
            fail("Expected ConflictingAggregateVersionException");
        } catch (ConflictingAggregateVersionException e) {
            assertEquals(identifier, e.getAggregateIdentifier());
            assertEquals(1L, e.getExpectedVersion());
            assertEquals(3L, e.getActualVersion());
        }
    }

    @Test
    public void testLoadAndSaveWithoutConflictingChanges() {
        ConflictResolver conflictResolver = mock(ConflictResolver.class);
        UUID identifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new GenericDomainEventMessage<String>(identifier, (long) 1,
                                                                                  "Mock contents",
                                                                                  MetaData.emptyInstance()
                ),
                                            new GenericDomainEventMessage<String>(identifier, (long) 2,
                                                                                  "Mock contents",
                                                                                  MetaData.emptyInstance()
                                            ),
                                            new GenericDomainEventMessage<String>(identifier, (long) 3,
                                                                                  "Mock contents",
                                                                                  MetaData.emptyInstance()
                                            )));
        testSubject.setConflictResolver(conflictResolver);
        TestAggregate actual = testSubject.load(identifier, 3L);
        verify(conflictResolver, never()).resolveConflicts(anyListOf(DomainEventMessage.class), anyListOf(
                DomainEventMessage.class));
        actual.apply(new StubDomainEvent());

        CurrentUnitOfWork.commit();

        verify(conflictResolver, never()).resolveConflicts(anyListOf(DomainEventMessage.class), anyListOf(
                DomainEventMessage.class));
    }

    @Test
    public void testLoadEventsWithDecorators() {
        UUID identifier = UUID.randomUUID();
        SpyEventPreprocessor decorator1 = new SpyEventPreprocessor();
        SpyEventPreprocessor decorator2 = new SpyEventPreprocessor();
        testSubject.setEventStreamDecorators(Arrays.asList(decorator1, decorator2));
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new GenericDomainEventMessage<String>(identifier, (long) 1,
                                                                                  "Mock contents",
                                                                                  MetaData.emptyInstance()
                ),
                                            new GenericDomainEventMessage<String>(identifier, (long) 2,
                                                                                  "Mock contents",
                                                                                  MetaData.emptyInstance()
                                            ),
                                            new GenericDomainEventMessage<String>(identifier, (long) 3,
                                                                                  "Mock contents",
                                                                                  MetaData.emptyInstance()
                                            )));
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
        SpyEventPreprocessor decorator1 = new SpyEventPreprocessor();
        SpyEventPreprocessor decorator2 = new SpyEventPreprocessor();
        testSubject.setEventStreamDecorators(Arrays.asList(decorator1, decorator2));
        testSubject.setEventStore(new EventStore() {
            @Override
            public void appendEvents(String type, DomainEventStream events) {
                while (events.hasNext()) {
                    events.next();
                }
            }

            @Override
            public DomainEventStream readEvents(String type, Object identifier) {
                return mockEventStore.readEvents(type, identifier);
            }
        });
        UUID identifier = UUID.randomUUID();
        when(mockEventStore.readEvents("test", identifier)).thenReturn(
                new SimpleDomainEventStream(new GenericDomainEventMessage<String>(identifier, (long) 3,
                                                                                  "Mock contents",
                                                                                  MetaData.emptyInstance()
                )));
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

    private static class SubtAggregateFactory implements AggregateFactory<TestAggregate> {

        @Override
        public TestAggregate createAggregate(Object aggregateIdentifier,
                                             DomainEventMessage firstEvent) {
            return new TestAggregate((UUID) aggregateIdentifier);
        }

        @Override
        public String getTypeIdentifier() {
            return "test";
        }
    }

    private static class TestAggregate extends AbstractEventSourcedAggregateRoot {

        private List<EventMessage> handledEvents = new ArrayList<EventMessage>();
        private UUID identifier;

        private TestAggregate(UUID identifier) {
            this.identifier = identifier;
        }

        @Override
        protected void apply(Object eventPayload) {
            super.apply(eventPayload);
        }

        @Override
        protected void handle(DomainEventMessage event) {
            handledEvents.add(event);
        }

        @Override
        protected void initialize(Object aggregateIdentifier) {
            identifier = (UUID) aggregateIdentifier;
        }

        public List<EventMessage> getHandledEvents() {
            return handledEvents;
        }

        public UUID getIdentifier() {
            return identifier;
        }
    }

    public static class SpyEventPreprocessor implements EventStreamDecorator {

        private DomainEventStream lastSpy;

        @Override
        public DomainEventStream decorateForRead(final String aggregateType, Object aggregateIdentifier,
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
