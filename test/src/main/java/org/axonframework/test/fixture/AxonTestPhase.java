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

public interface AxonTestPhase {

    interface Setup {

        AxonTestPhase.Given given();

        default AxonTestPhase.Given given(Consumer<AxonTestPhase.Given> onGiven) {
            var given = given();
            onGiven.accept(given);
            return given;
        }

        When when();

        default When when(Consumer<AxonTestPhase.When> onWhen) {
            var when = when();
            onWhen.accept(when);
            return when;
        }
    }

    interface Given {

        Given noPriorActivity();

        default Given event(Object payload) {
            return event(payload, MetaData.emptyInstance());
        }

        default Given event(Object payload, Map<String, ?> metaData) {
            return event(payload, MetaData.from(metaData));
        }

        Given event(Object payload, MetaData metaData);

        Given events(EventMessage<?>... messages);

        Given events(List<?>... events);

        default Given command(Object payload) {
            return command(payload, MetaData.emptyInstance());
        }

        default Given command(Object payload, Map<String, ?> metaData) {
            return command(payload, MetaData.from(metaData));
        }

        Given command(Object payload, MetaData metaData);

        Given commands(CommandMessage<?>... messages);

        When when();

        default When when(Consumer<AxonTestPhase.When> onWhen) {
            var when = when();
            onWhen.accept(when);
            return when;
        }
    }

    interface When {

        default When command(Object payload) {
            return command(payload, new HashMap<>());
        }

        default When command(Object payload, Map<String, ?> metaData) {
            return command(payload, MetaData.from(metaData));
        }

        When command(Object payload, MetaData metaData);

        Then then();

        default Then then(Consumer<AxonTestPhase.Then> onThen) {
            var then = then();
            onThen.accept(then);
            return then;
        }
    }

    interface Then {

        /**
         * Expect the given set of events to have been published during the {@link When} phase.
         * <p>
         * All events are compared for equality using a shallow equals comparison on all the fields of the events. This
         * means that all assigned values on the events' fields should have a proper equals implementation.
         * <p>
         * Note that the event identifier is ignored in the comparison.
         *
         * @param expectedEvents The expected events, in the exact order they are expected to be published
         * @return the current Then instance, for fluent interfacing
         */
        Then events(Object... expectedEvents);

        /**
         * Expect the given set of event messages to have been published during the {@link When} phase.
         * <p>
         * All events are compared for equality using a shallow equals comparison on all the fields of the events. This
         * means that all assigned values on the events' fields should have a proper equals implementation.
         * Additionally, the metadata will be compared too.
         * <p>
         * Note that the event identifier is ignored in the comparison.
         *
         * @param expectedEvents The expected event messages, in the exact order they are expected to be published.
         * @return the current Then instance, for fluent interfacing.
         */
        Then events(EventMessage<?>... expectedEvents);

        /**
         * Expect the published events during the {@link When} phase to match the given {@code matcher}.
         * <p>
         * Note: if no events were published, the matcher receives an empty List.
         *
         * @param matcher The matcher to match with the actually published events.
         * @return the current Then instance, for fluent interfacing.
         */
        Then events(Matcher<? extends List<? super EventMessage<?>>> matcher);

        /**
         * Expect no events to have been published during the {@link When} phase.
         *
         * @return the current Then instance, for fluent interfacing.
         */
        default Then noEvents() {
            return events();
        }

        /**
         * Expect a successful execution of the When phase, regardless of the actual return value.
         *
         * @return the current Then instance, for fluent interfacing.
         */
        Then success();

        /**
         * Expect the last command handler from the When phase to return a result message that matches the given
         * {@code matcher}.
         *
         * @param matcher The matcher to verify the actual result message against.
         * @return the current Then instance, for fluent interfacing.
         */
        Then resultMessage(Matcher<? super CommandResultMessage<?>> matcher);

        /**
         * Expect the last command handler from the When phase to return the given {@code expectedPayload} after execution. The actual and
         * expected values are compared using their equals methods.
         *
         * @param expectedPayload The expected result message payload of the command execution.
         * @return the current Then, for fluent interfacing.
         */
        Then resultMessagePayload(Object expectedPayload);

        /**
         * Expect the last command handler from the When phase to return a payload that matches the given {@code matcher} after execution.
         *
         * @param matcher The matcher to verify the actual return value against.
         * @return the current Then instance, for fluent interfacing.
         */
        Then resultMessagePayloadMatching(Matcher<?> matcher);

        /**
         * Expect the given {@code expectedException} to occur during the When phase execution.
         * The actual exception should be exactly of that type, subclasses are not accepted.
         *
         * @param expectedException The type of exception expected from the When phase execution.
         * @return the current Then instance, for fluent interfacing.
         */
        Then exception(Class<? extends Throwable> expectedException);

        /**
         * Expect an exception to occur during the When phase that matches with the given {@code matcher}.
         *
         * @param matcher The matcher to validate the actual exception.
         * @return the current Then instance, for fluent interfacing.
         */
        Then exception(Matcher<?> matcher);

        /**
         * Expect the given set of commands to have been dispatched during the "when" phase.
         * <p>
         * All commands are compared for equality using a shallow equals comparison on all the fields of the commands.
         * This means that all assigned values on the commands' fields should have a proper equals implementation.
         *
         * @param expectedCommands The expected commands, in the exact order they are expected to be dispatched.
         * @return the current Then instance, for fluent interfacing.
         */
        Then commands(Object... expectedCommands);

        /**
         * Expect the given set of command messages to have been dispatched during the "when" phase.
         * <p>
         * All commands are compared for equality using a shallow equals comparison on all the fields of the commands.
         * This means that all assigned values on the commands' fields should have a proper equals implementation.
         * Additionally, the metadata will be compared too.
         *
         * @param expectedCommands The expected command messages, in the exact order they are expected to be dispatched.
         * @return the current Then instance, for fluent interfacing.
         */
        Then commands(CommandMessage<?>... expectedCommands);

        /**
         * Returns to the setup phase to continue with additional test scenarios. This allows for chaining multiple test
         * scenarios within a single test method.
         *
         * @return a {@link Setup} instance that allows configuring a new test scenario.
         */
        Setup and();
    }
}
