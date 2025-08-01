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

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.LegacyEventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.LegacyEmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.LegacyEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.LegacyInMemoryEventStorageEngine;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventPublicationOrderTest {

    private CommandBus commandBus;
    private LegacyEventStore eventStore;

    @BeforeEach
    void setUp() {
        this.commandBus = new SimpleCommandBus();
        eventStore = spy(LegacyEmbeddedEventStore.builder()
                                                 .storageEngine(new LegacyInMemoryEventStorageEngine())
                                                 .build());
        LegacyEventSourcingRepository<StubAggregate> repository =
                LegacyEventSourcingRepository.builder(StubAggregate.class)
                                             .eventStore(eventStore)
                                             .build();
        StubAggregateCommandHandler target = new StubAggregateCommandHandler();
        target.setRepository(repository);
        target.setEventBus(eventStore);
        commandBus.subscribe(new AnnotatedCommandHandlingComponent<>(target));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void publicationOrderIsMaintained_AggregateAdded() {
        String aggregateId = UUID.randomUUID().toString();
        UpdateStubAggregateWithExtraEventCommand testPayload = new UpdateStubAggregateWithExtraEventCommand(aggregateId);
        CommandMessage<UpdateStubAggregateWithExtraEventCommand> testCommand =
                new GenericCommandMessage<>(new MessageType("command"), testPayload);
        DomainEventMessage<StubAggregateCreatedEvent> event = new GenericDomainEventMessage<>(
                "test", aggregateId, 0, new MessageType("event"),
                new StubAggregateCreatedEvent(aggregateId)
        );
        when(eventStore.readEvents(aggregateId)).thenReturn(DomainEventStream.of(event));
        doAnswer(invocation -> Void.class).when(eventStore).publish(isA(EventMessage.class));

        CompletableFuture<? extends Message<?>> dispatchingResult = commandBus.dispatch(testCommand, null);
        assertFalse(dispatchingResult.isCompletedExceptionally(), () -> dispatchingResult.exceptionNow().getMessage());

        InOrder inOrder = inOrder(eventStore, eventStore, eventStore);
        inOrder.verify(eventStore).publish(isA(DomainEventMessage.class));
        inOrder.verify(eventStore).publish(argThat(new NotADomainEventMatcher()));
        inOrder.verify(eventStore).publish(isA(DomainEventMessage.class));
    }

    private static class NotADomainEventMatcher implements ArgumentMatcher<EventMessage<?>> {

        @Override
        public boolean matches(EventMessage o) {
            return !(o instanceof DomainEventMessage);
        }
    }
}
