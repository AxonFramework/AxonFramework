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
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandContext;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.Event;
import org.axonframework.domain.EventBase;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericEventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStoreException;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * A test fixture that allows the execution of given-when-then style test cases. For detailed usage information, see
 * {@link org.axonframework.test.FixtureConfiguration}.
 *
 * @author Allard Buijze
 * @since 0.6
 */
class GivenWhenThenTestFixture implements ResultValidator, FixtureConfiguration, TestExecutor {

    private EventSourcingRepository<?> repository;
    private CommandBus commandBus;
    private EventBus eventBus;
    private UUID aggregateIdentifier;
    private EventStore eventStore;

    private List<DomainEvent> givenEvents;
    private List<DomainEvent> storedEvents;
    private List<Event> publishedEvents;
    private Object actualReturnValue;
    private Throwable actualException;

    private long sequenceNumber = 0;

    private final Reporter reporter = new Reporter();

    /**
     * Initializes a new given-when-then style test fixture.
     */
    GivenWhenThenTestFixture() {
        aggregateIdentifier = UUID.randomUUID();
        eventBus = new RecordingEventBus();
        commandBus = new SimpleCommandBus();
        eventStore = new RecordingEventStore();
        clearGivenWhenState();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <T extends EventSourcedAggregateRoot> EventSourcingRepository<T> createGenericRepository(
            Class<T> aggregateClass) {
        registerRepository(new GenericEventSourcingRepository<T>(aggregateClass));
        return (EventSourcingRepository<T>) repository;
    }

    @Override
    public FixtureConfiguration registerRepository(EventSourcingRepository<?> eventSourcingRepository) {
        this.repository = eventSourcingRepository;
        eventSourcingRepository.setEventBus(eventBus);
        eventSourcingRepository.setEventStore(eventStore);
        return this;
    }

    @Override
    public FixtureConfiguration registerAnnotatedCommandHandler(Object annotatedCommandHandler) {
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

    @Override
    public TestExecutor given(DomainEvent... domainEvents) {
        return given(Arrays.asList(domainEvents));
    }

    @Override
    public TestExecutor given(List<DomainEvent> domainEvents) {
        clearGivenWhenState();
        for (DomainEvent event : domainEvents) {
            setByReflection(DomainEvent.class, "aggregateIdentifier", event, aggregateIdentifier);
            setByReflection(DomainEvent.class, "sequenceNumber", event, sequenceNumber++);
        }
        this.givenEvents.addAll(domainEvents);
        return this;
    }

    @Override
    public ResultValidator when(Object command) {
        commandBus.dispatch(command, new CommandCallback<Object, Object>() {
            @Override
            public void onSuccess(Object result, CommandContext context) {
                actualReturnValue = result;
            }

            @Override
            public void onFailure(Throwable cause, CommandContext context) {
                actualException = cause;
            }
        });
        return this;
    }

    @Override
    public ResultValidator expectEvents(DomainEvent... expectedEvents) {
        if (publishedEvents.size() != storedEvents.size()) {
            reporter.reportDifferenceInStoredVsPublished(storedEvents, publishedEvents);
        }

        return expectPublishedEvents(expectedEvents);
    }

    @Override
    public ResultValidator expectPublishedEvents(Event... expectedEvents) {
        if (expectedEvents.length != publishedEvents.size()) {
            reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
        }

        Iterator<Event> iterator = publishedEvents.iterator();
        for (Event expectedEvent : expectedEvents) {
            Event actualEvent = iterator.next();
            if (!verifyEventEquality(expectedEvent, actualEvent)) {
                reporter.reportWrongEvent(publishedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @Override
    public ResultValidator expectStoredEvents(DomainEvent... expectedEvents) {
        if (expectedEvents.length != storedEvents.size()) {
            reporter.reportWrongEvent(storedEvents, Arrays.asList(expectedEvents), actualException);
        }
        Iterator<DomainEvent> iterator = storedEvents.iterator();
        for (DomainEvent expectedEvent : expectedEvents) {
            DomainEvent actualEvent = iterator.next();
            if (!verifyEventEquality(expectedEvent, actualEvent)) {
                reporter.reportWrongEvent(storedEvents, Arrays.asList(expectedEvents), actualException);
            }
        }
        return this;
    }

    @Override
    public ResultValidator expectVoidReturnType() {
        return expectReturnValue(Void.TYPE);
    }

    @Override
    public ResultValidator expectReturnValue(Object expectedReturnValue) {
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedReturnValue);
        }
        if ((expectedReturnValue != null && !expectedReturnValue.equals(actualReturnValue) ||
                expectedReturnValue == null && actualReturnValue != null)) {
            reporter.reportWrongResult(actualReturnValue, expectedReturnValue);
        }
        return this;
    }

    @Override
    public ResultValidator expectException(Class<? extends Throwable> expectedException) {
        if (actualReturnValue != null) {
            reporter.reportUnexpectedReturnValue(actualReturnValue, expectedException);
        }
        if (!expectedException.equals(actualException.getClass())) {
            reporter.reportWrongException(actualException, expectedException);
        }
        return this;
    }

    private void clearGivenWhenState() {
        storedEvents = new ArrayList<DomainEvent>();
        publishedEvents = new ArrayList<Event>();
        givenEvents = new ArrayList<DomainEvent>();
        sequenceNumber = 0;
        actualReturnValue = null;
        actualException = null;
    }

    @Override
    public UUID getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public CommandBus getCommandBus() {
        return commandBus;
    }

    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public EventStore getEventStore() {
        return eventStore;
    }

    @Override
    public EventSourcingRepository<?> getRepository() {
        return repository;
    }

    private boolean verifyEventEquality(Event expectedEvent, Event actualEvent) {
        if (!expectedEvent.getClass().equals(actualEvent.getClass())) {
            return false;
        }
        verifyEqualFields(expectedEvent.getClass(), expectedEvent, actualEvent);
        return true;
    }

    private void setByReflection(Class<?> eventClass, String fieldName, DomainEvent event, Serializable value) {
        try {
            Field field = eventClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(event, value);
        }
        catch (Exception e) {
            throw new FixtureExecutionException("This test fixture needs to be able to set fields by reflection", e);
        }
    }

    private void verifyEqualFields(Class<?> aClass, Event expectedEvent, Event actualEvent) {
        for (Field field : aClass.getDeclaredFields()) {
            field.setAccessible(true);
            try {

                Object expected = field.get(expectedEvent);
                Object actual = field.get(actualEvent);
                if ((expected != null && !expected.equals(actual)) || expected == null && actual != null) {
                    reporter.reportDifferentEventContents(expectedEvent.getClass(), field, actual, expected);
                }
            } catch (IllegalAccessException e) {
                throw new FixtureExecutionException("Could not confirm event equality due to an exception", e);
            }
        }
        if (aClass.getSuperclass() != DomainEvent.class
                && aClass.getSuperclass() != EventBase.class
                && aClass.getSuperclass() != Object.class) {
            verifyEqualFields(aClass.getSuperclass(), expectedEvent, actualEvent);
        }
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
                throw new EventStoreException("You probably want to use aggregateIdentifier() on your fixture "
                        + "to get the aggregate identifier to load");
            }
            return new SimpleDomainEventStream(givenEvents);
        }
    }

    private class RecordingEventBus implements EventBus {

        @Override
        public void publish(Event event) {
            publishedEvents.add(event);
        }

        @Override
        public void subscribe(EventListener eventListener) {
        }

        @Override
        public void unsubscribe(EventListener eventListener) {
        }
    }
}
