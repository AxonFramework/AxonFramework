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

package org.axonframework.test;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.Event;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericEventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.monitoring.jmx.JmxConfiguration;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;
import static org.axonframework.util.ReflectionUtils.*;

/**
 * A test fixture that allows the execution of given-when-then style test cases. For detailed usage information, see
 * {@link org.axonframework.test.FixtureConfiguration}.
 *
 * @author Allard Buijze
 * @since 0.6
 */
class GivenWhenThenTestFixture implements FixtureConfiguration, TestExecutor {

    private static final Logger logger = LoggerFactory.getLogger(GivenWhenThenTestFixture.class);

    private EventSourcingRepository<?> repository;
    private SimpleCommandBus commandBus;
    private EventBus eventBus;
    private AggregateIdentifier aggregateIdentifier;
    private EventStore eventStore;

    private Collection<DomainEvent> givenEvents;

    private Deque<DomainEvent> storedEvents;
    private List<Event> publishedEvents;
    private long sequenceNumber = 0;
    private AggregateRoot workingAggregate;
    private boolean reportIllegalStateChange = true;

    /**
     * Initializes a new given-when-then style test fixture.
     */
    GivenWhenThenTestFixture() {
        JmxConfiguration.getInstance().disableMonitoring();
        aggregateIdentifier = new UUIDAggregateIdentifier();
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
    public TestExecutor given(DomainEventStream domainEvents) {
        List<DomainEvent> eventList = new ArrayList<DomainEvent>();
        while (domainEvents.hasNext()) {
            eventList.add(domainEvents.next());
        }
        return given(eventList);
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
    public TestExecutor givenCommands(Object... commands) {
        return givenCommands(Arrays.asList(commands));
    }

    @Override
    public TestExecutor givenCommands(List<?> commands) {
        clearGivenWhenState();
        for (Object command : commands) {
            commandBus.dispatch(command);
            givenEvents.addAll(storedEvents);
            storedEvents.clear();
        }
        publishedEvents.clear();
        return this;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public ResultValidator when(Object command) {
        ResultValidatorImpl resultValidator = new ResultValidatorImpl(storedEvents, publishedEvents);
        commandBus.setInterceptors(Arrays.asList(new AggregateRegisteringInterceptor()));
        commandBus.dispatch(command, resultValidator);
        detectIllegalStateChanges();
        return resultValidator;
    }

    private void detectIllegalStateChanges() {
        if (workingAggregate != null && reportIllegalStateChange) {
            repository.setEventStore(new EventStore() {
                @Override
                public void appendEvents(String type, DomainEventStream events) {
                }

                @Override
                public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
                    List<DomainEvent> eventsToStream = new ArrayList<DomainEvent>(givenEvents);
                    eventsToStream.addAll(storedEvents);
                    return new SimpleDomainEventStream(eventsToStream);
                }
            });
            UnitOfWork uow = DefaultUnitOfWork.startAndGet();
            try {
                EventSourcedAggregateRoot aggregate2 = repository.load(aggregateIdentifier);
                if (workingAggregate.isDeleted()) {
                    throw new AxonAssertionError("The working aggregate was considered deleted, "
                                                         + "but the Repository still contains a non-deleted copy of "
                                                         + "the aggregate. Make sure one of the Aggregate's Events "
                                                         + "contains an AggregateDeletedEvent, or the aggregate "
                                                         + "explicitly marks itself as deleted in an EventHandler.");
                }
                assertValidWorkingAggregateState(aggregate2);
            } catch (AggregateNotFoundException notFound) {
                if (!workingAggregate.isDeleted()) {
                    throw new AxonAssertionError("The working aggregate was not considered deleted, "
                                                         + "but the Repository cannot recover the state of the "
                                                         + "aggregate, as it is considered deleted there.");
                }
            } finally {
                // rollback to prevent changes bing pushed to event store
                uow.rollback();

                // return to regular event store, just in case
                repository.setEventStore(eventStore);
            }
        }
    }

    private void assertValidWorkingAggregateState(EventSourcedAggregateRoot eventSourcedAggregate) {
        HashSet<ComparationEntry> comparedEntries = new HashSet<ComparationEntry>();
        if (!workingAggregate.getClass().equals(eventSourcedAggregate.getClass())) {
            throw new AxonAssertionError(String.format("The aggregate loaded based on the generated events seems to "
                                                               + "be of another type than the original.\n"
                                                               + "Working type: <%s>\nEvent Sourced type: <%s>",
                                                       workingAggregate.getClass().getName(),
                                                       eventSourcedAggregate.getClass().getName()));
        }
        ensureValuesEqual(workingAggregate,
                          eventSourcedAggregate,
                          eventSourcedAggregate.getClass().getName(),
                          comparedEntries);
    }

    private void ensureValuesEqual(Object workingValue, Object eventSourcedValue, String propertyPath,
                                   Set<ComparationEntry> comparedEntries) {
        if (explicitlyUnequal(workingValue, eventSourcedValue)) {
            throw new AxonAssertionError(format("Illegal state change detected! "
                                                        + "Property \"%s\" has different value when sourcing events.\n"
                                                        + "Working aggregate value:     <%s>\n"
                                                        + "Value after applying events: <%s>",
                                                propertyPath, workingValue, eventSourcedValue));
        } else if (workingValue != null && comparedEntries.add(new ComparationEntry(workingValue, eventSourcedValue))
                && !hasEqualsMethod(workingValue.getClass())) {
            for (Field field : fieldsOf(workingValue.getClass())) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                    ensureAccessible(field);
                    String newPropertyPath = propertyPath + "." + field.getName();
                    try {
                        Object workingFieldValue = field.get(workingValue);
                        Object eventSourcedFieldValue = field.get(eventSourcedValue);
                        ensureValuesEqual(workingFieldValue, eventSourcedFieldValue, newPropertyPath, comparedEntries);
                    } catch (IllegalAccessException e) {
                        logger.warn("Could not access field \"{}\". Unable to detect inappropriate state changes.",
                                    newPropertyPath);
                    }
                }
            }
        }
    }

