/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.AggregateLifecycle;
import org.axonframework.commandhandling.model.ConflictingAggregateVersionException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventSourcingRepositoryTest {

    private EventStore mockEventStore;
    private EventSourcingRepository<TestAggregate> testSubject;
    private UnitOfWork<?> unitOfWork;
    private StubAggregateFactory stubAggregateFactory;

    @Before
    public void setUp() {
        mockEventStore = mock(EventStore.class);
        stubAggregateFactory = new StubAggregateFactory();
        testSubject = new EventSourcingRepository<>(stubAggregateFactory, mockEventStore);
        unitOfWork = DefaultUnitOfWork.startAndGet(new GenericMessage<>("test"));
    }

    @After
    public void tearDown() {
        if (unitOfWork.isActive()) {
            unitOfWork.rollback();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLoadAndSaveAggregate() {
        String identifier = UUID.randomUUID().toString();
        DomainEventMessage event1 = new GenericDomainEventMessage<>("type", identifier, (long) 1, "Mock contents",
                                                                    MetaData.emptyInstance());
        DomainEventMessage event2 = new GenericDomainEventMessage<>("type", identifier, (long) 2, "Mock contents",
                                                                    MetaData.emptyInstance());
        when(mockEventStore.readEvents(identifier)).thenReturn(DomainEventStream.of(event1, event2));

        Aggregate<TestAggregate> aggregate = testSubject.load(identifier, null);

        assertEquals(2, aggregate.invoke(TestAggregate::getHandledEvents).size());
        assertSame(event1, aggregate.invoke(TestAggregate::getHandledEvents).get(0));
        assertSame(event2, aggregate.invoke(TestAggregate::getHandledEvents).get(1));

        // now the aggregate is loaded (and hopefully correctly locked)
        StubDomainEvent event3 = new StubDomainEvent();

        aggregate.execute(r -> r.apply(event3));

        CurrentUnitOfWork.commit();

        verify(mockEventStore, times(1)).publish((EventMessage)anyVararg());
    }

    @Test
    public void testLoad_FirstEventIsSnapshot() {
        String identifier = UUID.randomUUID().toString();
        TestAggregate aggregate = new TestAggregate(identifier);
        when(mockEventStore.readEvents(identifier)).thenReturn(
                DomainEventStream.of(new GenericDomainEventMessage<>("type", identifier, 10, aggregate)));
        assertSame(aggregate, testSubject.load(identifier).getWrappedAggregate().getAggregateRoot());
    }

    @Test
    public void testLoadWithConflictingChanges() {
        String identifier = UUID.randomUUID().toString();
        DomainEventMessage<? extends String> event2 = new GenericDomainEventMessage<>("type", identifier, (long) 2,
                                                                                      "Mock contents",
                                                                                      MetaData.emptyInstance());
        DomainEventMessage<? extends String> event3 = new GenericDomainEventMessage<>("type", identifier, (long) 3,
                                                                                      "Mock contents",
                                                                                      MetaData.emptyInstance());
        when(mockEventStore.readEvents(identifier)).thenReturn(DomainEventStream.of(
                new GenericDomainEventMessage<>("type", identifier, (long) 1, "Mock contents", MetaData.emptyInstance()),
                event2, event3));

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
    public void testLoadWithConflictingChanges_NoConflictResolverSet_UsingTooHighExpectedVersion() {
        String identifier = UUID.randomUUID().toString();
        DomainEventMessage<? extends String> event2 = new GenericDomainEventMessage<>("type", identifier, (long) 2,
                                                                                      "Mock contents",
                                                                                      MetaData.emptyInstance());
        DomainEventMessage<? extends String> event3 = new GenericDomainEventMessage<>("type", identifier, (long) 3,
                                                                                      "Mock contents",
                                                                                      MetaData.emptyInstance());
        when(mockEventStore.readEvents(identifier)).thenReturn(DomainEventStream.of(
                new GenericDomainEventMessage<>("type", identifier, (long) 1, "Mock contents", MetaData.emptyInstance()),
                event2, event3));

        try {
            testSubject.load(identifier, 100L);
            fail("Expected ConflictingAggregateVersionException");
        } catch (ConflictingAggregateVersionException e) {
            assertEquals(identifier, e.getAggregateIdentifier());
            assertEquals(100L, e.getExpectedVersion());
            assertEquals(3L, e.getActualVersion());
        }
    }

    @Test
    public void testLoadEventsWithDecorators() {
        String identifier = UUID.randomUUID().toString();
        SpyEventPreprocessor decorator1 = new SpyEventPreprocessor();
        SpyEventPreprocessor decorator2 = new SpyEventPreprocessor();
        testSubject.setEventStreamDecorators(Arrays.asList(decorator1, decorator2));
        when(mockEventStore.readEvents(identifier)).thenReturn(DomainEventStream.of(
                new GenericDomainEventMessage<>("type", identifier, (long) 1, "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", identifier, (long) 2, "Mock contents", MetaData.emptyInstance()),
                new GenericDomainEventMessage<>("type", identifier, (long) 3, "Mock contents",
                                                MetaData.emptyInstance())));
        Aggregate<TestAggregate> aggregate = testSubject.load(identifier);
        // loading them in...
        InOrder inOrder = Mockito.inOrder(decorator1.lastSpy, decorator2.lastSpy);
        inOrder.verify(decorator2.lastSpy).forEachRemaining(any());
        inOrder.verify(decorator1.lastSpy).next();
        inOrder.verify(decorator1.lastSpy).next();
        inOrder.verify(decorator1.lastSpy).next();
        aggregate.execute(r -> r.apply(new StubDomainEvent()));
        aggregate.execute(r -> r.apply(new StubDomainEvent()));
    }

    //todo fix test
    @Ignore
    @Test
    public void testSaveEventsWithDecorators() {
        testSubject = new EventSourcingRepository<>(stubAggregateFactory, mockEventStore);
        SpyEventPreprocessor decorator1 = spy(new SpyEventPreprocessor());
        SpyEventPreprocessor decorator2 = spy(new SpyEventPreprocessor());
        testSubject.setEventStreamDecorators(Arrays.asList(decorator1, decorator2));
        String identifier = UUID.randomUUID().toString();
        when(mockEventStore.readEvents(identifier)).thenReturn(DomainEventStream.of(
                new GenericDomainEventMessage<>("type", identifier, (long) 3, "Mock contents",
                                                MetaData.emptyInstance())));
        Aggregate<TestAggregate> aggregate = testSubject.load(identifier);
        aggregate.execute(r -> r.apply(new StubDomainEvent()));
        aggregate.execute(r -> r.apply(new StubDomainEvent()));

        CurrentUnitOfWork.commit();

        InOrder inOrder = Mockito.inOrder(decorator1, decorator2);
        inOrder.verify(decorator1).decorateForAppend(eq(aggregate), anyList());
        inOrder.verify(decorator2).decorateForAppend(eq(aggregate), anyList());
    }

    private static class StubAggregateFactory extends AbstractAggregateFactory<TestAggregate> {

        public StubAggregateFactory() {
            super(TestAggregate.class);
        }

        @Override
        public TestAggregate doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent) {
            return new TestAggregate(aggregateIdentifier);
        }

        @Override
        public Class<TestAggregate> getAggregateType() {
            return TestAggregate.class;
        }
    }

    private static class TestAggregate {

        private List<EventMessage<?>> handledEvents = new ArrayList<EventMessage<? extends Object>>();

        @AggregateIdentifier
        private String identifier;

        private TestAggregate(String identifier) {
            this.identifier = identifier;
        }

        public void apply(Object eventPayload) {
            AggregateLifecycle.apply(eventPayload);
        }

        @EventSourcingHandler
        protected void handle(EventMessage event) {
            identifier = ((DomainEventMessage<?>) event).getAggregateIdentifier();
            handledEvents.add(event);
        }

        public List<EventMessage<?>> getHandledEvents() {
            return handledEvents;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    public static class SpyEventPreprocessor implements EventStreamDecorator {

        private DomainEventStream lastSpy;

        @Override
        public DomainEventStream decorateForRead(String aggregateIdentifier,
                                                 DomainEventStream eventStream) {
            createSpy(eventStream);
            return lastSpy;
        }

        @Override
        public List<DomainEventMessage<?>> decorateForAppend(Aggregate<?> aggregate,
                                                             List<DomainEventMessage<?>> events) {
            return events;
        }

        private void createSpy(final DomainEventStream eventStream) {
            lastSpy = mock(DomainEventStream.class);
            when(lastSpy.next()).thenAnswer(invocation -> eventStream.next());
            when(lastSpy.hasNext()).thenAnswer(invocation -> eventStream.hasNext());
            when(lastSpy.peek()).thenAnswer(invocation -> eventStream.peek());
            doAnswer(invocation -> {
                Consumer c = (Consumer) invocation.getArguments()[0];
                while (eventStream.hasNext()) {
                    c.accept(eventStream.next());
                }
                return null;
            }).when(lastSpy).forEachRemaining(any());
        }
    }
}
