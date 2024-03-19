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

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
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
    private EventSourcingRepository<StubAggregateForCreation> repository;
    private AtomicInteger factoryInvocationCounter;

    @BeforeEach
    void setUp() {
        this.commandBus = new SimpleCommandBus();
        eventStore = spy(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build());
        repository = EventSourcingRepository.builder(StubAggregateForCreation.class)
                                            .eventStore(eventStore)
                                            .build();
        factoryInvocationCounter = new AtomicInteger(0);
    }

    @Test
    void constructorCreationWithoutFactory() {
        createAndRegisterDefaultCommandHandler();
        String aggregateId = UUID.randomUUID().toString();
        commandBus.dispatch(
                asCommandMessage(new StubAggregateForCreation.ConstructorCommand(aggregateId)), ProcessingContext.NONE
        );

        List<? extends DomainEventMessage<?>> events = eventStore.readEvents(aggregateId).asStream()
                                                                 .collect(Collectors.toList());
        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(aggregateId, events.get(0).getAggregateIdentifier());
    }

    @Test
    void createAlwaysCreationWithoutFactory() {
        createAndRegisterDefaultCommandHandler();
        String aggregateId = UUID.randomUUID().toString();
        commandBus.dispatch(
                asCommandMessage(new StubAggregateForCreation.CreateAlwaysCommand(aggregateId)), ProcessingContext.NONE
        );

        List<? extends DomainEventMessage<?>> events = eventStore.readEvents(aggregateId).asStream()
                                                                 .collect(Collectors.toList());
        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(aggregateId, events.get(0).getAggregateIdentifier());
    }

    @Test
    void createIfMissingCreationWithoutFactory() {
        createAndRegisterDefaultCommandHandler();
        String aggregateId = UUID.randomUUID().toString();
        commandBus.dispatch(
                asCommandMessage(new StubAggregateForCreation.CreateIfMissingCommand(aggregateId)),
                ProcessingContext.NONE
        );

        List<? extends DomainEventMessage<?>> events = eventStore.readEvents(aggregateId).asStream()
                                                                 .collect(Collectors.toList());
        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(aggregateId, events.get(0).getAggregateIdentifier());
    }

    @Test
    void constructorCreationWithFactoryConfiguredButNotInUse() {
        createAndRegisterCommandHandlerWithFactory();
        String aggregateId = UUID.randomUUID().toString();
        commandBus.dispatch(
                asCommandMessage(new StubAggregateForCreation.ConstructorCommand(aggregateId)), ProcessingContext.NONE
        );

        List<? extends DomainEventMessage<?>> events = eventStore.readEvents(aggregateId).asStream()
                                                                 .collect(Collectors.toList());
        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(aggregateId, events.get(0).getAggregateIdentifier());
        Assertions.assertEquals(0, factoryInvocationCounter.get());
    }

    @Test
    void createAlwaysCreationWithFactory() {
        createAndRegisterCommandHandlerWithFactory();
        String aggregateId = UUID.randomUUID().toString();
        commandBus.dispatch(
                asCommandMessage(new StubAggregateForCreation.CreateAlwaysCommand(aggregateId)), ProcessingContext.NONE
        );

        List<? extends DomainEventMessage<?>> events = eventStore.readEvents(aggregateId).asStream()
                                                                 .collect(Collectors.toList());
        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(aggregateId, events.get(0).getAggregateIdentifier());
        Assertions.assertEquals(1, factoryInvocationCounter.get());
    }

    @Test
    void createIfMissingCreationWithFactory() {
        createAndRegisterCommandHandlerWithFactory();
        String aggregateId = UUID.randomUUID().toString();
        commandBus.dispatch(
                asCommandMessage(new StubAggregateForCreation.CreateIfMissingCommand(aggregateId)),
                ProcessingContext.NONE
        );

        List<? extends DomainEventMessage<?>> events = eventStore.readEvents(aggregateId).asStream()
                                                                 .collect(Collectors.toList());
        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(aggregateId, events.get(0).getAggregateIdentifier());
        Assertions.assertEquals(1, factoryInvocationCounter.get());
    }

    private void createAndRegisterDefaultCommandHandler() {
        AggregateAnnotationCommandHandler<StubAggregateForCreation> ch = AggregateAnnotationCommandHandler
                .<StubAggregateForCreation>builder()
                .repository(repository)
                .aggregateType(StubAggregateForCreation.class)
                .aggregateModel(new AnnotatedAggregateMetaModelFactory().createModel(StubAggregateForCreation.class))
                .build();
        //noinspection resource
        ch.subscribe(commandBus);
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
        //noinspection resource
        ch.subscribe(commandBus);
    }
}
