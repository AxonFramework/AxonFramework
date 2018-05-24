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

import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.eventhandling.EventMessage;
import org.hamcrest.Matcher;

import java.time.Duration;
import java.time.Instant;
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
     * Expect an exception message to occur during command handler execution that matches with the given {@code
     * matcher}.
     *
     * @param matcher The matcher to validate the actual exception message
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectExceptionMessage(Matcher<?> matcher);

    /**
     * Expect the given {@code exceptionMessage} to occur during command handler execution. The actual exception
     * message should be exactly the same as {@code exceptionMessage}.
     *
     * @param exceptionMessage The exception message expected from the command handler execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectExceptionMessage(String exceptionMessage);

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

    /**
     * Asserts that a deadline scheduled after given {@code duration} matches the given {@code matcher}.
     *
     * @param duration The delay expected before the deadline is met
     * @param matcher  The matcher that must match with the deadline scheduled at the given time
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectScheduledDeadlineMatching(Duration duration, Matcher<? super DeadlineMessage<?>> matcher);

    /**
     * Asserts that a deadline equal to the given {@code deadline} has been scheduled after the given {@code duration}.
     * <p/>
     * Note that the source attribute of the deadline is ignored when comparing deadlines. Deadlines are compared using
     * an "equals" check on all fields in the deadlines.
     *
     * @param duration The time to wait before the deadline should be met
     * @param deadline The expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectScheduledDeadline(Duration duration, Object deadline);

    /**
     * Asserts that a deadline of the given {@code deadlineType} has been scheduled after the given {@code duration}.
     *
     * @param duration     The time to wait before the deadline is met
     * @param deadlineType The type of the expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectScheduledDeadlineOfType(Duration duration, Class<?> deadlineType);

    /**
     * Asserts that a deadline matching the given {@code matcher} has been scheduled at the given {@code
     * scheduledTime}.
     * <p/>
     * If the {@code scheduledTime} is calculated based on the "current time", use the {@link
     * TestExecutor#currentTime()} to get the time to use as "current time".
     *
     * @param scheduledTime The time at which the deadline should be met
     * @param matcher       The matcher defining the deadline expected
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectScheduledDeadlineMatching(Instant scheduledTime, Matcher<? super DeadlineMessage<?>> matcher);

    /**
     * Asserts that a deadline equal to the given {@code deadline} has been scheduled at the given {@code
     * scheduledTime}.
     * <p/>
     * If the {@code scheduledTime} is calculated based on the "current time", use the {@link
     * TestExecutor#currentTime()} to get the time to use as "current time".
     * <p/>
     * Note that the source attribute of the deadline is ignored when comparing deadlines. Deadlines are compared using
     * an "equals" check on all fields in the deadlines.
     *
     * @param scheduledTime The time at which the deadline is scheduled
     * @param deadline      The expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectScheduledDeadline(Instant scheduledTime, Object deadline);

    /**
     * Asserts that a deadline of the given {@code deadlineType} has been scheduled at the given {@code scheduledTime}.
     *
     * @param scheduledTime The time at which the deadline is scheduled
     * @param deadlineType  The type of the expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectScheduledDeadlineOfType(Instant scheduledTime, Class<?> deadlineType);

    /**
     * Asserts that no deadlines are scheduled. This means that either no deadlines were scheduled at all, all schedules
     * have been cancelled or all scheduled deadlines have been met already.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectNoScheduledDeadlines();

    /**
     * Asserts that deadlines match given {@code matcher} have been met (which have passed in time) on this aggregate.
     *
     * @param matcher The matcher that defines the expected list of deadlines
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectDeadlinesMetMatching(Matcher<? extends List<? super DeadlineMessage<?>>> matcher);

    /**
     * Asserts that given {@code expected} deadlines have been met (which have passed in time). Deadlines are compared
     * comparing their type and fields using "equals".
     *
     * @param expected The sequence of deadlines expected to be met
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectDeadlinesMet(Object... expected);
}
