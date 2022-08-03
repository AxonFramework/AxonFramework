/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.disruptor.commandhandling;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.junit.jupiter.api.*;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisruptorRepositoryTest {

    private final EventStore eventStore = mock(EventStore.class);

    @Test
    void testDisruptorCommandBusRepositoryNotAvailableOutsideOfInvokerThread() {
        DisruptorCommandBus commandBus = DisruptorCommandBus.builder().build();
        Repository<TestAggregate> repository = commandBus
                .createRepository(eventStore, new GenericAggregateFactory<>(TestAggregate.class));

        AggregateAnnotationCommandHandler<TestAggregate> handler =
                AggregateAnnotationCommandHandler.<TestAggregate>builder()
                                                 .aggregateType(TestAggregate.class)
                                                 .repository(repository)
                                                 .build();

        handler.subscribe(commandBus);
        DefaultCommandGateway gateway = DefaultCommandGateway.builder().commandBus(commandBus).build();

        // Create the aggregate
        String aggregateId = "" + System.currentTimeMillis();
        gateway.sendAndWait(new CreateCommandAndEvent(aggregateId));

        // Load the aggregate from the repository -- from "worker" thread
        UnitOfWork<CommandMessage<?>> uow = DefaultUnitOfWork.startAndGet(null);
        try {
            repository.load(aggregateId);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("DisruptorCommandBus"));
        } finally {
            uow.rollback();
        }
    }

    public static class CreateCommandAndEvent {

        @TargetAggregateIdentifier
        private final String id;

        public CreateCommandAndEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings("unused")
    public static class TestAggregate {

        @SuppressWarnings("FieldCanBeLocal")
        @AggregateIdentifier
        private String id;

        private TestAggregate() {
        }

        @CommandHandler
        public TestAggregate(CreateCommandAndEvent command) {
            apply(command);
        }

        @EventSourcingHandler
        private void on(CreateCommandAndEvent event) {
            this.id = event.getId();
        }
    }
}
