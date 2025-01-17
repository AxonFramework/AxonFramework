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

package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.test.FixtureExecutionException;
import org.axonframework.test.matchers.FieldFilter;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Interface describing the operations available on a test fixture in the configuration stage. This stage allows a test
 * case to prepare the fixture for test execution.
 * <p/>
 * The fixture is initialized using a Command Handler that expects an {@code @CommandHandler} aggregate. If you
 * have implemented your own command handler (either using annotations, or by implementing the {@link MessageHandler}
 * interface), you must register the command handler using {@link #registerAnnotatedCommandHandler(Object)} or {@link
 * #registerCommandHandler(Class, MessageHandler)}, respectively. A typical command
 * handler will require a repository. The test fixture initializes an Event Sourcing Repository, which can be obtained
 * using {@link #getRepository()}. Alternatively, you can register your own repository using the {@link
 * #registerRepository(Repository)} method. Registering the repository
 * will cause the fixture to configure the correct {@link EventBus} and {@link EventStore} implementations required by
 * the test.
 * <p/>
 * Typical usage example:<br/> <code>
 * <pre>
 * public class MyCommandHandlerTest() {
 * <br/>       private FixtureConfiguration fixture;
 * <br/>      {@code @}Before
 *     public void setUp() {
 *         fixture = new AggregateTestFixture(MyAggregate.class);
 *         MyCommandHandler commandHandler = new MyCommandHandler();
 *         commandHandler.setRepository(fixture.<strong>getRepository()</strong>);
 *         fixture.<strong>registerAnnotatedCommandHandler(commandHandler)</strong>;
 *     }
 * <br/>      {@code @}Test
 *     public void testCommandHandlerCase() {
 *         fixture.<strong>given(new MyEvent(1), new MyEvent(2))</strong>
 *                .when(new TestCommand())
 *                .expectResultMessagePayload(null)
 *                .expectEvents(new MyEvent(3));
 *     }
 * <br/>  }
 * </pre>
 * </code>
 * <p/>
 * If you use {@code @CommandHandler} annotations on the aggregate, you do not need to configure any additional command
 * handlers. In that case, no configuration is required:
 * <p/>
 * Providing the "given" events using the {@link #given(Object...)} or {@link
 * #given(java.util.List) given(List&lt;DomainEvent&gt;)} methods must be the last operation in the configuration
 * stage. To indicate that no "given" events are available, just call {@code given()} with no parameters.
 * <p/>
 * Besides setting configuration, you can also use the FixtureConfiguration to get access to the configured components.
 * This allows you to (manually) inject the EventBus or any other component into you command handler, for example.
 *
 * @param <T> The type of the aggregate under test
 * @author Allard Buijze
 * @since 0.6
 */
public interface FixtureConfiguration<T> {

    /**
     * Registers subtypes of this aggregate to support aggregate polymorphism. Command Handlers defined on this subtype
     * will be considered part of this aggregate's handlers.
     *
     * @param subtypes subtypes in this polymorphic hierarchy
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> withSubtypes(Class<? extends T>... subtypes);

    /**
     * Configures the fixture for state stored aggregates.
     * This will register an in-memory {@link org.axonframework.modelling.command.Repository} with this fixture.
     * Should be used before calling {@link FixtureConfiguration#givenState(Supplier)} or {@link FixtureConfiguration#givenCommands(List)} (Supplier)}.
     *
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> useStateStorage();

    /**
     * Registers an arbitrary {@code repository} with the fixture. The repository must be wired
     * with the Event Store of this test fixture.
     * <p/>
     * Should not be used in combination with {@link
     * #registerAggregateFactory(org.axonframework.eventsourcing.AggregateFactory)}, as that will overwrite any
     * repository previously registered.
     *
     * @param repository The repository to use in the test case
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerRepository(Repository<T> repository);

    /**
     * Registers repository provider with the fixture. If an aggregate being tested spawns new aggregates, this
     * provider should be registered. Otherwise, it is not going to be invoked.
     *
     * @param repositoryProvider provides repositories for specified aggregate types
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerRepositoryProvider(RepositoryProvider repositoryProvider);

    /**
     * Registers the given {@code aggregateFactory} with the fixture. The repository used by the fixture will use
     * the given factory to create new aggregate instances. Defaults to an Aggregate Factory that uses the no-arg
     * constructor to create new instances.
     * <p/>
     * Should not be used in combination with {@link
     * #registerRepository(Repository)}, as that will overwrite any aggregate factory previously registered.
     *
     * @param aggregateFactory The Aggregate Factory to create empty aggregates with
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerAggregateFactory(AggregateFactory<T> aggregateFactory);

    /**
     * Registers an {@code annotatedCommandHandler} with this fixture. This will register this command handler
     * with the command bus used in this fixture.
     *
     * @param annotatedCommandHandler The command handler to register for this test
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerAnnotatedCommandHandler(Object annotatedCommandHandler);

    /**
     * Registers a {@code commandHandler} to handle commands of the given {@code commandType} with the
     * command bus used by this fixture.
     *
     * @param payloadType    The type of command to register the handler for
     * @param commandHandler The handler to register
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerCommandHandler(Class<?> payloadType, MessageHandler<CommandMessage<?>, CommandResultMessage<?>> commandHandler);

    /**
     * Registers a {@code commandHandler} to handle commands of the given {@code commandType} with the
     * command bus used by this fixture.
     *
     * @param commandName    The name of the command to register the handler for
     * @param commandHandler The handler to register
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerCommandHandler(String commandName, MessageHandler<CommandMessage<?>, CommandResultMessage<?>> commandHandler);

    /**
     * Registers a resource that is eligible for injection in handler method (e.g. methods annotated with {@link
     * CommandHandler @CommandHandler}, {@link EventSourcingHandler @EventSourcingHandler} and {@link EventHandler}.
     * These resource must be registered <em>before</em> registering any command handler.
     *
     * @param resource the resource eligible for injection
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerInjectableResource(Object resource);

    /**
     * Default implementation to register multiple resources in handler method (e.g. methods annotated with {@link
     * CommandHandler @CommandHandler}, {@link EventSourcingHandler @EventSourcingHandler} and {@link EventHandler}.
     * These resource must be registered <em>before</em> registering any command handler.
     * Internally this method calls {@link #registerInjectableResource(Object) for each resource.
     *
     * @param resources collection of resources eligible for injection
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    default FixtureConfiguration<T> registerInjectableResources(Object... resources) {
        for (Object resource : resources) {
            registerInjectableResource(resource);
        }
        return this;
    }

    /**
     * Registers a {@link ParameterResolverFactory} within this fixture. The given {@code parameterResolverFactory}
     * will be added to the other parameter resolver factories introduced through {@link
     * org.axonframework.messaging.annotation.ClasspathParameterResolverFactory#forClass(Class)} and the {@link
     * org.axonframework.messaging.annotation.SimpleResourceParameterResolverFactory} adding the registered resources
     * (with {@link #registerInjectableResource(Object)}. The generic {@code T} is used as input for the {@code
     * ClasspathParameterResolverFactory#forClass(Class)} operation.
     *
     * @param parameterResolverFactory the {@link ParameterResolver} to register within this fixture
     * @return the current FixtureConfiguration, for fluent interfacing
     * @see #registerInjectableResource(Object)
     */
    FixtureConfiguration<T> registerParameterResolverFactory(ParameterResolverFactory parameterResolverFactory);

    /**
     * Register a {@link MessageDispatchInterceptor} for {@link CommandMessage}s which will be invoked before any
     * command is dispatched on the {@link CommandBus} to perform a task specified in the interceptor. For example by
     * adding {@link MetaData} or throwing an exception based on the command.
     *
     * @param commandDispatchInterceptor the {@link MessageDispatchInterceptor} for {@link CommandMessage}s to be added
     *                                   to this fixture's {@link CommandBus}
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerCommandDispatchInterceptor(
            MessageDispatchInterceptor<? super CommandMessage<?>> commandDispatchInterceptor
    );

    /**
     * Register a {@link MessageHandlerInterceptor} for {@link CommandMessage}s which will be invoked before or after
     * the command has been dispatched on the {@link CommandBus} to perform a task specified in the interceptor. It
     * could for example block the command for security reasons or add auditing to the command bus
     *
     * @param commandHandlerInterceptor the {@link MessageHandlerInterceptor} for {@link CommandMessage}s to be added to
     *                                  this fixture's {@link CommandBus}
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerCommandHandlerInterceptor(
            MessageHandlerInterceptor<? super CommandMessage<?>> commandHandlerInterceptor
    );

    /**
     * Registers a deadline dispatch interceptor which will always be invoked before a deadline is dispatched
     * (scheduled) on the {@link org.axonframework.deadline.DeadlineManager} to perform a task specified in the
     * interceptor.
     *
     * @param deadlineDispatchInterceptor the interceptor for dispatching (scheduling) deadlines
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerDeadlineDispatchInterceptor(
            MessageDispatchInterceptor<? super DeadlineMessage<?>> deadlineDispatchInterceptor);

    /**
     * Registers a deadline handler interceptor which will always be invoked before a deadline is handled to perform a
     * task specified in the interceptor.
     *
     * @param deadlineHandlerInterceptor the interceptor for handling deadlines
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerDeadlineHandlerInterceptor(
            MessageHandlerInterceptor<? super DeadlineMessage<?>> deadlineHandlerInterceptor);

    /**
     * Registers the given {@code fieldFilter}, which is used to define which Fields are used when comparing objects.
     * The {@link ResultValidator#expectEvents(Object...)} and {@link ResultValidator#expectResultMessagePayload(Object)},
     * for example, use this filter.
     * <p/>
     * When multiple filters are registered, a Field must be accepted by all registered filters in order to be
     * accepted.
     * <p/>
     * By default, all Fields are included in the comparison.
     *
     * @param fieldFilter The FieldFilter that defines which fields to include in the comparison
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerFieldFilter(FieldFilter fieldFilter);

    /**
     * Indicates that a field with given {@code fieldName}, which is declared in given {@code declaringClass}
     * is ignored when performing deep equality checks.
     *
     * @param declaringClass The class declaring the field
     * @param fieldName      The name of the field
     * @return the current FixtureConfiguration, for fluent interfacing
     *
     * @throws FixtureExecutionException when no such field is declared
     */
    FixtureConfiguration<T> registerIgnoredField(Class<?> declaringClass, String fieldName);

    /**
     * Registers a {@link HandlerDefinition} within this fixture. The given {@code handlerDefinition} is added to the
     * handler definitions introduced through {@link org.axonframework.messaging.annotation.ClasspathHandlerDefinition#forClass(Class)}.
     * The generic {@code T} is used as input for the {@code ClasspathHandlerDefinition#forClass(Class)} operation.
     *
     * @param handlerDefinition used to create concrete handlers
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerHandlerDefinition(HandlerDefinition handlerDefinition);

    /**
     * Registers a {@link HandlerEnhancerDefinition} within this fixture. This given {@code handlerEnhancerDefinition}
     * is added to the handler enhancer definitions introduced through {@link org.axonframework.messaging.annotation.ClasspathHandlerEnhancerDefinition#forClass(Class)}.
     * The generic {@code T} is used as input for the {@code ClasspathHandlerEnhancerDefinition#forClass(Class)}
     * operation.
     *
     * @param handlerEnhancerDefinition the {@link HandlerEnhancerDefinition} to register within this fixture
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerHandlerEnhancerDefinition(HandlerEnhancerDefinition handlerEnhancerDefinition);

    /**
     * Registers the {@link CommandTargetResolver} within this fixture. The {@code commandTargetResolver} will replace
     * the default implementation (defined by the {@link org.axonframework.modelling.command.AggregateAnnotationCommandHandler}
     * within this fixture.
     *
     * @param commandTargetResolver the {@link CommandTargetResolver} used to resolve an Aggregate for a given command
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerCommandTargetResolver(CommandTargetResolver commandTargetResolver);

    /**
     * Configures the given {@code domainEvents} as the "given" events. These are the events returned by the event store
     * when an aggregate is loaded.
     * <p/>
     * If an item in the given {@code domainEvents} implements {@link Message}, the payload and {@link MetaData} from
     * that {@code Message} are copied into a newly created {@link org.axonframework.eventhandling.DomainEventMessage}.
     * Otherwise, a {@code DomainEventMessage} with the item as payload and empty {@code MetaData} is created.
     * <p>
     * Note that transitioning to the returned {@link TestExecutor} will clear any previously defined "given" state to
     * ensure the fixture can run a clean test environment.
     *
     * @param domainEvents The domain events the event store should return.
     * @return A {@link TestExecutor} instance that can execute the test with this configuration.
     */
    TestExecutor<T> given(Object... domainEvents);

    /**
     * Sets the aggregate instance as supplied by given {@code aggregateState} as the initial state for a test case.
     * <p>
     * Usage of this method is highly discouraged for event sourced aggregates. In that case, use
     * {@link #given(Object...)} to specify historic events.
     * <p>
     * Note that transitioning to the returned {@link TestExecutor} will clear any previously defined "given" state to
     * ensure the fixture can run a clean test environment.
     *
     * @param aggregateState A {@link Supplier} providing the state to use as starting point for this fixture.
     * @return A {@link TestExecutor} instance that can execute the test with this configuration.
     */
    TestExecutor<T> givenState(Supplier<T> aggregateState);

    /**
     * Indicates that no relevant activities like commands or events have occurred in the past. This also means that no
     * previous state is present in the repository.
     *
     * @return A {@link TestExecutor} instance that can execute the test with this configuration.
     */
    TestExecutor<T> givenNoPriorActivity();

    /**
     * Configures the given {@code domainEvents} as the "given" events. These are the events returned by the event store
     * when an aggregate is loaded.
     * <p/>
     * If an item in the list implements {@link Message}, the payload and {@link MetaData} from that {@code Message} are
     * copied into a newly created {@link org.axonframework.eventhandling.DomainEventMessage}. Otherwise, a
     * {@code DomainEventMessage} with the item as payload and empty {@code MetaData} is created.
     * <p>
     * Note that transitioning to the returned {@link TestExecutor} will clear any previously defined "given" state to
     * ensure the fixture can run a clean test environment.
     *
     * @param domainEvents The domain events the event store should return.
     * @return A {@link TestExecutor} instance that can execute the test with this configuration.
     */
    TestExecutor<T> given(List<?> domainEvents);

    /**
     * Configures the given {@code commands} as to execute against the aggregate under test to initiate the given-phase.
     * The commands are executed, and the resulting stored events are captured.
     * <p>
     * Note that transitioning to the returned {@link TestExecutor} will clear any previously defined "given" state to
     * ensure the fixture can run a clean test environment.
     *
     * @param commands The commands to execute against the aggregate under test to initiate the given-phase.
     * @return A {@link TestExecutor} instance that can execute the test with this configuration.
     */
    TestExecutor<T> givenCommands(Object... commands);

    /**
     * Configures the given {@code commands} as to execute against the aggregate under test to initiate the given-phase.
     * The commands are executed, and the resulting stored events are captured.
     * <p>
     * Note that transitioning to the returned {@link TestExecutor} will clear any previously defined "given" state to
     * ensure the fixture can run a clean test environment.
     *
     * @param commands The commands to execute against the aggregate under test to initiate the given-phase.
     * @return A {@link TestExecutor} instance that can execute the test with this configuration.
     */
    TestExecutor<T> givenCommands(List<?> commands);

    /**
     * Returns the command bus used by this fixture. The command bus is provided for wiring purposes only, for example
     * to support composite commands (a single command that causes the execution of one or more others).
     *
     * @return the command bus used by this fixture
     */
    CommandBus getCommandBus();

    /**
     * Returns the event bus used by this fixture. The event bus is provided for wiring purposes only, for example to
     * allow command handlers to publish events other than Domain Events. Events published on the returned event bus
     * are recorded an evaluated in the {@link ResultValidator} operations.
     *
     * @return the event bus used by this fixture
     */
    EventBus getEventBus();

    /**
     * Returns the event store used by this fixture. This event store is provided for wiring purposes only.
     *
     * @return the event store used by this fixture
     */
    EventStore getEventStore();

    /**
     * Returns the repository used by this fixture. This repository is provided for wiring purposes only. The
     * repository is configured to use the fixture's event store to load events.
     *
     * @return the repository used by this fixture
     */
    Repository<T> getRepository();

    /**
     * Use this method to indicate a specific moment as the initial current time "known" by the fixture at the start of
     * the given state.
     * <p>
     * Note that transitioning to the returned {@link TestExecutor} will clear any previously defined "given" state to
     * ensure the fixture can run a clean test environment.
     *
     * @param time An {@link Instant} defining the simulated "current time" at which the given state is initialized.
     * @return A {@link TestExecutor} instance that can execute the test with this configuration.
     */
    TestExecutor<T> givenCurrentTime(Instant time);

    /**
     * Sets whether or not the fixture should detect and report state changes that occur outside of Event Handler
     * methods.
     *
     * @param reportIllegalStateChange whether or not to detect and report state changes outside of Event Handler
     *                                 methods.
     */
    void setReportIllegalStateChange(boolean reportIllegalStateChange);
}