    private void clearGivenWhenState() {
        storedEvents = new LinkedList<DomainEvent>();
        publishedEvents = new ArrayList<Event>();
        givenEvents = new ArrayList<DomainEvent>();
        sequenceNumber = 0;
    }

    @Override
    public AggregateIdentifier getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public void setAggregateIdentifier(AggregateIdentifier aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier;
    }

    @Override
    public void setReportIllegalStateChange(boolean reportIllegalStateChange) {
        this.reportIllegalStateChange = reportIllegalStateChange;
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

    private void setByReflection(Class<?> eventClass, String fieldName, DomainEvent event, Serializable value) {
        try {
            Field field = eventClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(event, value);
        } catch (Exception e) {
            throw new FixtureExecutionException("This test fixture needs to be able to set fields by reflection", e);
        }
    }

    private class RecordingEventStore implements EventStore {

        @Override
        public void appendEvents(String type, DomainEventStream events) {
            while (events.hasNext()) {
                DomainEvent next = events.next();
                if (!storedEvents.isEmpty()) {
                    DomainEvent lastEvent = storedEvents.peekLast();
                    if (!lastEvent.getAggregateIdentifier().equals(next.getAggregateIdentifier())) {
                        throw new EventStoreException("Writing events for an unexpected aggregate. This could "
                                                              + "indicate that a wrong aggregate is being triggered.");
                    } else if (lastEvent.getSequenceNumber() != next.getSequenceNumber() - 1) {
                        throw new EventStoreException(format("Unexpected sequence number on stored event. "
                                                                     + "Expected %s, but got %s.",
                                                             lastEvent.getSequenceNumber() + 1,
                                                             next.getSequenceNumber()));
                    }
                }
                storedEvents.add(next);
            }
        }

        @Override
        public DomainEventStream readEvents(String type, AggregateIdentifier identifier) {
            if (!aggregateIdentifier.equals(identifier)) {
                throw new EventStoreException("You probably want to use aggregateIdentifier() on your fixture "
                                                      + "to get the aggregate identifier to use");
            }
            if (givenEvents.isEmpty()) {
                throw new AggregateNotFoundException(identifier,
                                                     "No 'given' events were configured for this aggregate.");
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

    private class AggregateRegisteringInterceptor implements CommandHandlerInterceptor {

        @Override
        public Object handle(Object command, UnitOfWork unitOfWork,
                             InterceptorChain interceptorChain)
                throws Throwable {
            unitOfWork.registerListener(new UnitOfWorkListenerAdapter() {
                @Override
                public void onPrepareCommit(Set<AggregateRoot> aggregateRoots, List<Event> events) {
                    Iterator<AggregateRoot> iterator = aggregateRoots.iterator();
                    if (iterator.hasNext()) {
                        workingAggregate = iterator.next();
                        if (workingAggregate.getVersion() == null) {
                            // to cope with aggregates generating their own identifiers
                            aggregateIdentifier = workingAggregate.getIdentifier();
                        }
                    }
                }
            });
            return interceptorChain.proceed();
        }
    }

    private static class ComparationEntry {

        private final Object workingObject;
        private final Object eventSourceObject;

        public ComparationEntry(Object workingObject, Object eventSourceObject) {
            this.workingObject = workingObject;
            this.eventSourceObject = eventSourceObject;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ComparationEntry that = (ComparationEntry) o;

            if (!eventSourceObject.equals(that.eventSourceObject)) {
                return false;
            }
            if (!workingObject.equals(that.workingObject)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = workingObject.hashCode();
            result = 31 * result + eventSourceObject.hashCode();
            return result;
        }
    }
}
