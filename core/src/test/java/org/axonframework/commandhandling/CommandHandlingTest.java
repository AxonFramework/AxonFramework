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

package org.axonframework.commandhandling;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.*;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 *
 */
public class CommandHandlingTest {

    private EventSourcingRepository<StubAggregate> repository;
    private String aggregateIdentifier;
    private StubEventStore stubEventStore;

    @Before
    public void setUp() {
        stubEventStore = new StubEventStore();
        repository = new EventSourcingRepository<>(StubAggregate.class, stubEventStore);
        aggregateIdentifier = "testAggregateIdentifier";
    }

    @Test
    public void testCommandHandlerLoadsSameAggregateTwice() throws Exception {
        DefaultUnitOfWork.startAndGet(null);
        repository.newInstance(() -> new StubAggregate(aggregateIdentifier)).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet(null);
        repository.load(aggregateIdentifier).execute(StubAggregate::doSomething);
        repository.load(aggregateIdentifier).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        Iterator<? extends DomainEventMessage<?>> es = stubEventStore.readEvents(aggregateIdentifier);
        assertTrue(es.hasNext());
        assertEquals((Object) 0L, es.next().getSequenceNumber());
        assertTrue(es.hasNext());
        assertEquals((Object) 1L, es.next().getSequenceNumber());
        assertTrue(es.hasNext());
        assertEquals((Object) 2L, es.next().getSequenceNumber());
        assertFalse(es.hasNext());
    }

    private static class StubEventStore extends AbstractEventBus implements EventStore {

        private List<DomainEventMessage<?>> storedEvents = new LinkedList<>();

        @Override
        public DomainEventStream readEvents(String identifier) {
            return DomainEventStream.of(new ArrayList<>(storedEvents).iterator());
        }

        @Override
        protected void commit(List<? extends EventMessage<?>> events) {
            storedEvents.addAll(events.stream().map(EventUtils::asDomainEventMessage).collect(Collectors.toList()));
        }

        @Override
        public TrackingEventStream streamEvents(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }
    }
}
