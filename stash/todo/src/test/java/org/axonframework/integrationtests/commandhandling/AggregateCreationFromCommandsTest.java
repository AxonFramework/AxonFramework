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

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.axonframework.messaging.eventsourcing.LegacyEventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.messaging.commandhandling.CommandBusTestUtils.aCommandBus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests various ways to create aggregates based on incoming Commands. Some cases will use aggregate class's
 * constructors while others might use regular or static methods
 *
 * @author Stefan Andjelkovic
 */
class AggregateCreationFromCommandsTest {

    private CommandBus commandBus;
    private EventStore eventStore;
    private LegacyEventSourcingRepository<StubAggregateForCreation> repository;
    private AtomicInteger factoryInvocationCounter;

    @BeforeEach
    void setUp() {
        this.commandBus = aCommandBus();
        eventStore = spy(new StorageEngineBackedEventStore(new InMemoryEventStorageEngine(), new SimpleEventBus(), new AnnotationBasedTagResolver()));
        repository = LegacyEventSourcingRepository.builder(StubAggregateForCreation.class)
                                                  .eventStore(eventStore)
                                                  .build();
        factoryInvocationCounter = new AtomicInteger(0);
    }

    @Test
    @Disabled("TODO #3195 - Migration Module")
    void createAlwaysCreationWithoutFactory() {
        createAndRegisterDefaultCommandHandler();
        String aggregateId = UUID.randomUUID().toString();
        StubAggregateForCreation.CreateAlwaysCommand testPayload =
                new StubAggregateForCreation.CreateAlwaysCommand(aggregateId);
        CommandMessage testCommand =
                new GenericCommandMessage(new MessageType("command"), testPayload);

        CompletableFuture<? extends Message> dispatchingResult =
                commandBus.dispatch(testCommand, null);
        assertFalse(dispatchingResult.isCompletedExceptionally(), () -> dispatchingResult.exceptionNow().getMessage());

//        List<? extends DomainEventMessage> events = eventStore.readEvents(aggregateId).asStream()
//                                                                 .toList();
//        assertEquals(1, events.size());
//        assertEquals(aggregateId, events.getFirst().getAggregateIdentifier());
    }

    @Test
    @Disabled("TODO #3195 - Migration Module")
    void createIfMissingCreationWithoutFactory() {
        createAndRegisterDefaultCommandHandler();
        String aggregateId = UUID.randomUUID().toString();
        StubAggregateForCreation.CreateIfMissingCommand testPayload =
                new StubAggregateForCreation.CreateIfMissingCommand(aggregateId);
        CommandMessage testCommand =
                new GenericCommandMessage(new MessageType("command"), testPayload);

        CompletableFuture<? extends Message> dispatchingResult = commandBus.dispatch(testCommand, null);
        assertFalse(dispatchingResult.isCompletedExceptionally(), () -> dispatchingResult.exceptionNow().getMessage());

//        List<? extends DomainEventMessage> events = eventStore.readEvents(aggregateId).asStream()
//                                                                 .toList();
//        assertEquals(1, events.size());
//        assertEquals(aggregateId, events.getFirst().getAggregateIdentifier());
    }

    @Test
    @Disabled("TODO #3195 - Migration Module")
    void createAlwaysCreationWithFactory() {
        createAndRegisterCommandHandlerWithFactory();
        String aggregateId = UUID.randomUUID().toString();
        StubAggregateForCreation.CreateAlwaysCommand testPayload =
                new StubAggregateForCreation.CreateAlwaysCommand(aggregateId);
        CommandMessage testCommand =
                new GenericCommandMessage(new MessageType("command"), testPayload);

        CompletableFuture<? extends Message> dispatchingResult = commandBus.dispatch(testCommand, null);
        assertFalse(dispatchingResult.isCompletedExceptionally(), () -> dispatchingResult.exceptionNow().getMessage());

//        List<? extends DomainEventMessage> events = eventStore.readEvents(aggregateId).asStream()
//                                                                 .toList();
//        assertEquals(1, events.size());
//        assertEquals(aggregateId, events.getFirst().getAggregateIdentifier());
//        assertEquals(1, factoryInvocationCounter.get());
    }

    @Test
    @Disabled("TODO #3195 - Migration Module")
    void createIfMissingCreationWithFactory() {
        createAndRegisterCommandHandlerWithFactory();
        String aggregateId = UUID.randomUUID().toString();
        StubAggregateForCreation.CreateIfMissingCommand testPayload =
                new StubAggregateForCreation.CreateIfMissingCommand(aggregateId);
        GenericCommandMessage testCommand =
                new GenericCommandMessage(new MessageType("command"), testPayload);

        CompletableFuture<? extends Message> dispatchingResult = commandBus.dispatch(testCommand, null);
        assertFalse(dispatchingResult.isCompletedExceptionally(), () -> dispatchingResult.exceptionNow().getMessage());

//        List<? extends DomainEventMessage> events = eventStore.readEvents(aggregateId).asStream()
//                                                                 .toList();
//        assertEquals(1, events.size());
//        assertEquals(aggregateId, events.getFirst().getAggregateIdentifier());
//        assertEquals(1, factoryInvocationCounter.get());
    }

    private void createAndRegisterDefaultCommandHandler() {
        AggregateAnnotationCommandHandler<StubAggregateForCreation> ch = AggregateAnnotationCommandHandler
                .<StubAggregateForCreation>builder()
                .repository(repository)
                .aggregateType(StubAggregateForCreation.class)
                .aggregateModel(new AnnotatedAggregateMetaModelFactory().createModel(StubAggregateForCreation.class))
                .build();
        commandBus.subscribe(ch);
    }

    private void createAndRegisterCommandHandlerWithFactory() {
        AggregateAnnotationCommandHandler<StubAggregateForCreation> ch = AggregateAnnotationCommandHandler
                .<StubAggregateForCreation>builder()
                .repository(repository)
                .aggregateType(StubAggregateForCreation.class)
                .creationPolicyAggregateFactory(id -> {
                    factoryInvocationCounter.incrementAndGet();
                    return new StubAggregateForCreation(id != null ? id.toString() : "null");
                })
                .aggregateModel(new AnnotatedAggregateMetaModelFactory().createModel(StubAggregateForCreation.class))
                .build();
        commandBus.subscribe(ch);
    }
}
