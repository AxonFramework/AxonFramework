/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.integrationtests.loopbacktest.synchronous;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.callbacks.VoidCallback;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.LockingStrategy;
import org.axonframework.repository.Repository;
import org.junit.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for issue #119
 *
 * @author Allard Buijze
 */
public class SynchronousLoopbackTest {

    private CommandBus commandBus;
    private EventBus eventBus;
    private UUID aggregateIdentifier;
    private EventStore eventStore;
    private VoidCallback reportErrorCallback;
    private CommandCallback<Object> expectErrorCallback;

    @Before
    public void setUp() {
        aggregateIdentifier = UUID.randomUUID();
        commandBus = new SimpleCommandBus();
        eventBus = new SimpleEventBus();
        eventStore = spy(new InMemoryEventStore());
        eventStore.appendEvents("CountingAggregate", new SimpleDomainEventStream(
                new GenericDomainEventMessage<AggregateCreatedEvent>(aggregateIdentifier, 0,
                                                                     new AggregateCreatedEvent(aggregateIdentifier),
                                                                     null
                )));
        reset(eventStore);

        reportErrorCallback = new VoidCallback() {
            @Override
            protected void onSuccess() {

            }

            @Override
            public void onFailure(Throwable cause) {
                throw new RuntimeException("Failure", cause);
            }
        };
        expectErrorCallback = new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Expected this command to fail");
            }

