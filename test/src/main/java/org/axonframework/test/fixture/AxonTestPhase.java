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

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Interface describing the operations available on a test phase for testing Axon-based applications using a
 * given-when-then pattern. The test phase provides a fluent API to configure and execute tests against Axon
 * components.
 *
 * <p>
 * This interface defines four primary phases of a test:
 * <ul>
 *   <li>{@link Setup} - Initial configuration of the test fixture</li>
 *   <li>{@link Given} - Defining the initial state of the system before testing by events and commands</li>
 *   <li>{@link When} - Executing commands to test</li>
 *   <li>{@link Then} - Validating the results of the test</li>
 * </ul>
 * <p>
 * The test fixture manages {@link UnitOfWork} instances during test execution,
 * automatically committing as appropriate. During the Given phase, each operation (like {@link Given#event}},
 * {@link Given#command} or even batched like {@link Given#events} and {@link Given#commands})
 * is executed in its own separate {@link UnitOfWork} that is committed immediately after execution. In the When phase, a single Unit of Work is started
 * and committed after the command is executed. The Then phase only validates the results.
 * <p>
 * The test phases operates on components defined in {@link Configuration} that you pass to the fixture during its construction.
 * <p>
 * Typical usage example:<br/>
 * <pre>
 * {@code
 * var fixture = AxonTestFixture.with(configurer);
 *
 * fixture.given()
 *        .event(new AccountCreatedEvent("account-1"))
 *        .when()
 *        .command(new WithdrawMoneyCommand("account-1", 100.00))
 *        .then()
 *        .success()
 *        .events(new MoneyWithdrawnEvent("account-1", 100.00));
 * }
 * </pre>
 * <p>
 * Example with chaining multiple test scenarios using {@code and()}:<br/>
 * <pre>
 * {@code
 * var fixture = AxonTestFixture.with(configurer);
 *
 * fixture.given()
 *        .event(new AccountCreatedEvent("account-1", 500.00))
 *        .when()
 *        .command(new WithdrawMoneyCommand("account-1", 100.00))
 *        .then()
 *        .events(new MoneyWithdrawnEvent("account-1", 100.00))
 *        .success()
 *        .and()
 *        .when()
 *        .command(new WithdrawMoneyCommand("account-1", 500.00))
 *        .then()
 *        .exception(InsufficientBalanceException.class)
 *        .noEvents();
 * }
 * </pre>
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface AxonTestPhase {

    /**
     * Interface describing the initial setup phase of a test fixture. This phase allows configuring the test fixture
     * before starting the test execution.
     * <p>
     * From this phase, you can transition to either the Given phase to define initial state, or directly to the When
     * phase if no prior state is needed.
     */
    interface Setup {

        /**
         * Transition to the Given phase to define the initial state of the system before testing.
         *
         * @return A {@link Given} instance that allows defining the initial state.
         */
        AxonTestPhase.Given given();

        /**
         * Transition directly to the When phase, skipping the Given phase, which implies no prior state.
         *
         * @return A {@link When} instance that allows executing the test.
         */
        When when();

        /**
         * Stops the fixture, releasing any active resources, like registered handlers or pending event processing
         * tasks.
         */
        void stop();
    }

    /**
     * Interface describing the operations available in the Given phase of the test fixture execution. This phase is
     * used to define the initial state of the system before executing the test action.
     */
    interface Given {

        /**
         * Indicates that no relevant activities like commands or events have occurred in the past. This also means that
         * no previous state is present in the system.
         *
         * @return The current Given instance, for fluent interfacing
         */
        Given noPriorActivity();

        /**
         * Configures a single event with the given {@code payload} as part of the "given" state. This event will be
         * published with empty metadata.
         *
         * @param payload The payload of the event to publish.
         * @return The current Given instance, for fluent interfacing.
         */
        default Given event(@Nonnull Object payload) {
            return event(payload, MetaData.emptyInstance());
        }

        /**
         * Configures a single event with the given {@code payload} and {@code metaData} as part of the "given" state.
         * This event will be published.
         *
         * @param payload  The payload of the event to publish.
         * @param metaData The metadata to attach to the event.
         * @return The current Given instance, for fluent interfacing.
         */
        default Given event(@Nonnull Object payload, @Nonnull Map<String, String> metaData) {
            return event(payload, MetaData.from(metaData));
        }

        /**
         * Configures a single event with the given {@code payload} and {@code metaData} as part of the "given" state.
         * This event will be published.
         *
         * @param payload  The payload of the event to publish.
         * @param metaData The metadata to attach to the event.
         * @return The current Given instance, for fluent interfacing.
         */
        Given event(@Nonnull Object payload, @Nonnull MetaData metaData);

        /**
         * Configures the given {@code messages} as events in the "given" state. These events will be published in the
         * order they are provided.
         * <p>
         * All the {@code messages} will be processed within a single Unit of Work, meaning their processing won't be
         * affected by changes made by earlier messages passed to this method.
         *
         * @param messages The event messages to publish.
         * @return The current Given instance, for fluent interfacing.
         */
        Given events(@Nonnull EventMessage... messages);

        /**
         * Configures the given {@code events} as events in the "given" state. These events will be published in the
         * order they are provided.
         * <p>
         * All the {@code messages} will be processed within a single Unit of Work, meaning their processing won't be
         * affected by changes made by earlier messages passed to this method.
         *
         * @param events The lists of events to publish.
         * @return The current Given instance, for fluent interfacing.
         */
        default Given events(@Nonnull Object... events) {
            return events(Arrays.stream(events).toList());
        }

        /**
         * Configures the given {@code events} as events in the "given" state. These events will be published in the
         * order they are provided.
         * <p>
         * All the {@code messages} will be processed within a single Unit of Work, meaning their processing won't be
         * affected by changes made by earlier messages passed to this method.
         *
         * @param events The lists of events to publish.
         * @return The current Given instance, for fluent interfacing.
         */
        Given events(@Nonnull List<?> events);

        /**
         * Configures a single command with the given {@code payload} as part of the "given" state. This command will be
         * dispatched to corresponding command handlers.
         *
         * @param payload The payload of the command to dispatch.
         * @return The current Given instance, for fluent interfacing.
         */
        default Given command(@Nonnull Object payload) {
            return command(payload, MetaData.emptyInstance());
        }

        /**
         * Configures a single command with the given {@code payload} and {@code metaData} as part of the "given" state.
         * This command will be dispatched to corresponding command handlers.
         *
         * @param payload  The payload of the command to dispatch.
         * @param metaData The metadata to attach to the command.
         * @return The current Given instance, for fluent interfacing.
         */
        default Given command(@Nonnull Object payload, @Nonnull Map<String, String> metaData) {
            return command(payload, MetaData.from(metaData));
        }

        /**
         * Configures a single command with the given {@code payload} and {@code metaData} as part of the "given" state.
         * This command will be dispatched to corresponding command handlers.
         *
         * @param payload  The payload of the command to dispatch.
         * @param metaData The metadata to attach to the command.
         * @return The current Given instance, for fluent interfacing.
         */
        Given command(@Nonnull Object payload, @Nonnull MetaData metaData);

        /**
         * Configures the given {@code messages} as commands in the "given" state.
         * <p>
         * Each message will be processed in a dedicated Unit of Work, meaning that the processing of a message will be
         * affected by the state changes made by the processing of previous messages. This behavior is in contrast to
         * the {@link Given#events} method, where all messages are processed within a single Unit of Work.
         *
         * @param messages The command messages to dispatch.
         * @return The current Given instance, for fluent interfacing.
         */
        Given commands(@Nonnull CommandMessage... messages);

        /**
         * Configures the given {@code commands} as commands in the "given" state. These commands will be dispatched in
         * the order they are provided in the same Unit of Work.
         * <p>
         * Each message will be processed in a dedicated Unit of Work, meaning that the processing of a message will be
         * affected by the state changes made by the processing of previous messages. This behavior is in contrast to
         * the {@link Given#events} method, where all messages are processed within a single Unit of Work.
         *
         * @param commands The command messages to dispatch.
         * @return The current Given instance, for fluent interfacing.
         */
        default Given commands(@Nonnull Object... commands) {
            return commands(Arrays.stream(commands).toList());
        }

        /**
         * Configures the given {@code commands} as commands in the "given" state. These commands will be dispatched in
         * the order they are provided in the same Unit of Work.
         * <p>
         * Each message will be processed in a dedicated Unit of Work, meaning that the processing of a message will be
         * affected by the state changes made by the processing of previous messages. This behavior is in contrast to
         * the {@link Given#events} method, where all messages are processed within a single Unit of Work.
         *
         * @param commands The command messages to dispatch.
         * @return The current Given instance, for fluent interfacing.
         */
        Given commands(@Nonnull List<?> commands);

        /**
         * Transitions to the When phase to execute the test action.
         *
         * @return A {@link When} instance that allows executing the test.
         */
        When when();
    }

    /**
     * Interface describing the operations available in the When phase of the test fixture execution. This phase is used
     * to execute the actual action being tested, typically a command.
     * <p>
     * Each operation in the phase (such as dispatching a command) is executed in its own separate Unit of Work which is
     * committed immediately after execution. This allows for building up the initial state incrementally with each
     * operation being processed independently.
     */
    interface When {

        interface Command {

            /**
             * Transitions to the Then phase to validate the results of the test.
             *
             * @return A {@link Then} instance that allows validating the test results.
             */
            Then.Command then();
        }

        interface Event {

            /**
             * Transitions to the Then phase to validate the results of the test.
             *
             * @return A {@link Then} instance that allows validating the test results.
             */
            Then.Event then();
        }

        /**
         * Dispatches the given {@code payload} command to the appropriate command handler and records all activity for
         * result validation. The command will be dispatched with empty metadata.
         *
         * @param payload The command to execute.
         * @return The current When instance, for fluent interfacing.
         */
        default Command command(@Nonnull Object payload) {
            return command(payload, new HashMap<>());
        }

        /**
         * Dispatches the given {@code payload} command with the provided {@code metaData} to the appropriate command
         * handler and records all activity for result validation.
         *
         * @param payload  The command to execute.
         * @param metaData The metadata to attach to the command.
         * @return The current When instance, for fluent interfacing.
         */
        default Command command(@Nonnull Object payload, @Nonnull Map<String, String> metaData) {
            return command(payload, MetaData.from(metaData));
        }

        /**
         * Dispatches the given {@code payload} command with the provided {@code metaData} to the appropriate command
         * handler and records all activity for result validation.
         *
         * @param payload  The command to execute.
         * @param metaData The metadata to attach to the command.
         * @return The current When instance, for fluent interfacing.
         */
        Command command(@Nonnull Object payload, @Nonnull MetaData metaData);

        /**
         * Publishes the given {@code payload} event with the provided {@code metaData} to the appropriate event handler
         * and records all activity for result validation. The event will be published with empty metadata.
         *
         * @param payload The command to execute.
         * @return The current When instance, for fluent interfacing.
         */
        default Event event(@Nonnull Object payload) {
            return event(payload, MetaData.emptyInstance());
        }

        /**
         * Publishes the given {@code payload} event with the provided {@code metaData} to the appropriate event handler
         * and records all activity for result validation.
         *
         * @param payload  The event to execute.
         * @param metaData The metadata to attach to the command.
         * @return The current When instance, for fluent interfacing.
         */
        Event event(@Nonnull Object payload, @Nonnull MetaData metaData);

        /**
         * Publishes the given Event Messages to the appropriate event handlers and records all activity for result
         * validation.
         *
         * @param messages The event messages to publish.
         * @return The current When instance, for fluent interfacing.
         */
        Event events(@Nonnull EventMessage... messages);

        /**
         * Publishes the given Event Messages to the appropriate event handlers and records all activity for result
         * validation.
         *
         * @param events The lists of events to publish.
         * @return The current When instance, for fluent interfacing.
         */
        Event events(@Nonnull List<?>... events);
    }

    /**
     * Interface describing the operations available in the Then phase of the test fixture execution. This phase is used
     * to validate the results of the test action executed in the When phase.
     */
    interface Then {

        /**
         * Operations available in the Then phase of the test fixture execution only if command was dispatched during
         * the When phase.
         */
        interface Command extends Message<Command> {

            /**
             * Expect a successful execution of the When phase, regardless of the actual return value.
             *
             * @return The current Then instance, for fluent interfacing.
             */
            Command success();

            /**
             * Invokes the given {@code consumer} of the command result message that has been returned during the When
             * phase, allowing for <b>any</b> form of assertion.
             *
             * @param consumer Consumes the command result. You may place your own assertions here.
             * @return The current Then instance, for fluent interfacing.
             */
            Command resultMessageSatisfies(@Nonnull Consumer<? super CommandResultMessage<?>> consumer);

            /**
             * Expect the last command handler from the When phase to return the given {@code expectedPayload} after
             * execution. The actual and expected values are compared using their equals methods.
             * <p>
             * Only take commands into account that were dispatched explicitly with the {@link When#command}. Hence, do
             * not take into accounts commands dispatched as side effects of the message handlers.
             *
             * @param expectedPayload The expected result message payload of the command execution.
             * @return The current Then, for fluent interfacing.
             */
            Command resultMessagePayload(@Nonnull Object expectedPayload);

            /**
             * Invokes the given {@code consumer} of the command result payload that has been returned during the When
             * phase, allowing for <b>any</b> form of assertion.
             *
             * @param consumer Consumes the command result payload. You may place your own assertions here.
             * @return The current Then instance, for fluent interfacing.
             */
            Command resultMessagePayloadSatisfies(@Nonnull Consumer<Object> consumer);
        }

        /**
         * Operations available in the Then phase of the test fixture execution only if event was published during the
         * When phase.
         */
        interface Event extends Message<Event> {

            /**
             * Expect a successful execution of the When phase, no exception raised while handling the event.
             *
             * @return The current Then instance, for fluent interfacing.
             */
            Event success();
        }

        /**
         * Interface describing the operations available in the Then phase of the test fixture execution. It's possible
         * to assert published messages from the When phase.
         *
         * @param <T> The type of the current Then instance, for fluent interfacing. The type depends on the operation
         *            which was triggered in the When phase.
         */
        interface Message<T extends Message<T>> {

            /**
             * Expect the given set of events to have been published during the {@link When} phase.
             * <p>
             * All events are compared for equality using a shallow equals comparison on all the fields of the events.
             * This means that all assigned values on the events' fields should have a proper equals implementation.
             * <p>
             * Note that the {@link EventMessage#identifier()} is ignored in the comparison.
             *
             * @param expectedEvents The expected events, in the exact order they are expected to be published.
             * @return The current Then instance, for fluent interfacing.
             */
            T events(@Nonnull Object... expectedEvents);

            /**
             * Expect the given set of event messages to have been published during the {@link When} phase.
             * <p>
             * All events are compared for equality using a shallow equals comparison on all the fields of the events.
             * This means that all assigned values on the events' fields should have a proper equals implementation.
             * Additionally, the metadata will be compared too.
             * <p>
             * Note that the {@link EventMessage#identifier()} is ignored in the comparison.
             *
             * @param expectedEvents The expected event messages, in the exact order they are expected to be published.
             * @return The current Then instance, for fluent interfacing.
             */
            T events(@Nonnull EventMessage... expectedEvents);

            /**
             * Invokes the given {@code consumer} of the set of event messages that have been published during the When
             * phase, allowing for <b>any</b> form of assertion.
             *
             * @param consumer Consumes the published events. You may place your own assertions here.
             * @return The current Then instance, for fluent interfacing.
             */
            T eventsSatisfy(@Nonnull Consumer<List<EventMessage>> consumer);

            /**
             * Allow to check if the set of event messages which have been published during the When phase match given
             * {@code predicate}.
             *
             * @param predicate The predicate to check the dispatched events against.
             * @return The current Then instance, for fluent interfacing.
             */
            T eventsMatch(@Nonnull Predicate<List<EventMessage>> predicate);

            /**
             * Expect no events to have been published during the {@link When} phase.
             *
             * @return The current Then instance, for fluent interfacing.
             */
            default T noEvents() {
                return events();
            }

            /**
             * Expect the given set of commands to have been dispatched during the When phase.
             * <p>
             * All commands are compared for equality using a shallow equals comparison on all the fields of the
             * commands. This means that all assigned values on the commands' fields should have a proper equals
             * implementation.
             *
             * @param expectedCommands The expected commands, in the exact order they are expected to be dispatched.
             * @return The current Then instance, for fluent interfacing.
             */
            T commands(@Nonnull Object... expectedCommands);

            /**
             * Expect the given set of command messages to have been dispatched during the When phase.
             * <p>
             * All commands are compared for equality using a shallow equals comparison on all the fields of the
             * commands. This means that all assigned values on the commands' fields should have a proper equals
             * implementation. Additionally, the metadata will be compared too.
             *
             * @param expectedCommands The expected command messages, in the exact order they are expected to be
             *                         dispatched.
             * @return The current Then instance, for fluent interfacing.
             */
            T commands(@Nonnull CommandMessage... expectedCommands);

            /**
             * Invokes the given {@code consumer} of the set of command messages that have been dispatched during the
             * When phase, allowing for <b>any</b> form of assertion. *
             *
             * @param consumer Consumes the dispatched commands. You may place your own assertions here.
             * @return The current Then instance, for fluent interfacing.
             */
            T commandsSatisfy(@Nonnull Consumer<List<CommandMessage>> consumer);

            /**
             * Allow to check if the set of command messages which have been dispatched during the When phase match
             * given predicate.
             *
             * @param predicate The predicate to check the dispatched commands against.
             * @return The current Then instance, for fluent interfacing.
             */
            T commandsMatch(@Nonnull Predicate<List<CommandMessage>> predicate);

            /**
             * Expect no command messages to have been dispatched during the When phase.
             *
             * @return The current Then instance, for fluent interfacing.
             */
            T noCommands();

            /**
             * Returns to the setup phase to continue with additional test scenarios. This allows for chaining multiple
             * test scenarios within a single test method. The same configuration from the original fixture is reused,
             * so all components are shared among the invocations.
             * <p>
             * Example usage:
             * <pre>
             * {@code
             * fixture.given()
             *        .event(new AccountCreatedEvent("account-1"))
             *        .when()
             *        .command(new WithdrawMoneyCommand("account-1", 50.00))
             *        .then()
             *        .events(new MoneyWithdrawnEvent("account-1", 50.00))
             *        .success()
             *        .and()  // Return to setup phase with same configuration
             *        .given() // Start a new scenario
             *        .event(new AccountCreatedEvent("account-2"))
             *        .when()
             *        .command(new WithdrawMoneyCommand("account-2", 30.00))
             *        .then()
             *        .events(new MoneyWithdrawnEvent("account-2", 30.00));
             * }
             * </pre>
             *
             * @return a {@link Setup} instance that allows configuring a new test scenario.
             */

            /**
             * Expect the given {@code expectedException} to occur during the When phase execution. The actual exception
             * should be exactly of that type, subclasses are not accepted.
             * <p>
             * Only take messages into account that were published explicitly with the {@link When#command} or
             * {@link When#event}. Hence, do not take into accounts messages published as side effects of other message
             * handlers present in the configuration.
             *
             * @param type The type of exception expected from the When phase execution.
             * @return The current Then instance, for fluent interfacing.
             */
            T exception(@Nonnull Class<? extends Throwable> type);

            /**
             * Expect the exception with given {@code type} and {@code message} to occur during the When phase
             * execution. The actual exception should be exactly of that type, subclasses are not accepted.
             * <p>
             * Only take messages into account that were published explicitly with the {@link When#command} or
             * {@link When#event}. Hence, do not take into accounts messages published as side effects of other message
             * handlers present in the configuration.
             *
             * @param type    The type of exception expected from the When phase execution.
             * @param message The message of the exception expected from the When phase execution.
             * @return The current Then instance, for fluent interfacing.
             */
            T exception(@Nonnull Class<? extends Throwable> type, @Nonnull String message);

            /**
             * Invokes the given {@code consumer} of the exception that has been throw during the When phase, allowing
             * for <b>any</b> form of assertion.
             *
             * @param consumer Consumes the thrown exception. You may place your own assertions here.
             * @return The current Then instance, for fluent interfacing.
             */
            T exceptionSatisfies(@Nonnull Consumer<Throwable> consumer);

            Setup and();

            /**
             * Stops the fixture, releasing any active resources, like registered handlers or pending event processing
             * tasks.
             */
            default void stop() {
                and().stop();
            }
        }
    }
}
