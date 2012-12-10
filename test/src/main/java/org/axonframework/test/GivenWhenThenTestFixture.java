/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AggregateAnnotationCommandHandler;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.common.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.Message;
import org.axonframework.domain.MetaData;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStoreException;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static org.axonframework.common.IdentifierValidator.validateIdentifier;
import static org.axonframework.common.ReflectionUtils.*;

/**
 * A test fixture that allows the execution of given-when-then style test cases. For detailed usage information, see
 * {@link org.axonframework.test.FixtureConfiguration}.
 *
 * @param <T> The type of Aggregate tested in this Fixture
 * @author Allard Buijze
 * @since 0.6
 */
public class GivenWhenThenTestFixture<T extends EventSourcedAggregateRoot>
        implements FixtureConfiguration<T>, TestExecutor {

    private static final Logger logger = LoggerFactory.getLogger(GivenWhenThenTestFixture.class);

    private Repository<T> repository;
    private SimpleCommandBus commandBus;
    private EventBus eventBus;
    private Object aggregateIdentifier;

    private EventStore eventStore;

    private Collection<DomainEventMessage> givenEvents;
    private Deque<DomainEventMessage> storedEvents;
    private List<EventMessage> publishedEvents;
    private long sequenceNumber = 0;
    private AggregateRoot workingAggregate;
    private boolean reportIllegalStateChange = true;
    private final Class<T> aggregateType;
    private boolean explicitCommandHandlersSet;
    private final List<Object> injectableResources = new ArrayList<Object>();

    /**
     * Initializes a new given-when-then style test fixture for the given <code>aggregateType</code>.
     *
     * @param aggregateType The aggregate to initialize the test fixture for
     */
    public GivenWhenThenTestFixture(Class<T> aggregateType) {
        eventBus = new RecordingEventBus();
        commandBus = new SimpleCommandBus();
        eventStore = new RecordingEventStore();
        clearGivenWhenState();
        final EventSourcingRepository<T> eventSourcingRepository = new EventSourcingRepository<T>(aggregateType);
        eventSourcingRepository.setEventStore(eventStore);
        eventSourcingRepository.setEventBus(eventBus);
        repository = new IdentifierValidatingRepository<T>(eventSourcingRepository);
        this.aggregateType = aggregateType;
    }

    @Override
    public FixtureConfiguration<T> registerRepository(EventSourcingRepository<T> eventSourcingRepository) {
        this.repository = new IdentifierValidatingRepository<T>(eventSourcingRepository);
        eventSourcingRepository.setEventBus(eventBus);
        eventSourcingRepository.setEventStore(eventStore);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerAggregateFactory(AggregateFactory<T> aggregateFactory) {
        return registerRepository(new EventSourcingRepository<T>(aggregateFactory));
    }

    @Override
    public FixtureConfiguration<T> registerAnnotatedCommandHandler(Object annotatedCommandHandler) {
        explicitCommandHandlersSet = true;
        registerInjectableResources();
        AnnotationCommandHandlerAdapter.subscribe(annotatedCommandHandler, commandBus);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerCommandHandler(Class<?> payloadType, CommandHandler commandHandler) {
        return registerCommandHandler(payloadType.getName(), commandHandler);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public FixtureConfiguration<T> registerCommandHandler(String commandName, CommandHandler commandHandler) {
        explicitCommandHandlersSet = true;
        registerInjectableResources();
        commandBus.subscribe(commandName, commandHandler);
        return this;
    }

    private void registerInjectableResources() {
        injectableResources.add(commandBus);
        injectableResources.add(eventBus);
        SimpleResourceParameterResolverFactory.register(injectableResources);
    }

    @Override
    public FixtureConfiguration<T> registerInjectableResource(Object resource) {
        if (explicitCommandHandlersSet) {
            throw new FixtureExecutionException("Cannot inject resources after command handler has been created. "
                                                        + "Configure all resource before calling "
                                                        + "registerCommandHandler() or "
                                                        + "registerAnnotatedCommandHandler()");
        }
        injectableResources.add(resource);
        return this;
    }

    @Override
    public TestExecutor given(Object... domainEvents) {
        return given(Arrays.asList(domainEvents));
    }

    @Override
    public TestExecutor given(List<?> domainEvents) {
        clearGivenWhenState();
        for (Object event : domainEvents) {
            Object payload = event;
            MetaData metaData = null;
            if (event instanceof Message) {
                payload = ((Message) event).getPayload();
                metaData = ((Message) event).getMetaData();
            }
            this.givenEvents.add(new GenericDomainEventMessage<Object>(aggregateIdentifier, sequenceNumber++,
                                                                       payload, metaData));
        }
        return this;
    }

    @Override
    public TestExecutor givenCommands(Object... commands) {
        return givenCommands(Arrays.asList(commands));
    }

    @Override
    public TestExecutor givenCommands(List<?> commands) {
        finalizeConfiguration();
        clearGivenWhenState();
        for (Object command : commands) {
            commandBus.dispatch(GenericCommandMessage.asCommandMessage(command));
            givenEvents.addAll(storedEvents);
            storedEvents.clear();
        }
        publishedEvents.clear();
        return this;
    }

    @Override
    public ResultValidator when(Object command) {
        return when(command, MetaData.emptyInstance());
    }

    @Override
    public ResultValidator when(Object command, Map<String, ?> metaData) {
        finalizeConfiguration();
        ResultValidatorImpl resultValidator = new ResultValidatorImpl(storedEvents, publishedEvents);
        commandBus.setHandlerInterceptors(Collections.singletonList(new AggregateRegisteringInterceptor()));

        commandBus.dispatch(GenericCommandMessage.asCommandMessage(command).andMetaData(metaData), resultValidator);

        detectIllegalStateChanges();
        resultValidator.assertValidRecording();
        return resultValidator;
    }

    private void finalizeConfiguration() {
        registerInjectableResources();
        if (!explicitCommandHandlersSet) {
            new AggregateAnnotationCommandHandler<T>(this.aggregateType, repository, commandBus).subscribe();
        }
        explicitCommandHandlersSet = true;
    }

    private void detectIllegalStateChanges() {
        if (aggregateIdentifier != null && workingAggregate != null && reportIllegalStateChange) {
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
                    throw new AxonAssertionError("The working aggregate was not considered deleted, " //NOSONAR
                                                         + "but the Repository cannot recover the state of the "
                                                         + "aggregate, as it is considered deleted there.");
                }
            } catch (RuntimeException e) {
                logger.warn("An Exception occurred while detecting illegal state changes in {}.",
                            workingAggregate.getClass().getName(),
                            e);
            } finally {
                // rollback to prevent changes bing pushed to event store
                uow.rollback();
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
        storedEvents = new LinkedList<DomainEventMessage>();
        publishedEvents = new ArrayList<EventMessage>();
        givenEvents = new ArrayList<DomainEventMessage>();
        sequenceNumber = 0;
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
    public Repository<T> getRepository() {
        return repository;
    }

    private class RecordingEventStore implements EventStore {

        @Override
        public void appendEvents(String type, DomainEventStream events) {
            while (events.hasNext()) {
                DomainEventMessage next = events.next();
                validateIdentifier(next.getAggregateIdentifier().getClass());
                if (!storedEvents.isEmpty()) {
                    DomainEventMessage lastEvent = storedEvents.peekLast();
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
                if (aggregateIdentifier == null) {
                    aggregateIdentifier = next.getAggregateIdentifier();
                    injectAggregateIdentifier();
                }
                storedEvents.add(next);
            }
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier) {
            if (identifier != null) {
                validateIdentifier(identifier.getClass());
            }
            if (aggregateIdentifier != null && !aggregateIdentifier.equals(identifier)) {
                throw new EventStoreException("You probably want to use aggregateIdentifier() on your fixture "
                                                      + "to get the aggregate identifier to use");
            } else if (aggregateIdentifier == null) {
                aggregateIdentifier = identifier;
                injectAggregateIdentifier();
            }
            List<DomainEventMessage> allEvents = new ArrayList<DomainEventMessage>(givenEvents);
            allEvents.addAll(storedEvents);
            if (allEvents.isEmpty()) {
                throw new AggregateNotFoundException(identifier,
                                                     "No 'given' events were configured for this aggregate, "
                                                             + "nor have any events been stored.");
            }
            return new SimpleDomainEventStream(allEvents);
        }

        private void injectAggregateIdentifier() {
            List<DomainEventMessage> oldEvents = new ArrayList<DomainEventMessage>(givenEvents);
            givenEvents.clear();
            for (DomainEventMessage oldEvent : oldEvents) {
                if (oldEvent.getAggregateIdentifier() == null) {
                    givenEvents.add(new GenericDomainEventMessage<Object>(oldEvent.getIdentifier(),
                                                                          oldEvent.getTimestamp(),
                                                                          aggregateIdentifier,
                                                                          oldEvent.getSequenceNumber(),
                                                                          oldEvent.getPayload(),
                                                                          oldEvent.getMetaData()));
                } else {
                    givenEvents.add(oldEvent);
                }
            }
        }
    }

    private class RecordingEventBus implements EventBus {

        @Override
        public void publish(EventMessage... events) {
            publishedEvents.addAll(Arrays.asList(events));
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
        public Object handle(CommandMessage<?> commandMessage, UnitOfWork unitOfWork,
                             InterceptorChain interceptorChain)
                throws Throwable {
            unitOfWork.registerListener(new UnitOfWorkListenerAdapter() {
                @Override
                public void onPrepareCommit(UnitOfWork unitOfWork, Set<AggregateRoot> aggregateRoots,
                                            List<EventMessage> events) {
                    Iterator<AggregateRoot> iterator = aggregateRoots.iterator();
                    if (iterator.hasNext()) {
                        workingAggregate = iterator.next();
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

        @SuppressWarnings("RedundantIfStatement")
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

    private static class IdentifierValidatingRepository<T extends AggregateRoot> implements Repository<T> {

        private final Repository<T> delegate;

        public IdentifierValidatingRepository(Repository<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T load(Object aggregateIdentifier, Long expectedVersion) {
            T aggregate = delegate.load(aggregateIdentifier, expectedVersion);
            validateIdentifier(aggregateIdentifier, aggregate);
            return aggregate;
        }

        @Override
        public T load(Object aggregateIdentifier) {
            T aggregate = delegate.load(aggregateIdentifier, null);
            validateIdentifier(aggregateIdentifier, aggregate);
            return aggregate;
        }

        private void validateIdentifier(Object aggregateIdentifier, T aggregate) {
            if (aggregateIdentifier != null && !aggregateIdentifier.equals(aggregate.getIdentifier())) {
                throw new AssertionError(String.format(
                        "The aggregate used in this fixture was initialized with an identifier different than "
                                + "the one used to load it. Loaded [%s], but actual identifier is [%s].\n"
                                + "Make sure the identifier passed in the Command matches that of the given Events.",
                        aggregateIdentifier, aggregate.getIdentifier()));
            }
        }

        @Override
        public void add(T aggregate) {
            delegate.add(aggregate);
        }
    }
}
