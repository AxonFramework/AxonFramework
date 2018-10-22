/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.Assert;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.Registration;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.ConflictingAggregateVersionException;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.FixtureExecutionException;
import org.axonframework.test.deadline.StubDeadlineManager;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.IgnoreField;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.*;

/**
 * A test fixture that allows the execution of given-when-then style test cases. For detailed usage information, see
 * {@link FixtureConfiguration}.
 *
 * @param <T> The type of Aggregate tested in this Fixture
 * @author Allard Buijze
 * @since 0.6
 */
public class AggregateTestFixture<T> implements FixtureConfiguration<T>, TestExecutor<T> {

    private static final Logger logger = LoggerFactory.getLogger(AggregateTestFixture.class);
    private final Class<T> aggregateType;
    private final SimpleCommandBus commandBus;
    private final List<MessageDispatchInterceptor<CommandMessage<?>>> commandDispatchInterceptors = new ArrayList<>();
    private final List<MessageHandlerInterceptor<CommandMessage<?>>> commandHandlerInterceptors = new ArrayList<>();
    private final EventStore eventStore;
    private final List<FieldFilter> fieldFilters = new ArrayList<>();
    private final List<Object> resources = new ArrayList<>();
    private RepositoryProvider repositoryProvider;
    private IdentifierValidatingRepository<T> repository;
    private StubDeadlineManager deadlineManager;
    private String aggregateIdentifier;
    private Deque<DomainEventMessage<?>> givenEvents;
    private Deque<DomainEventMessage<?>> storedEvents;
    private List<EventMessage<?>> publishedEvents;
    private long sequenceNumber = 0;
    private boolean reportIllegalStateChange = true;
    private boolean explicitCommandHandlersSet;
    private MultiParameterResolverFactory parameterResolverFactory;
    private HandlerDefinition handlerDefinition;

    /**
     * Initializes a new given-when-then style test fixture for the given {@code aggregateType}.
     *
     * @param aggregateType The aggregate to initialize the test fixture for
     */
    public AggregateTestFixture(Class<T> aggregateType) {
        deadlineManager = new StubDeadlineManager();
        commandBus = SimpleCommandBus.builder().build();
        eventStore = new RecordingEventStore();
        resources.add(commandBus);
        resources.add(eventStore);
        resources.add(deadlineManager);
        this.aggregateType = aggregateType;
        clearGivenWhenState();
        parameterResolverFactory = MultiParameterResolverFactory.ordered(
                new SimpleResourceParameterResolverFactory(resources),
                ClasspathParameterResolverFactory.forClass(aggregateType));
        handlerDefinition = ClasspathHandlerDefinition.forClass(aggregateType);
    }

