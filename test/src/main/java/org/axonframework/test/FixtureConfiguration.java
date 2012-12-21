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
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.Repository;

import java.util.List;

/**
 * Interface describing the operations available on a test fixture in the configuration stage. This stage allows a test
 * case to prepare the fixture for test execution.
 * <p/>
 * The fixture is initialized using a Command Handler that expects an <code>@CommandHandler</code> aggregate. If you
 * have implemented your own command handler (either using annotations, or by implementing the {@link CommandHandler}
 * interace), you must register the command handler using {@link #registerAnnotatedCommandHandler(Object)} or {@link
 * #registerCommandHandler(Class, org.axonframework.commandhandling.CommandHandler)}, respectively. A typical command
 * handler will require a repository. The test fixture initializes an Event Sourcing Repository, which can be obtained
 * using {@link #getRepository()}. Alternatively, you can register your own repository using the {@link
 * #registerRepository(org.axonframework.eventsourcing.EventSourcingRepository)} method. Registering the repository
 * will cause the fixture to configure the correct {@link EventBus} and {@link EventStore} implementations required by
 * the test.
 * <p/>
 * Typical usage example:<br/> <code>
 * <pre>
 * public class MyCommandHandlerTest() {
 * <br/>       private FixtureConfiguration fixture;
 * <br/>      {@code @}Before
 *     public void setUp() {
 *         fixture = Fixtures.newGivenWhenThenFixture(MyAggregate.class);
 *         MyCommandHandler commandHandler = new MyCommandHandler();
 *         commandHandler.setRepository(fixture.<strong>getRepository()</strong>);
 *         fixture.<strong>registerAnnotatedCommandHandler(commandHandler)</strong>;
 *     }
 * <br/>      {@code @}Test
 *     public void testCommandHandlerCase() {
 *         fixture.<strong>given(new MyEvent(1), new MyEvent(2))</strong>
 *                .when(new TestCommand())
 *                .expectReturnValue(Void.TYPE)
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
 * stage. To indicate that no "given" events are available, just call <code>given()</code> with no parameters.
 * <p/>
 * Besides setting configuration, you can also use the FixtureConfiguration to get access to the configured components.
 * This allows you to (manually) inject the EventBus or any other component into you command handler, for example.
 *
 * @param <T> The type of the aggregate under test
 * @author Allard Buijze
 * @since 0.6
 */
public interface FixtureConfiguration<T extends EventSourcedAggregateRoot> {

    /**
     * Registers an arbitrary event sourcing <code>repository</code> with the fixture. The repository will be wired
     * with an Event Store and Event Bus implementation suitable for this test fixture.
     * <p/>
     * Should not be used in combination with {@link
     * #registerAggregateFactory(org.axonframework.eventsourcing.AggregateFactory)}, as that will overwrite any
     * repository previously registered.
     *
     * @param repository The repository to use in the test case
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerRepository(EventSourcingRepository<T> repository);

    /**
     * Registers the given <code>aggregateFactory</code> with the fixture. The repository used by the fixture will use
     * the given factory to create new aggregate instances. Defaults to an Aggregate Factory that uses the no-arg
     * constructor to create new instances.
     * <p/>
     * Should not be used in combination with {@link
     * #registerRepository(org.axonframework.eventsourcing.EventSourcingRepository)}, as that will overwrite any
     * aggregate factory previously registered.
     *
     * @param aggregateFactory The Aggregate Factory to create empty aggregates with
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerAggregateFactory(AggregateFactory<T> aggregateFactory);

    /**
     * Registers an <code>annotatedCommandHandler</code> with this fixture. This will register this command handler
     * with the command bus used in this fixture.
     *
     * @param annotatedCommandHandler The command handler to register for this test
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerAnnotatedCommandHandler(Object annotatedCommandHandler);

    /**
     * Registers a <code>commandHandler</code> to handle commands of the given <code>commandType</code> with the
     * command bus used by this fixture.
     *
     * @param payloadType    The type of command to register the handler for
     * @param commandHandler The handler to register
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerCommandHandler(Class<?> payloadType, CommandHandler commandHandler);

    /**
     * Registers a <code>commandHandler</code> to handle commands of the given <code>commandType</code> with the
     * command bus used by this fixture.
     *
     * @param commandName    The name of the command to register the handler for
     * @param commandHandler The handler to register
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerCommandHandler(String commandName, CommandHandler commandHandler);

    /**
     * Registers a resource that is eligible for injection in {@link
     * org.axonframework.commandhandling.annotation.CommandHandler @CommandHandler} and {@link
     * org.axonframework.eventhandling.annotation.EventHandler @EventHandler} annotated methods. These resource must be
     * registered <em>before</em> registering any command handler.
     *
     * @param resource The resource eligible for injection
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration<T> registerInjectableResource(Object resource);

    /**
     * Configures the given <code>domainEvents</code> as the "given" events. These are the events returned by the event
     * store when an aggregate is loaded.
     * <p/>
     * If an item in the given <code>domainEvents</code> implements {@link org.axonframework.domain.Message}, the
     * payload and meta data from that message are copied into a newly created Domain Event Message. Otherwise, a
     * Domain Event Message with the item as payload and empty meta data is created.
     *
     * @param domainEvents the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor given(Object... domainEvents);

    /**
     * Configures the given <code>domainEvents</code> as the "given" events. These are the events returned by the event
     * store when an aggregate is loaded.
     * <p/>
     * If an item in the list implements {@link org.axonframework.domain.Message}, the payload and meta data from that
     * message are copied into a newly created Domain Event Message. Otherwise, a Domain Event Message with the item
     * as payload and empty meta data is created.
     *
     * @param domainEvents the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor given(List<?> domainEvents);

    /**
     * Configures the given <code>commands</code> as the command that will provide the "given" events. The commands are
     * executed, and the resulting stored events are captured.
     *
     * @param commands the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor givenCommands(Object... commands);

    /**
     * Configures the given <code>commands</code> as the command that will provide the "given" events. The commands are
     * executed, and the resulting stored events are captured.
     *
     * @param commands the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor givenCommands(List<?> commands);

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
     * Sets whether or not the fixture should detect and report state changes that occur outside of Event Handler
     * methods.
     *
     * @param reportIllegalStateChange whether or not to detect and report state changes outside of Event Handler
     *                                 methods.
     */
    void setReportIllegalStateChange(boolean reportIllegalStateChange);
}
