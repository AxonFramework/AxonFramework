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
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.Event;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.LockingStrategy;
import org.axonframework.repository.Repository;
import org.junit.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * Tests for issue #119
 *
 * @author Allard Buijze
 */
public class SynchronousLoopbackTest {

    private CommandBus commandBus;
    private EventBus eventBus;
    private UUIDAggregateIdentifier aggregateIdentifier;
    private EventStore eventStore;
    private VoidCallback reportErrorCallback;
    private CommandCallback<Object> expectErrorCallback;

    @Before
    public void setUp() {
        aggregateIdentifier = new UUIDAggregateIdentifier();
        commandBus = new SimpleCommandBus();
        eventBus = new SimpleEventBus();
        eventStore = spy(new InMemoryEventStore());
        eventStore.appendEvents("CountingAggregate", new SimpleDomainEventStream(
                new AggregateCreatedEvent(aggregateIdentifier)));
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

        new AnnotationCommandHandlerAdapter(new CounterCommandHandler(repository), commandBus).subscribe();
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_PessimisticLocking() {
        initializeRepository(LockingStrategy.PESSIMISTIC);
        EventListener el = new EventListener() {
            @Override
            public void handle(Event event) {
                if (event instanceof CounterChangedEvent) {
                    CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event;
                    if (counterChangedEvent.getCounter() == 1) {
                        commandBus.dispatch(new ChangeCounterCommand(counterChangedEvent.getAggregateIdentifier(),
                                                                     counterChangedEvent.getCounter() + 1),
                                            reportErrorCallback);
                        commandBus.dispatch(new ChangeCounterCommand(counterChangedEvent.getAggregateIdentifier(),
                                                                     counterChangedEvent.getCounter() + 2),
                                            reportErrorCallback);
                    }
                }
            }
        };
        eventBus.subscribe(el);

        commandBus.dispatch(new ChangeCounterCommand(aggregateIdentifier, 1), reportErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents("CountingAggregate", aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEvent next = storedEvents.next();
            if (next instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next;
                assertEquals(Long.valueOf(event.getCounter()), event.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).appendEvents(eq("CountingAggregate"), isA(DomainEventStream.class));
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_OptimisticLocking() throws Throwable {
        initializeRepository(LockingStrategy.OPTIMISTIC);
        EventListener el = new EventListener() {
            @Override
            public void handle(Event event) {
                if (event instanceof CounterChangedEvent) {
                    CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event;
                    if (counterChangedEvent.getCounter() == 1) {
                        commandBus.dispatch(new ChangeCounterCommand(counterChangedEvent.getAggregateIdentifier(),
                                                                     counterChangedEvent.getCounter() + 1),
                                            reportErrorCallback);
                        commandBus.dispatch(new ChangeCounterCommand(counterChangedEvent.getAggregateIdentifier(),
                                                                     counterChangedEvent.getCounter() + 2),
                                            reportErrorCallback);
                    }
                }
            }
        };
        eventBus.subscribe(el);

        commandBus.dispatch(new ChangeCounterCommand(aggregateIdentifier, 1), reportErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents("CountingAggregate", aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEvent next = storedEvents.next();
            if (next instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next;
                assertEquals(Long.valueOf(event.getCounter()), event.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).appendEvents(eq("CountingAggregate"), isA(DomainEventStream.class));
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_OptimisticLocking_ProcessingFails() throws Throwable {
        initializeRepository(LockingStrategy.OPTIMISTIC);
        EventListener el = new EventListener() {
            @Override
            public void handle(Event event) {
                if (event instanceof CounterChangedEvent) {
                    CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event;
                    if (counterChangedEvent.getCounter() == 1) {
                        commandBus.dispatch(new ChangeCounterCommand(counterChangedEvent.getAggregateIdentifier(),
                                                                     counterChangedEvent.getCounter() + 1),
                                            reportErrorCallback);
                        commandBus.dispatch(new ChangeCounterCommand(counterChangedEvent.getAggregateIdentifier(),
                                                                     counterChangedEvent.getCounter() + 2),
                                            reportErrorCallback);
                    } else if (counterChangedEvent.getCounter() == 2) {
                        throw new RuntimeException("Mock exception");
                    }
                }
            }
        };
        eventBus.subscribe(el);

        commandBus.dispatch(new ChangeCounterCommand(aggregateIdentifier, 1), expectErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents("CountingAggregate", aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEvent next = storedEvents.next();
            if (next instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next;
                assertEquals(Long.valueOf(event.getCounter()), event.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).appendEvents(eq("CountingAggregate"), isA(DomainEventStream.class));
    }

    @Test
    public void testLoopBackKeepsProperEventOrder_PessimisticLocking_ProcessingFails() throws Throwable {
        initializeRepository(LockingStrategy.PESSIMISTIC);
        EventListener el = new EventListener() {
            @Override
            public void handle(Event event) {
                if (event instanceof CounterChangedEvent) {
                    CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event;
                    if (counterChangedEvent.getCounter() == 1) {
                        commandBus.dispatch(new ChangeCounterCommand(counterChangedEvent.getAggregateIdentifier(),
                                                                     counterChangedEvent.getCounter() + 1),
                                            reportErrorCallback);
                        commandBus.dispatch(new ChangeCounterCommand(counterChangedEvent.getAggregateIdentifier(),
                                                                     counterChangedEvent.getCounter() + 2),
                                            reportErrorCallback);
                    } else if (counterChangedEvent.getCounter() == 2) {
                        throw new RuntimeException("Mock exception");
                    }
                }
            }
        };
        eventBus.subscribe(el);

        commandBus.dispatch(new ChangeCounterCommand(aggregateIdentifier, 1), expectErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents("CountingAggregate", aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEvent next = storedEvents.next();
            if (next instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next;
                assertEquals(Long.valueOf(event.getCounter()), event.getSequenceNumber());
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
        private AggregateIdentifier aggregateId;
        private int newValue;

        private ChangeCounterCommand(AggregateIdentifier aggregateId, int newValue) {
            this.aggregateId = aggregateId;
            this.newValue = newValue;
        }

        public AggregateIdentifier getAggregateId() {
            return aggregateId;
        }

        public int getNewValue() {
            return newValue;
        }
    }

    private static class AggregateCreatedEvent extends DomainEvent {

        private AggregateCreatedEvent(AggregateIdentifier aggregateIdentifier) {
            super(0, aggregateIdentifier);
        }
    }

    private static class CountingAggregate extends AbstractAnnotatedAggregateRoot {

        private static final long serialVersionUID = -2927751585905120260L;

        private int counter = 0;

        private CountingAggregate(AggregateIdentifier identifier) {
            super(identifier);
        }

        public void setCounter(int newValue) {
            apply(new CounterChangedEvent(newValue));
        }

        @EventHandler
        private void handleCounterIncreased(CounterChangedEvent event) {
            this.counter = event.getCounter();
        }
    }

    private static class CounterChangedEvent extends DomainEvent {
        private final int counter;

        private CounterChangedEvent(int counter) {
            this.counter = counter;
        }

        public int getCounter() {
            return counter;
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private Map<AggregateIdentifier, List<DomainEvent>> store = new HashMap<AggregateIdentifier, List<DomainEvent>>();

        @Override
        public void appendEvents(String identifier, DomainEventStream events) {
            while (events.hasNext()) {
                DomainEvent next = events.next();
                if (!store.containsKey(next.getAggregateIdentifier())) {
                    store.put(next.getAggregateIdentifier(), new ArrayList<DomainEvent>());
                }
                List<DomainEvent> eventList = store.get(next.getAggregateIdentifier());
                eventList.add(next);
            }
        }

        @Override
        public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
            List<DomainEvent> events = store.get(identifier);
            events = events == null ? new ArrayList<DomainEvent>() : events;
            return new SimpleDomainEventStream(events);
        }
    }
}
