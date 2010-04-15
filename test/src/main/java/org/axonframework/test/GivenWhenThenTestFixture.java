/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.test;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.Event;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericEventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.AggregateNotFoundException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
class GivenWhenThenTestFixture implements ResultValidator, FixtureConfiguration, TestExecutor {

    private EventSourcingRepository<?> repository;
    private CommandBus commandBus;
    private EventBus eventBus;
    private UUID aggregateIdentifier;
    private List<DomainEvent> givenEvents;
    private EventStore eventStore;

    private List<DomainEvent> storedEvents;
    private List<DomainEvent> publishedEvents;
    private Object actualReturnValue;
    private Throwable actualException;

    private long sequenceNumber = 0;

    public GivenWhenThenTestFixture() {
        aggregateIdentifier = UUID.randomUUID();
        eventBus = new RecordingEventBus();
        commandBus = new SimpleCommandBus();
        eventStore = new RecordingEventStore();
        storedEvents = new ArrayList<DomainEvent>();
        publishedEvents = new ArrayList<DomainEvent>();
        givenEvents = new ArrayList<DomainEvent>();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public FixtureConfiguration registerGenericRepository(Class<?> aggregateClass) {
        registerRepository(new GenericEventSourcingRepository(aggregateClass));
        return this;
    }

    @Override
    public FixtureConfiguration registerRepository(EventSourcingRepository<?> repository) {
        this.repository = repository;
        repository.setEventBus(eventBus);
        repository.setEventStore(eventStore);
        return this;
    }

    @Override
    public GivenWhenThenTestFixture registerAnnotatedCommandHandler(Object annotatedCommandHandler) {
        AnnotationCommandHandlerAdapter commandHandlerAdapter = new AnnotationCommandHandlerAdapter(
                annotatedCommandHandler,
                commandBus);
        commandHandlerAdapter.subscribe();
        return this;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public FixtureConfiguration registerCommandHandler(Class<?> commandType, CommandHandler commandHandler) {
        commandBus.subscribe(commandType, commandHandler);
        return this;
    }

    public TestExecutor given(DomainEvent... domainEvents) {
        for (DomainEvent event : domainEvents) {
            setByReflection(DomainEvent.class, "aggregateIdentifier", event, aggregateIdentifier);
            setByReflection(DomainEvent.class, "sequenceNumber", event, sequenceNumber++);
        }
        this.givenEvents = Arrays.asList(domainEvents);
        return this;
    }

    @Override
    public ResultValidator when(Object command) {
        try {
            actualReturnValue = commandBus.dispatch(command);
        }
        catch (Exception ex) {
            actualException = ex;
        }
        return this;
    }

    @Override
    public ResultValidator expectEvents(DomainEvent... expectedEvents) {
        assertEquals(publishedEvents.size(), storedEvents.size());
        assertEquals("Wrong number of events. ", expectedEvents.length, publishedEvents.size());
        Iterator<DomainEvent> iterator = publishedEvents.iterator();
        for (DomainEvent expectedEvent : expectedEvents) {
            Event actualEvent = iterator.next();
            verifyEventEquality(expectedEvent, actualEvent);
        }
        return this;
    }

    @Override
    public ResultValidator expectReturnValue(Object expectedReturnValue) {
        if (actualException != null) {
            actualException.printStackTrace();
            fail(String.format("Expected return value (a %s), but got exception: %s",
                               expectedReturnValue.getClass().getSimpleName(),
                               actualException));
        }
        assertEquals("Return value of command handler not as", expectedReturnValue, actualReturnValue);
        return this;
    }

    @Override
    public ResultValidator expectException(Class<? extends Throwable> expectedException) {
        if (actualReturnValue != null) {
            fail(String.format("Expected exception (%s), but got return value: %s",
                               expectedException.getSimpleName(),
                               actualReturnValue));
        }
        assertEquals(expectedException, actualException.getClass());
        return this;
    }

    UUID getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    private void verifyEventEquality(DomainEvent expectedEvent, Event actualEvent) {
        assertEquals("Event of wrong type. ", expectedEvent.getClass().getName(), actualEvent.getClass().getName());
        verifyEqualFields(expectedEvent.getClass(), expectedEvent, actualEvent);
    }

    private void setByReflection(Class<?> eventClass, String fieldName, DomainEvent event, Object value
    ) {
        try {
            Field field = eventClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(event, value);
        }
        catch (Exception e) {
            throw new IllegalStateException("Test fixture cannot do its work", e);
        }
    }

    private void verifyEqualFields(Class<?> aClass, DomainEvent expectedEvent, Event actualEvent) {
        for (Field field : aClass.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                assertEquals(String.format("Field [%s] in event of type [%s],",
                                           aClass.getSimpleName() + "." + field.getName(),
                                           expectedEvent.getClass().getSimpleName()),
                             field.get(expectedEvent),
                             field.get(actualEvent));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        if (aClass.getSuperclass() != DomainEvent.class) {
            verifyEqualFields(aClass.getSuperclass(), expectedEvent, actualEvent);
        }
    }

    public EventSourcingRepository<?> getRepository() {
        return repository;
    }

    private class RecordingEventStore implements EventStore {

        @Override
        public void appendEvents(String type, DomainEventStream events) {
            while (events.hasNext()) {
                DomainEvent next = events.next();
                storedEvents.add(next);
            }
        }

        @Override
        public DomainEventStream readEvents(String type, UUID identifier) {
            if (!aggregateIdentifier.equals(identifier)) {
                throw new AggregateNotFoundException("You probably want to use Fixtures.aggregateIdentifier() "
                        + "to get the aggregate identifier to load");
            }
            return new SimpleDomainEventStream(givenEvents);
        }
    }

    private class RecordingEventBus implements EventBus {

        @Override
        public void publish(Event event) {
            publishedEvents.add((DomainEvent) event);
        }

        @Override
        public void subscribe(EventListener eventListener) {
        }

        @Override
        public void unsubscribe(EventListener eventListener) {
        }
    }
}
