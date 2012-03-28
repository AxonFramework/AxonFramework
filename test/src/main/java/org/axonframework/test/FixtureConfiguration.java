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
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.EventStore;

import java.util.List;

/**
 * Interface describing the operations available on a test fixture in the configuration stage. This stage allows a test
 * case to prepare the fixture for test execution.
 * <p/>
 * In the preparation stage, you should register all components required for your test, mainly the command handler. A
 * typical command handler will require a repository. The test fixture can create a generic repository using the {@link
 * #createRepository(Class)} method. Alternatively, you can register your own repository using the {@link
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
 *         fixture = Fixtures.newGivenWhenThenFixture();
 *         MyCommandHandler commandHandler = new MyCommandHandler();
 *         commandHandler.setRepository(fixture.<strong>createRepository(MyAggregate.class)</strong>);
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
 * Providing the "given" events using the {@link #given(Object...)} or {@link
 * #given(java.util.List) given(List&lt;DomainEvent&gt;)} methods must be the last operation in the configuration
 * stage.
 * <p/>
 * Besides setting configuration, you can also use the FixtureConfiguration to get access to the configured components.
 * This allows you to (manually) inject the EventBus or any other component into you command handler, for example.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface FixtureConfiguration {

    /**
     * Creates and registers a generic repository for the given <code>aggregateClass</code>. It is not recommended to
     * use this method to create more than a single generic repository. If multiple repositories are created, the exact
     * behavior is undefined.
     *
     * @param aggregateClass The class of the aggregate to create the repository for
     * @param <T>            The type of the aggregate to create the repository for
     * @return The generic repository, which has been registered with the fixture
     *
     * @see org.axonframework.eventsourcing.EventSourcingRepository
     */
    <T extends EventSourcedAggregateRoot> EventSourcingRepository<T> createRepository(Class<T> aggregateClass);

    /**
     * Registers an arbitrary event sourcing <code>repository</code> with the fixture. The repository will be wired
     * with
     * an Event Store and Event Bus implementation suitable for this test fixture.
     *
     * @param repository The repository to use in the test case
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration registerRepository(EventSourcingRepository<?> repository);

    /**
     * Registers an <code>annotatedCommandHandler</code> with this fixture. This will register this command handler
     * with
     * the command bus used in this fixture.
     *
     * @param annotatedCommandHandler The command handler to register for this test
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration registerAnnotatedCommandHandler(Object annotatedCommandHandler);

    /**
     * Registers a <code>commandHandler</code> to handle commands of the given <code>commandType</code> with the
     * command
     * bus used by this fixture.
     *
     * @param commandType    The type of command to register the handler for
     * @param commandHandler The handler to register
     * @return the current FixtureConfiguration, for fluent interfacing
     */
    FixtureConfiguration registerCommandHandler(Class<?> commandType, CommandHandler commandHandler);

    /**
     * Configures the given <code>domainEvents</code> as the "given" events. These are the events returned by the event
     * store when an aggregate is loaded.
     *
     * @param domainEvents the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor given(Object... domainEvents);

    /**
     * Configures the given <code>domainEvents</code> as the "given" events. These are the events returned by the event
     * store when an aggregate is loaded.
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
     * Returns the identifier of the aggregate that this fixture prepares. When commands need to load an aggregate
     * using
     * a specific identifier, use this method to obtain the correct identifier to use.
     *
     * @return the identifier of the aggregate prepared in this fixture
     */
    Object getAggregateIdentifier();

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
     * are
     * recorded an evaluated in the {@link ResultValidator} operations.
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
     * repository
     * is configured to use the fixture's event store to load events.
     *
     * @return the repository used by this fixture
     */
    EventSourcingRepository<?> getRepository();

    /**
     * Sets the aggregate identifier of the fixture to the given <code>aggregateIdentifier</code>. This allows for the
     * use of aggregate identifiers that have a meaning inside the aggregate.
     *
     * @param aggregateIdentifier The aggregate identifier the fixture should use.s
     */
    void setAggregateIdentifier(Object aggregateIdentifier);

    /**
     * Sets whether or not the fixture should detect and report state changes that occur outside of Event Handler
     * methods.
     *
     * @param reportIllegalStateChange whether or not to detect and report state changes outside of Event Handler
     *                                 methods.
     */
    void setReportIllegalStateChange(boolean reportIllegalStateChange);
}
