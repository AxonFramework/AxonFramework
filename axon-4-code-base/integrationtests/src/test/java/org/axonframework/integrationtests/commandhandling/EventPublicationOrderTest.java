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

import org.axonframework.commandhandling.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.UUID;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
class EventPublicationOrderTest {

    private CommandBus commandBus;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        this.commandBus = SimpleCommandBus.builder().build();
        eventStore = spy(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build());
        EventSourcingRepository<StubAggregate> repository = EventSourcingRepository.builder(StubAggregate.class)
                                                                                   .eventStore(eventStore)
                                                                                   .build();
        StubAggregateCommandHandler target = new StubAggregateCommandHandler();
        target.setRepository(repository);
        target.setEventBus(eventStore);
        new AnnotationCommandHandlerAdapter<>(target).subscribe(commandBus);
    }

    @Test
    void publicationOrderIsMaintained_AggregateAdded() {
        String aggregateId = UUID.randomUUID().toString();
        GenericDomainEventMessage<StubAggregateCreatedEvent> event =
                new GenericDomainEventMessage<>("test", aggregateId, 0, new StubAggregateCreatedEvent(aggregateId));
        when(eventStore.readEvents(aggregateId)).thenReturn(DomainEventStream.of(event));
        doAnswer(invocation -> Void.class).when(eventStore).publish(isA(EventMessage.class));
        commandBus.dispatch(
                asCommandMessage(new UpdateStubAggregateWithExtraEventCommand(aggregateId)), NoOpCallback.INSTANCE
        );
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