    @Override
    public FixtureConfiguration<T> registerRepository(Repository<T> repository) {
        this.repository = new IdentifierValidatingRepository<>(repository);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerRepositoryProvider(RepositoryProvider repositoryProvider) {
        this.repositoryProvider = repositoryProvider;
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerAggregateFactory(AggregateFactory<T> aggregateFactory) {
        MultiParameterResolverFactory parameterResolverFactory = MultiParameterResolverFactory.ordered(
                new SimpleResourceParameterResolverFactory(resources),
                ClasspathParameterResolverFactory.forClass(aggregateType)
        );

        return registerRepository(EventSourcingRepository.builder(aggregateFactory.getAggregateType())
                                                         .aggregateFactory(aggregateFactory)
                                                         .eventStore(eventStore)
                                                         .parameterResolverFactory(parameterResolverFactory)
                                                         .handlerDefinition(handlerDefinition)
                                                         .repositoryProvider(getRepositoryProvider())
                                                         .build());
    }

    @Override
    public synchronized FixtureConfiguration<T> registerAnnotatedCommandHandler(final Object annotatedCommandHandler) {
        registerAggregateCommandHandlers();
        explicitCommandHandlersSet = true;
        AnnotationCommandHandlerAdapter adapter = new AnnotationCommandHandlerAdapter<>(
                annotatedCommandHandler, parameterResolverFactory, handlerDefinition);
        adapter.subscribe(commandBus);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerCommandHandler(Class<?> payloadType,
                                                          MessageHandler<CommandMessage<?>> commandHandler) {
        return registerCommandHandler(payloadType.getName(), commandHandler);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public FixtureConfiguration<T> registerCommandHandler(String commandName,
                                                          MessageHandler<CommandMessage<?>> commandHandler) {
        registerAggregateCommandHandlers();
        explicitCommandHandlersSet = true;
        commandBus.subscribe(commandName, commandHandler);
        return this;
    }


    @Override
    public FixtureConfiguration<T> registerInjectableResource(Object resource) {
        if (explicitCommandHandlersSet) {
            throw new FixtureExecutionException("Cannot inject resources after command handler has been created. " +
                                                        "Configure all resource before calling " +
                                                        "registerCommandHandler() or " +
                                                        "registerAnnotatedCommandHandler()");
        }
        resources.add(resource);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerCommandDispatchInterceptor(
            MessageDispatchInterceptor<CommandMessage<?>> commandDispatchInterceptor) {
        commandDispatchInterceptors.add(commandDispatchInterceptor);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerCommandHandlerInterceptor(
            MessageHandlerInterceptor<CommandMessage<?>> commandHandlerInterceptor) {
        commandHandlerInterceptors.add(commandHandlerInterceptor);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerDeadlineDispatchInterceptor(
            MessageDispatchInterceptor<DeadlineMessage<?>> deadlineDispatchInterceptor) {
        deadlineManager.registerDispatchInterceptor(deadlineDispatchInterceptor);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerDeadlineHandlerInterceptor(
            MessageHandlerInterceptor<DeadlineMessage<?>> deadlineHandlerInterceptor) {
        deadlineManager.registerHandlerInterceptor(deadlineHandlerInterceptor);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerFieldFilter(FieldFilter fieldFilter) {
        this.fieldFilters.add(fieldFilter);
        return this;
    }

    @Override
    public FixtureConfiguration<T> registerIgnoredField(Class<?> declaringClass, String fieldName) {
        return registerFieldFilter(new IgnoreField(declaringClass, fieldName));
    }

    @Override
    public FixtureConfiguration<T> registerHandlerDefinition(HandlerDefinition handlerDefinition) {
        this.handlerDefinition = handlerDefinition;
        return this;
    }

    @Override
    public TestExecutor<T> given(Object... domainEvents) {
        return given(Arrays.asList(domainEvents));
    }

    @Override
    public TestExecutor<T> andGiven(Object... domainEvents) {
        return andGiven(Arrays.asList(domainEvents));
    }

    @Override
    public TestExecutor<T> givenNoPriorActivity() {
        return given(Collections.emptyList());
    }

    @Override
    public TestExecutor<T> givenState(Supplier<T> aggregate) {
        clearGivenWhenState();
        DefaultUnitOfWork.startAndGet(null).execute(() -> {
            if (repository == null) {
                registerRepository(new InMemoryRepository<>(aggregateType, eventStore, getRepositoryProvider()));
            }
            try {
                repository.newInstance(aggregate::get);
            } catch (Exception e) {
                throw new FixtureExecutionException(
                        "An exception occurred while trying to initialize repository with given aggregate (using 'givenState')",
                        e);
            }
        });
        return this;
    }

    @Override
    public TestExecutor<T> given(List<?> domainEvents) {
        ensureRepositoryConfiguration();
        clearGivenWhenState();
        return andGiven(domainEvents);
    }

    @Override
    public TestExecutor<T> andGiven(List<?> domainEvents) {
        for (Object event : domainEvents) {
            Object payload = event;
            MetaData metaData = null;
            if (event instanceof Message) {
                payload = ((Message) event).getPayload();
                metaData = ((Message) event).getMetaData();
            }
            this.givenEvents.add(new GenericDomainEventMessage<>(aggregateType.getSimpleName(), aggregateIdentifier,
                                                                 sequenceNumber++, payload, metaData));
        }
        return this;
    }

    @Override
    public TestExecutor<T> givenCommands(Object... commands) {
        return givenCommands(Arrays.asList(commands));
    }

    @Override
    public TestExecutor<T> andGivenCommands(Object... commands) {
        return andGivenCommands(Arrays.asList(commands));
    }

    @Override
    public TestExecutor<T> givenCommands(List<?> commands) {
        clearGivenWhenState();
        return andGivenCommands(commands);
    }

    @Override
    public TestExecutor<T> andGivenCommands(List<?> commands) {
        finalizeConfiguration();
        for (Object command : commands) {
            ExecutionExceptionAwareCallback callback = new ExecutionExceptionAwareCallback();
            commandBus.dispatch(GenericCommandMessage.asCommandMessage(command), callback);
            callback.assertSuccessful();
            givenEvents.addAll(storedEvents);
            storedEvents.clear();
        }
        publishedEvents.clear();
        return this;
    }

    @Override
    public TestExecutor<T> andGivenCurrentTime(Instant currentTime) {
        deadlineManager.initializeAt(currentTime);
        return this;
    }

    @Override
    public Instant currentTime() {
        return deadlineManager.getCurrentDateTime();
    }

    @Override
    public ResultValidator andThenTimeElapses(Duration elapsedTime) {
        deadlineManager.advanceTimeBy(elapsedTime, this::handleDeadline);
        return buildResultValidator();
    }

    @Override
    public ResultValidator andThenTimeAdvancesTo(Instant newDateTime) {
        deadlineManager.advanceTimeTo(newDateTime, this::handleDeadline);
        return buildResultValidator();
    }

    @Override
    public ResultValidator<T> when(Object command) {
        return when(command, MetaData.emptyInstance());
    }

    @Override
    public ResultValidator<T> when(Object command, Map<String, ?> metaData) {
        finalizeConfiguration();
        final MatchAllFieldFilter fieldFilter = new MatchAllFieldFilter(fieldFilters);
        ResultValidatorImpl<T> resultValidator = new ResultValidatorImpl<>(publishedEvents,
                                                                           fieldFilter,
                                                                           () -> repository.getAggregate(),
                                                                           deadlineManager);

        commandBus.dispatch(GenericCommandMessage.asCommandMessage(command).andMetaData(metaData), resultValidator);

        if (!repository.rolledBack) {
            Aggregate<T> workingAggregate = repository.aggregate;
            detectIllegalStateChanges(fieldFilter, workingAggregate);
        }
        resultValidator.assertValidRecording();
        return resultValidator;
    }

    /**
     * Handles the given {@code deadlineMessage} in the aggregate described by the given {@code aggregateDescriptor}.
     * Deadline message is handled in the scope of a {@link UnitOfWork}. If handling the deadline results in an
     * exception, the exception will be wrapped in a {@link FixtureExecutionException}.
     *
     * @param aggregateDescriptor A {@link ScopeDescriptor} describing the aggregate under test
     * @param deadlineMessage     The {@link DeadlineMessage} to be handled
     */
    protected void handleDeadline(ScopeDescriptor aggregateDescriptor, DeadlineMessage<?> deadlineMessage)
            throws Exception {
        ensureRepositoryConfiguration();
        repository.send(deadlineMessage, aggregateDescriptor);
    }

    private ResultValidator buildResultValidator() {
        MatchAllFieldFilter fieldFilter = new MatchAllFieldFilter(fieldFilters);
        ResultValidatorImpl resultValidator = new ResultValidatorImpl<>(publishedEvents,
                                                                        fieldFilter,
                                                                        () -> repository.getAggregate(),
                                                                        deadlineManager);
        resultValidator.assertValidRecording();
        return resultValidator;
    }

    private void ensureRepositoryConfiguration() {
        if (repository == null) {
            registerRepository(EventSourcingRepository.builder(aggregateType)
                                                      .aggregateFactory(new GenericAggregateFactory<>(aggregateType))
                                                      .eventStore(eventStore)
                                                      .parameterResolverFactory(parameterResolverFactory)
                                                      .handlerDefinition(handlerDefinition)
                                                      .repositoryProvider(getRepositoryProvider())
                                                      .build());
        }
    }

    private RepositoryProvider getRepositoryProvider() {
        if (repositoryProvider == null) {
            registerRepositoryProvider(new DefaultRepositoryProvider());
        }
        return repositoryProvider;
    }

    private void finalizeConfiguration() {
        registerAggregateCommandHandlers();
        registerCommandInterceptors();
        explicitCommandHandlersSet = true;
    }

    private void registerAggregateCommandHandlers() {
        ensureRepositoryConfiguration();
        if (!explicitCommandHandlersSet) {
            AggregateAnnotationCommandHandler<T> handler =
                    AggregateAnnotationCommandHandler.<T>builder()
                            .aggregateType(aggregateType)
                            .parameterResolverFactory(parameterResolverFactory)
                            .repository(repository)
                            .build();
            handler.subscribe(commandBus);
        }
    }

    private void registerCommandInterceptors() {
        commandDispatchInterceptors.forEach(commandBus::registerDispatchInterceptor);
        commandHandlerInterceptors.forEach(commandBus::registerHandlerInterceptor);
    }

    private void detectIllegalStateChanges(MatchAllFieldFilter fieldFilter, Aggregate<T> workingAggregate) {
        logger.debug("Starting separate Unit of Work for the purpose of checking illegal state changes in Aggregate");
        if (aggregateIdentifier != null && workingAggregate != null && reportIllegalStateChange) {
            UnitOfWork<?> uow = DefaultUnitOfWork.startAndGet(null);
            try {
                Aggregate<T> aggregate2 = repository.delegate.load(aggregateIdentifier);
                if (workingAggregate.isDeleted()) {
                    throw new AxonAssertionError("The working aggregate was considered deleted, " +
                                                         "but the Repository still contains a non-deleted copy of " +
                                                         "the aggregate. Make sure the aggregate explicitly marks " +
                                                         "itself as deleted in an EventHandler.");
                }
                assertValidWorkingAggregateState(aggregate2, fieldFilter, workingAggregate);
            } catch (AggregateNotFoundException notFound) {
                if (!workingAggregate.isDeleted()) {
                    throw new AxonAssertionError("The working aggregate was not considered deleted, " //NOSONAR
                                                         + "but the Repository cannot recover the state of the " +
                                                         "aggregate, as it is considered deleted there.");
                }
            } catch (Exception e) {
                throw new FixtureExecutionException("An Exception occurred while reconstructing the Aggregate from " +
                                                            "given and published events. This may be an indication " +
                                                            "that the aggregate cannot be recreated from its events.",
                                                    e);
            } finally {
                // rollback to prevent changes bing pushed to event store
                uow.rollback();
            }
        }
    }

    private void assertValidWorkingAggregateState(Aggregate<T> eventSourcedAggregate, MatchAllFieldFilter fieldFilter,
                                                  Aggregate<T> workingAggregate) {
        HashSet<ComparationEntry> comparedEntries = new HashSet<>();
        if (!workingAggregate.rootType().equals(eventSourcedAggregate.rootType())) {
            throw new AxonAssertionError(String.format("The aggregate loaded based on the generated events seems to " +
                                                               "be of another type than the original.\n" +
                                                               "Working type: <%s>\nEvent Sourced type: <%s>",
                                                       workingAggregate.rootType().getName(),
                                                       eventSourcedAggregate.rootType().getName()));
        }
        ensureValuesEqual(workingAggregate.invoke(Function.identity()),
                          eventSourcedAggregate.invoke(Function.identity()), eventSourcedAggregate.rootType().getName(),
                          comparedEntries, fieldFilter);
    }

    private void ensureValuesEqual(Object workingValue, Object eventSourcedValue, String propertyPath,
                                   Set<ComparationEntry> comparedEntries, FieldFilter fieldFilter) {
        if (explicitlyUnequal(workingValue, eventSourcedValue)) {
            throw new AxonAssertionError(format("Illegal state change detected! " +
                                                        "Property \"%s\" has different value when sourcing events.\n" +
                                                        "Working aggregate value:     <%s>\n" +
                                                        "Value after applying events: <%s>", propertyPath, workingValue,
                                                eventSourcedValue));
        } else if (workingValue != null && comparedEntries.add(new ComparationEntry(workingValue, eventSourcedValue)) &&
                !hasEqualsMethod(workingValue.getClass())) {
            for (Field field : fieldsOf(workingValue.getClass())) {
                if (fieldFilter.accept(field) && !Modifier.isStatic(field.getModifiers()) &&
                        !Modifier.isTransient(field.getModifiers())) {
                    ensureAccessible(field);
                    String newPropertyPath = propertyPath + "." + field.getName();

                    Object workingFieldValue = ReflectionUtils.getFieldValue(field, workingValue);
                    Object eventSourcedFieldValue = ReflectionUtils.getFieldValue(field, eventSourcedValue);
                    ensureValuesEqual(workingFieldValue, eventSourcedFieldValue, newPropertyPath, comparedEntries,
                                      fieldFilter);
                }
            }
        }
    }

    private void clearGivenWhenState() {
        storedEvents = new LinkedList<>();
        publishedEvents = new ArrayList<>();
        givenEvents = new LinkedList<>();
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
        return eventStore;
    }

    @Override
    public EventStore getEventStore() {
        return eventStore;
    }

    @Override
    public Repository<T> getRepository() {
        ensureRepositoryConfiguration();
        return repository;
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
            return Objects.equals(workingObject, that.workingObject) &&
                    Objects.equals(eventSourceObject, that.eventSourceObject);
        }

        @Override
        public int hashCode() {
            return Objects.hash(workingObject, eventSourceObject);
        }
    }

    private static class IdentifierValidatingRepository<T> implements Repository<T> {

        private final Repository<T> delegate;
        private Aggregate<T> aggregate;
        private boolean rolledBack;

        public IdentifierValidatingRepository(Repository<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Aggregate<T> newInstance(Callable<T> factoryMethod) throws Exception {
            CurrentUnitOfWork.get().onRollback(u -> this.rolledBack = true);
            aggregate = delegate.newInstance(factoryMethod);
            return aggregate;
        }

        @Override
        public Aggregate<T> load(String aggregateIdentifier, Long expectedVersion) {
            CurrentUnitOfWork.get().onRollback(u -> this.rolledBack = true);
            aggregate = delegate.load(aggregateIdentifier, expectedVersion);
            validateIdentifier(aggregateIdentifier, aggregate);
            return aggregate;
        }

        @Override
        public Aggregate<T> load(String aggregateIdentifier) {
            CurrentUnitOfWork.get().onRollback(u -> this.rolledBack = true);
            aggregate = delegate.load(aggregateIdentifier, null);
            validateIdentifier(aggregateIdentifier, aggregate);
            return aggregate;
        }

        private void validateIdentifier(String aggregateIdentifier, Aggregate<T> aggregate) {
            if (aggregateIdentifier != null && !aggregateIdentifier.equals(aggregate.identifierAsString())) {
                throw new AssertionError(String.format(
                        "The aggregate used in this fixture was initialized with an identifier different than " +
                                "the one used to load it. Loaded [%s], but actual identifier is [%s].\n" +
                                "Make sure the identifier passed in the Command matches that of the given Events.",
                        aggregateIdentifier, aggregate.identifierAsString()));
            }
        }

        public Aggregate<T> getAggregate() {
            Assert.state(!rolledBack, () -> "The state of this aggregate cannot be retrieved because it " +
                    "has been modified in a Unit of Work that was rolled back");

            return aggregate;
        }

        @Override
        public void send(Message<?> message, ScopeDescriptor scopeDescription) throws Exception {
            if (canResolve(scopeDescription)) {
                load(((AggregateScopeDescriptor) scopeDescription).getIdentifier().toString()).handle(message);
            }
        }

        @Override
        public boolean canResolve(ScopeDescriptor scopeDescription) {
            return scopeDescription instanceof AggregateScopeDescriptor;
        }
    }

    private static class InMemoryRepository<T> implements Repository<T> {

        private final EventBus eventBus;
        private final RepositoryProvider repositoryProvider;
        private final AggregateModel<T> aggregateModel;
        private AnnotatedAggregate<T> storedAggregate;

        protected InMemoryRepository(Class<T> aggregateType, EventBus eventBus, RepositoryProvider repositoryProvider) {
            this.aggregateModel = AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateType);
            this.eventBus = eventBus;
            this.repositoryProvider = repositoryProvider;
        }

        @Override
        public Aggregate<T> newInstance(Callable<T> factoryMethod) throws Exception {
            Assert.state(storedAggregate == null,
                         () -> "Creating an Aggregate while one is already stored. Test fixtures do not allow multiple instances to be stored.");
            storedAggregate = AnnotatedAggregate.initialize(factoryMethod,
                                                            aggregateModel,
                                                            eventBus,
                                                            repositoryProvider,
                                                            true);
            return storedAggregate;
        }

        @Override
        public Aggregate<T> load(String aggregateIdentifier) {
            return load(aggregateIdentifier, null);
        }

        @Override
        public Aggregate<T> load(String aggregateIdentifier, Long expectedVersion) {
            if (storedAggregate == null) {
                throw new AggregateNotFoundException(aggregateIdentifier,
                                                     "Aggregate not found. No aggregate has been stored yet.");
            }
            if (!aggregateIdentifier.equals(storedAggregate.identifier().toString())) {
                throw new AggregateNotFoundException(
                        aggregateIdentifier,
                        "Aggregate not found. Did you mean to load " + storedAggregate.identifier() + "?"
                );
            }
            if (storedAggregate.isDeleted()) {
                throw new AggregateNotFoundException(aggregateIdentifier, "Aggregate not found. It has been deleted.");
            }
            if (expectedVersion != null && !Objects.equals(expectedVersion, storedAggregate.version())) {
                throw new ConflictingAggregateVersionException(aggregateIdentifier,
                                                               expectedVersion,
                                                               storedAggregate.version());
            }
            return storedAggregate;
        }

        @Override
        public void send(Message<?> message, ScopeDescriptor scopeDescription) throws Exception {
            if (canResolve(scopeDescription)) {
                load(((AggregateScopeDescriptor) scopeDescription).getIdentifier().toString()).handle(message);
            }
        }

        @Override
        public boolean canResolve(ScopeDescriptor scopeDescription) {
            return scopeDescription instanceof AggregateScopeDescriptor;
        }
    }

    private class RecordingEventStore implements EventStore {

        @Override
        public DomainEventStream readEvents(String identifier) {
            if (aggregateIdentifier != null && !aggregateIdentifier.equals(identifier)) {
                throw new EventStoreException("You probably want to use aggregateIdentifier() on your fixture " +
                                                      "to get the aggregate identifier to use");
            } else if (aggregateIdentifier == null) {
                aggregateIdentifier = identifier;
                injectAggregateIdentifier();
            }
            List<DomainEventMessage<?>> allEvents = new ArrayList<>(givenEvents);
            allEvents.addAll(storedEvents);
            if (allEvents.isEmpty()) {
                throw new AggregateNotFoundException(identifier,
                                                     "No 'given' events were configured for this aggregate, " +
                                                             "nor have any events been stored.");
            }
            return DomainEventStream.of(allEvents);
        }

        @Override
        public void publish(List<? extends EventMessage<?>> events) {
            if (CurrentUnitOfWork.isStarted()) {
                CurrentUnitOfWork.get().onPrepareCommit(u -> doAppendEvents(events));
            } else {
                doAppendEvents(events);
            }
        }

        protected void doAppendEvents(List<? extends EventMessage<?>> events) {
            publishedEvents.addAll(events);
            events.stream().filter(DomainEventMessage.class::isInstance).map(e -> (DomainEventMessage<?>) e)
                  .forEach(event -> {
                      if (aggregateIdentifier == null) {
                          aggregateIdentifier = event.getAggregateIdentifier();
                          injectAggregateIdentifier();
                      }

                      DomainEventMessage lastEvent = (storedEvents.isEmpty() ? givenEvents : storedEvents).peekLast();

                      if (lastEvent != null) {
                          if (!lastEvent.getAggregateIdentifier().equals(event.getAggregateIdentifier())) {
                              throw new EventStoreException(
                                      "Writing events for an unexpected aggregate. This could " +
                                              "indicate that a wrong aggregate is being triggered.");
                          } else if (lastEvent.getSequenceNumber() != event.getSequenceNumber() - 1) {
                              throw new EventStoreException(format("Unexpected sequence number on stored event. " +
                                                                           "Expected %s, but got %s.",
                                                                   lastEvent.getSequenceNumber() + 1,
                                                                   event.getSequenceNumber()));
                          }
                      }
                      storedEvents.add(event);
                  });
        }

        private void injectAggregateIdentifier() {
            List<DomainEventMessage> oldEvents = new ArrayList<>(givenEvents);
            givenEvents.clear();
            for (DomainEventMessage oldEvent : oldEvents) {
                if (oldEvent.getAggregateIdentifier() == null) {
                    givenEvents.add(new GenericDomainEventMessage<>(oldEvent.getType(), aggregateIdentifier,
                                                                    oldEvent.getSequenceNumber(), oldEvent.getPayload(),
                                                                    oldEvent.getMetaData(), oldEvent.getIdentifier(),
                                                                    oldEvent.getTimestamp()));
                } else {
                    givenEvents.add(oldEvent);
                }
            }
        }

        @Override
        public TrackingEventStream openStream(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void storeSnapshot(DomainEventMessage<?> snapshot) {
        }

        @Override
        public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            return () -> true;
        }

        @Override
        public Registration registerDispatchInterceptor(
                MessageDispatchInterceptor<? super EventMessage<?>> dispatchInterceptor) {
            return () -> true;
        }
    }

    private class ExecutionExceptionAwareCallback implements CommandCallback<Object, Object> {

        private FixtureExecutionException exception;

        @Override
        public void onResult(CommandMessage<?> commandMessage, CommandResultMessage<?> commandResultMessage) {
            if (commandResultMessage.isExceptional()) {
                Throwable cause = commandResultMessage.exceptionResult();
                if (cause instanceof FixtureExecutionException) {
                    this.exception = (FixtureExecutionException) cause;
                } else {
                    this.exception = new FixtureExecutionException("Failed to execute givenCommands", cause);
                }
            }
        }

        public void assertSuccessful() {
            if (exception != null) {
                throw exception;
            }
        }
    }

    private class DefaultRepositoryProvider implements RepositoryProvider {

        @Override
        public <R> Repository<R> repositoryFor(Class<R> aggregateType) {
            return new CreationalRepository<>(aggregateType, this);
        }
    }

    private class CreationalRepository<R> implements Repository<R> {

        private final Class<R> aggregateType;
        private final RepositoryProvider repositoryProvider;

        private CreationalRepository(Class<R> aggregateType,
                                     RepositoryProvider repositoryProvider) {
            this.aggregateType = aggregateType;
            this.repositoryProvider = repositoryProvider;
        }

        @Override
        public Aggregate<R> load(String aggregateIdentifier) {
            throw new UnsupportedOperationException(
                    "Default repository does not mock loading of an aggregate, only creation of it");
        }

        @Override
        public Aggregate<R> load(String aggregateIdentifier, Long expectedVersion) {
            throw new UnsupportedOperationException(
                    "Default repository does not mock loading of an aggregate, only creation of it");
        }

        @Override
        public Aggregate<R> newInstance(Callable<R> factoryMethod) throws Exception {
            AggregateModel<R> aggregateModel = AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateType);
            return EventSourcedAggregate.initialize(factoryMethod, aggregateModel, eventStore, repositoryProvider);
        }

        @Override
        public void send(Message<?> message, ScopeDescriptor scopeDescription) {
            throw new UnsupportedOperationException(
                    "Default repository does not mock loading of an aggregate, only creation of it");
        }

        @Override
        public boolean canResolve(ScopeDescriptor scopeDescription) {
            return false;
        }
    }
}