            @Override
            public void onFailure(Throwable cause) {
                assertEquals("Mock exception", cause.getMessage());
            }
        };
    }

    protected void initializeRepository(LockingStrategy lockingStrategy) {
        EventSourcingRepository<CountingAggregate> repository = new EventSourcingRepository<CountingAggregate>(
                CountingAggregate.class,
                lockingStrategy);
        repository.setEventBus(eventBus);
        repository.setEventStore(this.eventStore);

        AnnotationCommandHandlerAdapter.subscribe(new CounterCommandHandler(repository), commandBus);
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_PessimisticLocking() {
        initializeRepository(LockingStrategy.PESSIMISTIC);
        EventListener el = new EventListener() {
            @Override
            public void handle(EventMessage event) {
                DomainEventMessage domainEvent = (DomainEventMessage) event;
                if (event.getPayload() instanceof CounterChangedEvent) {
                    CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event.getPayload();
                    if (counterChangedEvent.getCounter() == 1) {
                        commandBus.dispatch(asCommandMessage(
                                new ChangeCounterCommand((UUID) domainEvent.getAggregateIdentifier(),
                                                         counterChangedEvent.getCounter() + 1)), reportErrorCallback);
                        commandBus.dispatch(asCommandMessage(
                                new ChangeCounterCommand((UUID) domainEvent.getAggregateIdentifier(),
                                                         counterChangedEvent.getCounter() + 2)), reportErrorCallback);
                    }
                }
            }
        };
        eventBus.subscribe(el);

        commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(aggregateIdentifier, 1)), reportErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents("CountingAggregate", aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEventMessage next = storedEvents.next();
            if (next.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next.getPayload();
                assertEquals(event.getCounter(), next.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).appendEvents(eq("CountingAggregate"), isA(DomainEventStream.class));
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_OptimisticLocking() throws Throwable {
        initializeRepository(LockingStrategy.OPTIMISTIC);
        EventListener el = new EventListener() {
            @Override
            public void handle(EventMessage event) {
                DomainEventMessage domainEvent = (DomainEventMessage) event;
                if (event.getPayload() instanceof CounterChangedEvent) {
                    CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event.getPayload();
                    if (counterChangedEvent.getCounter() == 1) {
                        commandBus.dispatch(asCommandMessage(
                                new ChangeCounterCommand((UUID) domainEvent.getAggregateIdentifier(),
                                                         counterChangedEvent.getCounter() + 1)), reportErrorCallback);
                        commandBus.dispatch(asCommandMessage(
                                new ChangeCounterCommand((UUID) domainEvent.getAggregateIdentifier(),
                                                         counterChangedEvent.getCounter() + 2)), reportErrorCallback);
                    }
                }
            }
        };
        eventBus.subscribe(el);

        commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(aggregateIdentifier, 1)), reportErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents("CountingAggregate", aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEventMessage next = storedEvents.next();
            if (next.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next.getPayload();
                assertEquals(event.getCounter(), next.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).appendEvents(eq("CountingAggregate"), isA(DomainEventStream.class));
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_OptimisticLocking_ProcessingFails() throws Throwable {
        initializeRepository(LockingStrategy.OPTIMISTIC);
        EventListener el = new EventListener() {
            @Override
            public void handle(EventMessage event) {
                DomainEventMessage domainEvent = (DomainEventMessage) event;
                if (event.getPayload() instanceof CounterChangedEvent) {
                    CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event.getPayload();
                    if (counterChangedEvent.getCounter() == 1) {
                        commandBus.dispatch(asCommandMessage(
                                new ChangeCounterCommand((UUID) domainEvent.getAggregateIdentifier(),
                                                         counterChangedEvent.getCounter() + 1)), reportErrorCallback);
                        commandBus.dispatch(asCommandMessage(
                                new ChangeCounterCommand((UUID) domainEvent.getAggregateIdentifier(),
                                                         counterChangedEvent.getCounter() + 2)),
                                            reportErrorCallback);
                    } else if (counterChangedEvent.getCounter() == 2) {
                        throw new RuntimeException("Mock exception");
                    }
                }
            }
        };
        eventBus.subscribe(el);

        commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(aggregateIdentifier, 1)), expectErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents("CountingAggregate", aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEventMessage next = storedEvents.next();
            if (next.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next.getPayload();
                assertEquals(event.getCounter(), next.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).appendEvents(eq("CountingAggregate"), isA(DomainEventStream.class));
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_PessimisticLocking_ProcessingFails() throws Throwable {
        initializeRepository(LockingStrategy.PESSIMISTIC);
        EventListener el = new EventListener() {
            @Override
            public void handle(EventMessage event) {
                DomainEventMessage domainEvent = (DomainEventMessage) event;
                if (event.getPayload() instanceof CounterChangedEvent) {
                    CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event.getPayload();
                    if (counterChangedEvent.getCounter() == 1) {
                        commandBus.dispatch(asCommandMessage(
                                new ChangeCounterCommand((UUID) domainEvent.getAggregateIdentifier(),
                                                         counterChangedEvent.getCounter() + 1)),
                                            reportErrorCallback);
                        commandBus.dispatch(
                                asCommandMessage(new ChangeCounterCommand((UUID) domainEvent.getAggregateIdentifier(),
                                                                          counterChangedEvent.getCounter() + 2)),
                                reportErrorCallback);
                    } else if (counterChangedEvent.getCounter() == 2) {
                        throw new RuntimeException("Mock exception");
                    }
                }
            }
        };
        eventBus.subscribe(el);

        commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(aggregateIdentifier, 1)), expectErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents("CountingAggregate", aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEventMessage next = storedEvents.next();
            if (next.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next.getPayload();
                assertEquals(event.getCounter(), next.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).appendEvents(eq("CountingAggregate"), isA(DomainEventStream.class));
    }

    private static class CounterCommandHandler {

        private Repository<CountingAggregate> repository;

        private CounterCommandHandler(Repository<CountingAggregate> repository) {
            this.repository = repository;
        }

        @CommandHandler
        public void changeCounter(ChangeCounterCommand command) {
            CountingAggregate aggregate = repository.load(command.getAggregateId());
            aggregate.setCounter(command.getNewValue());
        }
    }

    private static class ChangeCounterCommand {

        private UUID aggregateId;
        private int newValue;

        private ChangeCounterCommand(UUID aggregateId, int newValue) {
            this.aggregateId = aggregateId;
            this.newValue = newValue;
        }

        public UUID getAggregateId() {
            return aggregateId;
        }

        public int getNewValue() {
            return newValue;
        }
    }

    private static class AggregateCreatedEvent {

        private final UUID aggregateIdentifier;

        private AggregateCreatedEvent(UUID aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public UUID getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    private static class CountingAggregate extends AbstractAnnotatedAggregateRoot {

        private static final long serialVersionUID = -2927751585905120260L;

        private int counter = 0;

        @AggregateIdentifier
        private UUID identifier;

        private CountingAggregate(UUID identifier) {
            apply(new AggregateCreatedEvent(identifier));
        }

        CountingAggregate() {
        }

        public void setCounter(int newValue) {
            apply(new CounterChangedEvent(newValue));
        }

        @EventHandler
        private void handleCreatedEvent(AggregateCreatedEvent event) {
            this.identifier = event.getAggregateIdentifier();
        }

        @EventHandler
        private void handleCounterIncreased(CounterChangedEvent event) {
            this.counter = event.getCounter();
        }
    }

    private static class CounterChangedEvent {

        private final int counter;

        private CounterChangedEvent(int counter) {
            this.counter = counter;
        }

        public int getCounter() {
            return counter;
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private Map<Object, List<DomainEventMessage>> store = new HashMap<Object, List<DomainEventMessage>>();

        @Override
        public void appendEvents(String identifier, DomainEventStream events) {
            while (events.hasNext()) {
                DomainEventMessage next = events.next();
                if (!store.containsKey(next.getAggregateIdentifier())) {
                    store.put(next.getAggregateIdentifier(), new ArrayList<DomainEventMessage>());
                }
                List<DomainEventMessage> eventList = store.get(next.getAggregateIdentifier());
                eventList.add(next);
            }
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier) {
            List<DomainEventMessage> events = store.get(identifier);
            events = events == null ? new ArrayList<DomainEventMessage>() : events;
            return new SimpleDomainEventStream(events);
        }
    }
}
