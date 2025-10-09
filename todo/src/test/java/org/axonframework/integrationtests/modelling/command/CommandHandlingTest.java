/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventsourcing.LegacyEventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.integrationtests.commandhandling.StubAggregate;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CommandHandlingTest {

    private LegacyEventSourcingRepository<StubAggregate> repository;
    private String aggregateIdentifier;
    private StubEventStore stubEventStore;

    @BeforeEach
    void setUp() {
        stubEventStore = new StubEventStore();
        repository = LegacyEventSourcingRepository.builder(StubAggregate.class)
//                                                  .eventStore(stubEventStore)
                                                  .build();
        aggregateIdentifier = "testAggregateIdentifier";
    }

    @Test
    @Disabled
    void commandHandlerLoadsSameAggregateTwice() throws Exception {
        LegacyDefaultUnitOfWork.startAndGet(null);
//        repository.newInstance(() -> new StubAggregate(aggregateIdentifier)).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        LegacyDefaultUnitOfWork.startAndGet(null);
//        repository.load(aggregateIdentifier).execute(StubAggregate::doSomething);
//        repository.load(aggregateIdentifier).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        Iterator<? extends DomainEventMessage> es = stubEventStore.readEvents(aggregateIdentifier);
        assertTrue(es.hasNext());
        assertEquals((Object) 0L, es.next().getSequenceNumber());
        assertTrue(es.hasNext());
        assertEquals((Object) 1L, es.next().getSequenceNumber());
        assertTrue(es.hasNext());
        assertEquals((Object) 2L, es.next().getSequenceNumber());
        assertFalse(es.hasNext());
    }

    private static class StubEventStore extends AbstractEventBus {

        private final List<DomainEventMessage> storedEvents = new LinkedList<>();

        public DomainEventStream readEvents(@Nonnull String identifier) {
            return DomainEventStream.of(new ArrayList<>(storedEvents));
        }

        @Override
        protected CompletableFuture<Void> commit(@Nonnull List<? extends EventMessage> events, @Nullable ProcessingContext context) {
            storedEvents.addAll(events.stream().map(StubEventStore::asDomainEventMessage).toList());
            return super.commit(events, context);
        }

        private static DomainEventMessage asDomainEventMessage(EventMessage event) {
            return event instanceof DomainEventMessage e
                    ? e
                    : new GenericDomainEventMessage(null, event.identifier(), 0L, event, event::timestamp);
        }

        public void storeSnapshot(@Nonnull DomainEventMessage snapshot) {
        }

        public TrackingEventStream openStream(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration subscribe(
                @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer) {
            throw new UnsupportedOperationException();
        }

    }
}
