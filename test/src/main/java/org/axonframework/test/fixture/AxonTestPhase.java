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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;
import org.hamcrest.Matcher;

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
 * This interface defines the four primary phases of the test:
 * <ul>
 *   <li>{@link Setup} - Initial configuration of the test fixture</li>
 *   <li>{@link Given} - Defining the initial state of the system before testing by events and commands</li>
 *   <li>{@link When} - Executing commands to test</li>
 *   <li>{@link Then} - Validating the results of the test</li>
 * </ul>
 * <p>
 * The test fixture manages Unit of Work instances during test execution, automatically committing
 * as appropriate. During the "given" phase, each operation (like event or command) is executed in its own separate
 * Unit of Work that is committed immediately after execution. In the "when" phase, a single Unit of Work is started
 * and committed after the command is executed. The "then" phase operates outside of a Unit of Work as it only
 * validates the results.
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
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface AxonTestPhase {

    /**
     * Interface describing the initial setup phase of a test fixture. This phase allows configuring the test fixture
     * before starting the test execution.
     * <p>
     * From this phase, you can transition to either the "given" phase to define initial state, or directly to the
     * "when" phase if no prior state is needed.
     */
    interface Setup {

        /**
         * Transition to the "given" phase to define the initial state of the system before testing.
         *
         * @return a {@link Given} instance that allows defining the initial state.
         */
        AxonTestPhase.Given given();

        /**
         * Transition directly to the "when" phase, skipping the "given" phase, which implies no prior state.
         *
         * @return a {@link When} instance that allows executing the test.
         */
        When when();
    }

    /**
     * Interface describing the operations available in the "given" phase of the test fixture execution. This phase is
     * used to define the initial state of the system before executing the test action.
     * <p>
     * Each operation in the "given" phase (such as applying an event or dispatching a command) is executed in its own
     * separate Unit of Work which is committed immediately after execution. This allows for building up the initial
     * state incrementally with each operation being processed independently.
     */
    interface Given {

        /**
         * Indicates that no relevant activities like commands or events have occurred in the past. This also means that
         * no previous state is present in the system.
         *
         * @return the current Given instance, for fluent interfacing
         */
        Given noPriorActivity();

        /**
         * Configures a single event with the given {@code payload} as part of the "given" state. This event will be
         * published with empty metadata.
         *
         * @param payload The payload of the event to publish.
         * @return the current Given instance, for fluent interfacing.
         */
        default Given event(Object payload) {
            return event(payload, MetaData.emptyInstance());
        }

        /**
         * Configures a single event with the given {@code payload} and {@code metaData} as part of the "given" state.
         * This event will be published.
         *
         * @param payload  The payload of the event to publish.
         * @param metaData The metadata to attach to the event.
         * @return the current Given instance, for fluent interfacing.
         */
        default Given event(Object payload, Map<String, ?> metaData) {
            return event(payload, MetaData.from(metaData));
        }

        /**
         * Configures a single event with the given {@code payload} and {@code metaData} as part of the "given" state.
         * This event will be published.
         *
         * @param payload  The payload of the event to publish.
         * @param metaData The metadata to attach to the event.
         * @return the current Given instance, for fluent interfacing.
         */
        Given event(Object payload, MetaData metaData);

        /**
         * Configures the given {@code messages} as events in the "given" state. These events will be published in the
         * order they are provided.
         *
         * @param messages The event messages to publish.
         * @return the current Given instance, for fluent interfacing.
         */
        Given events(EventMessage<?>... messages);

        /**
         * Configures the given {@code events} as events in the "given" state. These events will be published in the
         * order they are provided.
         *
         * @param events The lists of events to publish.
         * @return the current Given instance, for fluent interfacing.
         */
        Given events(List<?>... events);

        /**
         * Configures a single command with the given {@code payload} as part of the "given" state. This command will be
         * dispatched to corresponding command handlers.
         *
         * @param payload The payload of the command to dispatch.
         * @return the current Given instance, for fluent interfacing.
         */
        default Given command(Object payload) {
            return command(payload, MetaData.emptyInstance());
        }

        /**
         * Configures a single command with the given {@code payload} and {@code metaData} as part of the "given" state.
         * This command will be dispatched to corresponding command handlers.
         *
         * @param payload  The payload of the command to dispatch.
         * @param metaData The metadata to attach to the command.
         * @return the current Given instance, for fluent interfacing.
         */
        default Given command(Object payload, Map<String, ?> metaData) {
            return command(payload, MetaData.from(metaData));
        }

        /**
         * Configures a single command with the given {@code payload} and {@code metaData} as part of the "given" state.
         * This command will be dispatched to corresponding command handlers.
         *
         * @param payload  The payload of the command to dispatch.
         * @param metaData The metadata to attach to the command.
         * @return the current Given instance, for fluent interfacing.
         */
        Given command(Object payload, MetaData metaData);

        /**
         * Configures the given {@code messages} as commands in the "given" state. These commands will be dispatched in
         * the order they are provided in the same Unit of Work.
         *
         * @param messages The command messages to dispatch.
         * @return the current Given instance, for fluent interfacing.
         */
        Given commands(CommandMessage<?>... messages);

        /**
         * Transitions to the "when" phase to execute the test action. This method completes the "given" phase,
         * committing any Unit of Work that was started during this phase.
         *
         * @return a {@link When} instance that allows executing the test.
         */
        When when();
    }

    /**
     * Interface describing the operations available in the "when" phase of the test fixture execution. This phase is
     * used to execute the actual action being tested, typically a command.
     * <p>
     * Each operation in the phase (such as dispatching a command) is executed in its own separate Unit of Work which is
     * committed immediately after execution. This allows for building up the initial state incrementally with each
     * operation being processed independently.
     */
    interface When {

        interface CommandWhen {

            /**
             * Transitions to the "then" phase to validate the results of the test. This method completes the "when"
             * phase, committing any Unit of Work that was started during this phase.
             *
             * @return a {@link Then} instance that allows validating the test results.
             */
            Then.CommandThen then();
        }

        interface EventWhen {

            /**
             * Transitions to the "then" phase to validate the results of the test. This method completes the "when"
             * phase, committing any Unit of Work that was started during this phase.
             *
             * @return a {@link Then} instance that allows validating the test results.
             */
            Then.EventThen then();
        }

        /**
         * Dispatches the given {@code payload} command to the appropriate command handler and records all activity for
         * result validation. The command will be dispatched with empty metadata.
         *
         * @param payload The command to execute.
         * @return the current When instance, for fluent interfacing.
         */
        default CommandWhen command(Object payload) {
            return command(payload, new HashMap<>());
        }

        /**
         * Dispatches the given {@code payload} command with the provided {@code metaData} to the appropriate command
         * handler and records all activity for result validation.
         *
         * @param payload  The command to execute.
         * @param metaData The metadata to attach to the command.
         * @return the current When instance, for fluent interfacing.
         */
        default CommandWhen command(Object payload, Map<String, ?> metaData) {
            return command(payload, MetaData.from(metaData));
        }

        /**
         * Dispatches the given {@code payload} command with the provided {@code metaData} to the appropriate command
         * handler and records all activity for result validation.
         *
         * @param payload  The command to execute.
         * @param metaData The metadata to attach to the command.
         * @return the current When instance, for fluent interfacing.
         */
        CommandWhen command(Object payload, MetaData metaData);

        /**
         * Publishes the given {@code payload} event with the provided {@code metaData} to the appropriate event handler
         * and records all activity for result validation. The event will be published with empty metadata.
         *
         * @param payload The command to execute.
         * @return the current When instance, for fluent interfacing.
         */
        default EventWhen event(Object payload) {
            return event(payload, MetaData.emptyInstance());
        }

        /**
         * Publishes the given {@code payload} event with the provided {@code metaData} to the appropriate event handler
         * and records all activity for result validation.
         *
         * @param payload  The event to execute.
         * @param metaData The metadata to attach to the command.
         * @return the current When instance, for fluent interfacing.
         */
        EventWhen event(Object payload, MetaData metaData);

        /**
         * Publishes the given Event Messages to the appropriate event handlers and records all activity for result
         * validation.
         *
         * @param messages The event messages to publish.
         * @return the current When instance, for fluent interfacing.
         */
        EventWhen events(EventMessage<?>... messages);

        /**
         * Publishes the given Event Messages to the appropriate event handlers and records all activity for result
         * validation.
         *
         * @param events The lists of events to publish.
         * @return the current When instance, for fluent interfacing.
         */
        EventWhen events(List<?>... events);
    }

    /**
     * Interface describing the operations available in the "then" phase of the test fixture execution. This phase is
     * used to validate the results of the test action executed in the "when" phase.
     * <p>
     * The "then" phase operates outside of a Unit of Work as it only validates the results.
     */
    interface Then {

        interface CommandThen extends MessageThen<CommandThen> {

            /**
             * Expect a successful execution of the When phase, regardless of the actual return value.
             *
             * @return the current Then instance, for fluent interfacing.
             */
            CommandThen success();

            /**
             * Expect the last command handler from the When phase to return a result message that matches the given
             * {@code matcher}. Take into account only commands executed explicitly with the {@link When#command}. Do
             * not take into accounts commands published as side effects of the message handlers.
             *
             * @param matcher The matcher to verify the actual result message against.
             * @return the current Then instance, for fluent interfacing.
             */
            CommandThen resultMessage(Matcher<? super CommandResultMessage<?>> matcher);

            /**
             * Expect the last command handler from the When phase to return the given {@code expectedPayload} after
             * execution. The actual and expected values are compared using their equals methods. Take into account only
             * commands executed explicitly with the {@link When#command}. Do not take into accounts commands published
             * as side effects of the message handlers.
             *
             * @param expectedPayload The expected result message payload of the command execution.
             * @return the current Then, for fluent interfacing.
             */
            CommandThen resultMessagePayload(Object expectedPayload);

            /**
             * Expect the last command handler from the When phase to return a payload that matches the given
             * {@code matcher} after execution. Take into account only commands executed explicitly with the
             * {@link When#command}. Do not take into accounts commands published as side effects of the message
             * handlers.
             *
             * @param matcher The matcher to verify the actual return value against.
             * @return the current Then instance, for fluent interfacing.
             */
            CommandThen resultMessagePayloadMatching(Matcher<?> matcher);

            /**
             * Expect the given {@code expectedException} to occur during the When phase execution. The actual exception
             * should be exactly of that type, subclasses are not accepted. Take into account only commands executed
             * explicitly with the {@link When#command}. Do not take into accounts commands published as side effects of
             * the message handlers.
             *
             * @param expectedException The type of exception expected from the When phase execution.
             * @return the current Then instance, for fluent interfacing.
             */
            CommandThen exception(Class<? extends Throwable> expectedException);

            /**
             * Expect an exception to occur during the When phase that matches with the given {@code matcher}. Take into
             * account only commands executed explicitly with the {@link When#command}. Do not take into accounts
             * commands published as side effects of the message handlers.
             *
             * @param matcher The matcher to validate the actual exception.
             * @return the current Then instance, for fluent interfacing.
             */
            CommandThen exception(Matcher<?> matcher);
        }

        interface EventThen extends MessageThen<EventThen> {

        }

        interface MessageThen<T extends MessageThen<T>> {

            /**
             * Expect the given set of events to have been published during the {@link When} phase.
             * <p>
             * All events are compared for equality using a shallow equals comparison on all the fields of the events.
             * This means that all assigned values on the events' fields should have a proper equals implementation.
             * <p>
             * Note that the event identifier is ignored in the comparison.
             *
             * @param expectedEvents The expected events, in the exact order they are expected to be published.
             * @return the current Then instance, for fluent interfacing.
             */
            T events(Object... expectedEvents);

            /**
             * Expect the given set of event messages to have been published during the {@link When} phase.
             * <p>
             * All events are compared for equality using a shallow equals comparison on all the fields of the events.
             * This means that all assigned values on the events' fields should have a proper equals implementation.
             * Additionally, the metadata will be compared too.
             * <p>
             * Note that the event identifier is ignored in the comparison.
             *
             * @param expectedEvents The expected event messages, in the exact order they are expected to be published.
             * @return the current Then instance, for fluent interfacing.
             */
            T events(EventMessage<?>... expectedEvents);

            /**
             * Expect the published events during the {@link When} phase to match the given {@code matcher}.
             * <p>
             * Note: if no events were published, the matcher receives an empty List.
             *
             * @param matcher The matcher to match with the actually published events.
             * @return the current Then instance, for fluent interfacing.
             */
            T events(Matcher<? extends List<? super EventMessage<?>>> matcher);

            T events(Consumer<List<? super EventMessage<?>>> consumer);

            T eventsMatch(Predicate<List<? super EventMessage<?>>> predicate);

            /**
             * Expect no events to have been published during the {@link When} phase.
             *
             * @return the current Then instance, for fluent interfacing.
             */
            default T noEvents() {
                return events();
            }

            /**
             * Expect the given set of commands to have been dispatched during the "when" phase.
             * <p>
             * All commands are compared for equality using a shallow equals comparison on all the fields of the
             * commands. This means that all assigned values on the commands' fields should have a proper equals
             * implementation.
             *
             * @param expectedCommands The expected commands, in the exact order they are expected to be dispatched.
             * @return the current Then instance, for fluent interfacing.
             */
            T commands(Object... expectedCommands);

            /**
             * Expect the given set of command messages to have been dispatched during the "when" phase.
             * <p>
             * All commands are compared for equality using a shallow equals comparison on all the fields of the
             * commands. This means that all assigned values on the commands' fields should have a proper equals
             * implementation. Additionally, the metadata will be compared too.
             *
             * @param expectedCommands The expected command messages, in the exact order they are expected to be
             *                         dispatched.
             * @return the current Then instance, for fluent interfacing.
             */
            T commands(CommandMessage<?>... expectedCommands);

            T commands(Consumer<List<? super CommandMessage<?>>> consumer);

            T commandsMatch(Predicate<List<? super CommandMessage<?>>> predicate);

            /**
             * Expect the given set of command messages to have been dispatched during the "when" phase. Only commands
             * as a result of the event in the "when" phase of ths fixture are recorded.
             *
             * @return the current Then instance, for fluent interfacing.
             */
            T noCommands();

            /**
             * Returns to the setup phase to continue with additional test scenarios. This allows for chaining multiple
             * test scenarios within a single test method.
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
             *        .and()  // Return to setup phase
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
            Setup and();

            T self();
        }
    }
}
