/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.test.aggregate;

import org.axonframework.eventhandling.EventMessage;
import org.hamcrest.Matcher;

import java.util.List;

/**
 * Interface describing the operations available on the "validate result" (a.k.a. "then") stage of the test execution.
 * The underlying fixture expects a test to have been executed succesfully using a {@link
 * TestExecutor}.
 * <p>
 * There are several things to validate:<ul><li>the published events,<li>the stored events, and<li>the command
 * handler's
 * execution result, which is one of <ul><li>a regular return value,<li>a {@code void} return value, or<li>an
 * exception.</ul></ul>
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface ResultValidator {

    /**
     * Expect the given set of events to have been published.
     * <p>
     * All events are compared for equality using a shallow equals comparison on all the fields of the events. This
     * means that all assigned values on the events' fields should have a proper equals implementation.
     * <p>
     * Note that the event identifier is ignored in the comparison.
     *
     * @param expectedEvents The expected events, in the exact order they are expected to be dispatched and stored.
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectEvents(Object... expectedEvents);

    /**
     * Expect no events to have been published from the command.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    default ResultValidator expectNoEvents() {
        return expectEvents();
    }

    /**
     * Expect the published events to match the given {@code matcher}.
     * <p>
     * Note: if no events were published, the matcher receives an empty List.
     *
     * @param matcher The matcher to match with the actually published events
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectEventsMatching(Matcher<? extends List<? super EventMessage<?>>> matcher);

    /**
     * Expect the command handler to return the given {@code expectedReturnValue} after execution. The actual and
     * expected values are compared using their equals methods.
     *
     * @param expectedReturnValue The expected return value of the command execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectReturnValue(Object expectedReturnValue);

    /**
     * Expect the command handler to return a value that matches the given {@code matcher} after execution.
     *
     * @param matcher The matcher to verify the actual return value against
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectReturnValueMatching(Matcher<?> matcher);

    /**
     * Expect the given {@code expectedException} to occur during command handler execution. The actual exception
     * should be exactly of that type, subclasses are not accepted.
     *
     * @param expectedException The type of exception expected from the command handler execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectException(Class<? extends Throwable> expectedException);

    /**
     * Expect an exception to occur during command handler execution that matches with the given {@code matcher}.
     *
     * @param matcher The matcher to validate the actual exception
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectException(Matcher<?> matcher);

    /**
     * Expect a successful execution of the given command handler, regardless of the actual return value.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectSuccessfulHandlerExecution();
}
