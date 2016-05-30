/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.callbacks.VoidCallback;
import org.axonframework.commandhandling.model.AggregateNotFoundException;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.*;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for issue #119
 *
 * @author Allard Buijze
 */
public class SynchronousLoopbackTest {

    private CommandBus commandBus;
    private EventBus eventBus;
    private String aggregateIdentifier;
    private EventStore eventStore;
    private VoidCallback reportErrorCallback;
    private CommandCallback<Object, Object> expectErrorCallback;

    @Before
    public void setUp() {
        aggregateIdentifier = UUID.randomUUID().toString();
        commandBus = new SimpleCommandBus();
        eventBus = new SimpleEventBus();
        eventStore = spy(new InMemoryEventStore());
        eventStore.appendEvents(Collections.singletonList(
                new GenericDomainEventMessage<>(type, aggregateIdentifier, 0,
                        new AggregateCreatedEvent(aggregateIdentifier),
                        null
                )));
        reset(eventStore);

        reportErrorCallback = new VoidCallback<Object>() {
            @Override
            protected void onSuccess(CommandMessage<?> commandMessage) {
            }

            @Override
            public void onFailure(CommandMessage commandMessage, Throwable cause) {
                throw new RuntimeException("Failure", cause);
            }
        };
        expectErrorCallback = new CommandCallback<Object, Object>() {
            @Override
            public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                fail("Expected this command to fail");
            }

            @Override
            public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                assertEquals("Mock exception", cause.getCause().getMessage());
            }
        };
    }

    protected void initializeRepository(LockFactory lockingStrategy) {
        EventSourcingRepository<CountingAggregate> repository = new EventSourcingRepository<>(
                CountingAggregate.class, eventStore,
                lockingStrategy);
        new AnnotationCommandHandlerAdapter(new CounterCommandHandler(repository)).subscribe(commandBus);
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_PessimisticLocking() {
        initializeRepository(new PessimisticLockFactory());
        EventListener el = event -> {
            DomainEventMessage domainEvent = (DomainEventMessage) event;
            if (event.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event.getPayload();
                if (counterChangedEvent.getCounter() == 1) {
                    commandBus.dispatch(asCommandMessage(
                            new ChangeCounterCommand(domainEvent.getAggregateIdentifier(),
                                                     counterChangedEvent.getCounter() + 1)), reportErrorCallback);
                    commandBus.dispatch(asCommandMessage(
                            new ChangeCounterCommand(domainEvent.getAggregateIdentifier(),
                                                     counterChangedEvent.getCounter() + 2)), reportErrorCallback);
                }
            }
        };
        eventBus.subscribe(eventProcessor);

        commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(aggregateIdentifier, 1)), reportErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents(aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEventMessage next = storedEvents.next();
            if (next.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next.getPayload();
                assertEquals(event.getCounter(), next.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).appendEvents(anyEventList());
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_PessimisticLocking_ProcessingFails() throws Exception {
        initializeRepository(new PessimisticLockFactory());
        EventListener el = event -> {
            DomainEventMessage domainEvent = (DomainEventMessage) event;
            if (event.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event.getPayload();
                if (counterChangedEvent.getCounter() == 1) {
                    commandBus.dispatch(asCommandMessage(
                                                new ChangeCounterCommand(domainEvent.getAggregateIdentifier(),
                                                                         counterChangedEvent.getCounter() + 1)),
                                        reportErrorCallback);
                    commandBus.dispatch(
                            asCommandMessage(new ChangeCounterCommand(domainEvent.getAggregateIdentifier(),
                                                                      counterChangedEvent.getCounter() + 2)),
                            reportErrorCallback);
                } else if (counterChangedEvent.getCounter() == 2) {
                    throw new RuntimeException("Mock exception");
                }
            }
        };
        eventBus.subscribe(eventProcessor);

        commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(aggregateIdentifier, 1)), expectErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents(aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEventMessage next = storedEvents.next();
            if (next.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next.getPayload();
                assertEquals(event.getCounter(), next.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).appendEvents(anyEventList());
    }

    @SuppressWarnings("unchecked")
    private static List<DomainEventMessage<?>> anyEventList() {
        return anyList();
    }

    private static class CounterCommandHandler {

        private Repository<CountingAggregate> repository;

        private CounterCommandHandler(Repository<CountingAggregate> repository) {
            this.repository = repository;
        }

        @CommandHandler
        @SuppressWarnings("unchecked")
        public void changeCounter(ChangeCounterCommand command) {
            repository.load(command.getAggregateId()).execute(r -> r.setCounter(command.getNewValue()));
        }
    }

    private static class ChangeCounterCommand {

        private String aggregateId;
        private int newValue;

        private ChangeCounterCommand(String aggregateId, int newValue) {
            this.aggregateId = aggregateId;
            this.newValue = newValue;
        }

        public String getAggregateId() {
            return aggregateId;
        }

        public int getNewValue() {
            return newValue;
        }
    }

    private static class AggregateCreatedEvent {

        private final String aggregateIdentifier;

        private AggregateCreatedEvent(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    private static class CountingAggregate {

        private static final long serialVersionUID = -2927751585905120260L;

        private int counter = 0;

        @AggregateIdentifier
        private String identifier;

        private CountingAggregate(String identifier) {
            apply(new AggregateCreatedEvent(identifier));
        }

        CountingAggregate() {
        }

        public void setCounter(int newValue) {
            apply(new CounterChangedEvent(newValue));
        }

        @EventSourcingHandler
        private void handleCreatedEvent(AggregateCreatedEvent event) {
            this.identifier = event.getAggregateIdentifier();
        }

        @EventSourcingHandler
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

    private class InMemoryEventStore implements EventStore {

        protected Map<Object, List<DomainEventMessage>> store = new HashMap<>();

        @Override
        public void appendEvents(List<DomainEventMessage<?>> events) {
            for (EventMessage event : events) {
                DomainEventMessage next = (DomainEventMessage) event;
                if (!store.containsKey(next.getAggregateIdentifier())) {
                    store.put(next.getAggregateIdentifier(), new ArrayList<>());
                }
                List<DomainEventMessage> eventList = store.get(next.getAggregateIdentifier());
                eventList.add(next);
            }
        }

        @Override
        public DomainEventStream readEvents(String identifier, long firstSequenceNumber,
                                            long lastSequenceNumber) {
            if (!store.containsKey(identifier)) {
                throw new AggregateNotFoundException(identifier, "Aggregate not found");
            }
            final List<DomainEventMessage> events = store.get(identifier);
            List<DomainEventMessage> filteredEvents = events.stream().filter(
                    message -> message.getSequenceNumber() >= firstSequenceNumber
                            && message.getSequenceNumber() <= lastSequenceNumber).collect(Collectors.toList());
            return new SimpleDomainEventStream(filteredEvents);
        }
    }
}
