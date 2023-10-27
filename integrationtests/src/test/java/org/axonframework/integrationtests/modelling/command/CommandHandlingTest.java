/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.modelling.command;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.integrationtests.utils.StubAggregate;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;

class CommandHandlingTest {

    private EventSourcingRepository<StubAggregate> repository;
    private String aggregateIdentifier;
    private StubEventStore stubEventStore;

    @BeforeEach
    void setUp() {
        stubEventStore = StubEventStore.builder().build();
        repository = EventSourcingRepository.builder(StubAggregate.class)
                .eventStore(stubEventStore)
                .build();
        aggregateIdentifier = "testAggregateIdentifier";
    }

    @Test
    void commandHandlerLoadsSameAggregateTwice() throws Exception {
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

        private final List<DomainEventMessage<?>> storedEvents = new LinkedList<>();

        private StubEventStore(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        public DomainEventStream readEvents(@Nonnull String identifier) {
            return DomainEventStream.of(new ArrayList<>(storedEvents));
        }

        @Override
        protected void commit(List<? extends EventMessage<?>> events) {
            storedEvents.addAll(events.stream().map(StubEventStore::asDomainEventMessage).collect(Collectors.toList()));
        }

        private static <T> DomainEventMessage<T> asDomainEventMessage(EventMessage<T> event) {
            return event instanceof DomainEventMessage<?>
                    ? (DomainEventMessage<T>) event
                    : new GenericDomainEventMessage<>(null, event.getIdentifier(), 0L, event, event::getTimestamp);
        }

        @Override
        public void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot) {
        }

        @Override
        public TrackingEventStream openStream(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        private static class Builder extends AbstractEventBus.Builder {

            private StubEventStore build() {
                return new StubEventStore(this);
            }
        }
    }
}
