/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.messaging.eventhandling.DelegatingEventBus;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.axonframework.messaging.eventsourcing.LegacyEventSourcingRepository;
import org.axonframework.messaging.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.integrationtests.commandhandling.StubAggregate;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

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

    private static class StubEventStore extends DelegatingEventBus {

        private final List<DomainEventMessage> storedEvents = new LinkedList<>();

        /**
         * Constructs the {@code DelegatingEventBus} with the given {@code delegate} to receive calls.
         *
         * @param delegate The {@link EventBus} instance to delegate calls to.
         */
        public StubEventStore() {
            super(new SimpleEventBus());
        }

        public DomainEventStream readEvents(@Nonnull String identifier) {
            return DomainEventStream.of(new ArrayList<>(storedEvents));
        }

        @Override
        public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                               @Nonnull List<EventMessage> events) {
            if (context == null) {
                storeEvents(events);
            } else {
                context.runOnCommit(ctx -> storeEvents(events));
            }
            return super.publish(context, events);
        }

        private void storeEvents(@Nonnull List<EventMessage> events) {
            storedEvents.addAll(
                    events.stream()
                          .map(StubEventStore::asDomainEventMessage)
                          .toList()
            );
        }

        private static DomainEventMessage asDomainEventMessage(EventMessage event) {
            return event instanceof DomainEventMessage e
                    ? e
                    : new GenericDomainEventMessage(null, event.identifier(), 0L, event, event::timestamp);
        }

        @Override
        public Registration subscribe(
                @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer) {
            throw new UnsupportedOperationException();
        }
    }
}
