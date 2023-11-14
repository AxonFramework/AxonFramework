/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.eventhandling.EventMessage;
import org.hamcrest.Matcher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interface describing the operations available on the "validate result" (a.k.a. "then") stage of the test execution.
 * The underlying fixture expects a test to have been executed successfully using a {@link TestExecutor}.
 * <p>
 * There are several things to validate:<ul><li>the published events,<li>the stored events, and<li>the command handler's
 * execution result, which is one of <ul><li>a regular return value,<li>a {@code void} return value, or<li>an
 * exception.</ul></ul>
 *
 * @param <T> The type of Aggregate under test
 * @author Allard Buijze
 * @since 0.6
 */
public interface ResultValidator<T> {

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
    ResultValidator<T> expectEvents(Object... expectedEvents);

    /**
     * Expect the given set of events to have been published.
     * <p>
     * All events are compared for equality using a shallow equals comparison on all the fields of the events. This
     * means that all assigned values on the events' fields should have a proper equals implementation.<br/>
     * Additionally the metadata will be compared too.
     * <p>
     * Note that the event identifier is ignored in the comparison.
     *
     * @param expectedEvents The expected events, in the exact order they are expected to be dispatched and stored.
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectEvents(EventMessage<?>... expectedEvents);

    /**
     * Expect no events to have been published from the command.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    default ResultValidator<T> expectNoEvents() {
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
    ResultValidator<T> expectEventsMatching(Matcher<? extends List<? super EventMessage<?>>> matcher);

    /**
     * Expect the command handler to return the given {@code expectedPayload} after execution. The actual and expected
     * values are compared using their equals methods.
     *
     * @param expectedPayload The expected result message payload of the command execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectResultMessagePayload(Object expectedPayload);

    /**
     * Expect the command handler to return a payload that matches the given {@code matcher} after execution.
     *
     * @param matcher The matcher to verify the actual return value against
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectResultMessagePayloadMatching(Matcher<?> matcher);

    /**
     * Expect the command handler to return the given {@code expectedResultMessage} after execution. The actual and
     * expected values are compared using their equals methods.
     * <p>
     * Comparison is done on message payload and meta data.
     * </p>
     *
     * @param expectedResultMessage The expected result message of the command execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectResultMessage(CommandResultMessage<?> expectedResultMessage);

    /**
     * Expect the command handler to return a value that matches the given {@code matcher} after execution.
     *
     * @param matcher The matcher to verify the actual result message against
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectResultMessageMatching(Matcher<? super CommandResultMessage<?>> matcher);

    /**
     * Expect an exception message to occur during command handler execution that matches with the given {@code
     * matcher}.
     *
     * @param matcher The matcher to validate the actual exception message
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectExceptionMessage(Matcher<?> matcher);

    /**
     * Expect the given {@code exceptionMessage} to occur during command handler execution. The actual exception message
     * should be exactly the same as {@code exceptionMessage}.
     *
     * @param exceptionMessage The exception message expected from the command handler execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectExceptionMessage(String exceptionMessage);

    /**
     * Expect the given {@code expectedException} to occur during command handler execution. The actual exception should
     * be exactly of that type, subclasses are not accepted.
     *
     * @param expectedException The type of exception expected from the command handler execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectException(Class<? extends Throwable> expectedException);

    /**
     * Expect an exception to occur during command handler execution that matches with the given {@code matcher}.
     *
     * @param matcher The matcher to validate the actual exception
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectException(Matcher<?> matcher);

    /**
     * Expect the given {@code exceptionDetails} to occur during command handler execution. The actual exception details
     * should be exactly the same as {@code exceptionDetails}.
     *
     * @param exceptionDetails The exception details expected from the command handler execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectExceptionDetails(Object exceptionDetails);

    /**
     * Expect the given {@code exceptionDetails} type to occur during command handler execution. The actual exception details
     * should be exactly of that type, subclasses are not accepted.
     *
     * @param exceptionDetails The type of exception details expected from the command handler execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectExceptionDetails(Class<?> exceptionDetails);

    /**
     * Expect exception details to occur during command handler execution that matches with the given {@code
     * matcher}.
     *
     * @param matcher The matcher to validate the actual exception details
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectExceptionDetails(Matcher<?> matcher);

    /**
     * Expect a successful execution of the given command handler, regardless of the actual return value.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectSuccessfulHandlerExecution();

    /**
     * Provides access the the state of the Aggregate as it was stored in the repository, allowing for validation of the
     * state of the aggregate, as it was left in the repository.
     * <p>
     * Note that validating state on Event Sourced Aggregate is highly discouraged. Event Sourced aggregates should
     * measure behavior exclusively by measuring emitted events.
     *
     * @param aggregateStateValidator a consumer that is provided with the aggregate instance, allowing it to make
     *                                assertions based on the aggregate's state.
     * @return the current ResultValidator, for fluent interfacing
     * @throws IllegalStateException when an error in Command execution caused the Unit of Work to be rolled back.
     */
    ResultValidator<T> expectState(Consumer<T> aggregateStateValidator);

    /**
     * Asserts that a deadline scheduled after given {@code duration} matches the given {@code matcher}.
     *
     * @param duration the delay expected before the deadline is met
     * @param matcher  the matcher that must match with the deadline scheduled at the given time
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectScheduledDeadlineMatching(Duration duration, Matcher<? super DeadlineMessage<?>> matcher);

    /**
     * Asserts that a deadline equal to the given {@code deadline} has been scheduled after the given {@code duration}.
     * <p/>
     * Note that the source attribute of the deadline is ignored when comparing deadlines. Deadlines are compared using
     * an "equals" check on all fields in the deadlines.
     *
     * @param duration the time to wait before the deadline should be met
     * @param deadline the expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectScheduledDeadline(Duration duration, Object deadline);

    /**
     * Asserts that a deadline of the given {@code deadlineType} has been scheduled after the given {@code duration}.
     *
     * @param duration     the time to wait before the deadline is met
     * @param deadlineType the type of the expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectScheduledDeadlineOfType(Duration duration, Class<?> deadlineType);

    /**
     * Asserts that a deadline with the given {@code deadlineName} has been scheduled after the given {@code duration}.
     *
     * @param duration     the time to wait before the deadline is met
     * @param deadlineName the name of the expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectScheduledDeadlineWithName(Duration duration, String deadlineName);

    /**
     * Asserts that a deadline matching the given {@code matcher} has been scheduled at the given {@code
     * scheduledTime}.
     * <p/>
     * If the {@code scheduledTime} is calculated based on the "current time", use the {@link
     * TestExecutor#currentTime()} to get the time to use as "current time".
     *
     * @param scheduledTime the time at which the deadline should be met
     * @param matcher       the matcher defining the deadline expected
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectScheduledDeadlineMatching(Instant scheduledTime,
                                                       Matcher<? super DeadlineMessage<?>> matcher);

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
     * @param scheduledTime the time at which the deadline is scheduled
     * @param deadline      the expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectScheduledDeadline(Instant scheduledTime, Object deadline);

    /**
     * Asserts that a deadline of the given {@code deadlineType} has been scheduled at the given {@code scheduledTime}.
     *
     * @param scheduledTime the time at which the deadline is scheduled
     * @param deadlineType  the type of the expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectScheduledDeadlineOfType(Instant scheduledTime, Class<?> deadlineType);

    /**
     * Asserts that a deadline with the given {@code deadlineName} has been scheduled at the given {@code
     * scheduledTime}.
     *
     * @param scheduledTime the time at which the deadline is scheduled
     * @param deadlineName  the name of the expected deadline
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectScheduledDeadlineWithName(Instant scheduledTime, String deadlineName);

    /**
     * Asserts that no deadlines are scheduled. This means that either no deadlines were scheduled at all, all schedules
     * have been cancelled or all scheduled deadlines have been met already.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlines();

    /**
     * Asserts that <b>no</b> deadline matching the given {@code matcher} is scheduled. Can be used to validate if a
     * deadline has never been set or has been canceled.
     *
     * @param matcher the matcher defining the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineMatching(Matcher<? super DeadlineMessage<?>> matcher);

    /**
     * Asserts that <b>no</b> deadline matching the given {@code matcher} should be scheduled after the given {@code
     * durationToScheduledTime}. Can be used to validate if a deadline has never been set or has been canceled at an
     * <b>exact</b> moment in time.
     *
     * @param durationToScheduledTime the time to wait until the trigger point of the deadline which should not be
     *                                scheduled
     * @param matcher                 the matcher defining the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineMatching(Duration durationToScheduledTime,
                                                         Matcher<? super DeadlineMessage<?>> matcher);

    /**
     * Asserts that <b>no</b> deadline equal to the given {@code deadline} has been scheduled after the given {@code
     * durationToScheduledTime}. Can be used to validate if a deadline has never been set or has been canceled at an
     * <b>exact</b> moment in time.
     * <p/>
     * Note that the source attribute of the deadline is ignored when comparing deadlines. Deadlines are compared using
     * an "equals" check on all fields in the deadlines.
     *
     * @param durationToScheduledTime the time to wait until the trigger point of the deadline which should not be
     *                                scheduled
     * @param deadline                the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadline(Duration durationToScheduledTime, Object deadline);

    /**
     * Asserts that <b>no</b> deadline of the given {@code deadlineType} has been scheduled after the given {@code
     * durationToScheduledTime}. Can be used to validate if a deadline has never been set or has been canceled at an
     * <b>exact</b> moment in time.
     *
     * @param durationToScheduledTime the time to wait until the trigger point of the deadline which should not be
     *                                scheduled
     * @param deadlineType            the type of the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineOfType(Duration durationToScheduledTime, Class<?> deadlineType);

    /**
     * Asserts that <b>no</b> deadline with the given {@code deadlineName} has been scheduled after the given {@code
     * durationToScheduledTime}. Can be used to validate if a deadline has never been set or has been canceled at an
     * <b>exact</b> moment in time.
     *
     * @param durationToScheduledTime the time to wait until the trigger point of the deadline which should not be
     *                                scheduled
     * @param deadlineName            the name of the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineWithName(Duration durationToScheduledTime, String deadlineName);

    /**
     * Asserts that <b>no</b> deadline matching the given {@code matcher} has been scheduled at the given {@code
     * scheduledTime}. Can be used to validate if a deadline has never been set or has been canceled at an exact moment
     * in time.
     * <p/>
     * If the {@code scheduledTime} is calculated based on the "current time", use the {@link
     * TestExecutor#currentTime()} to get the time to use as "current time".
     *
     * @param scheduledTime the time at which no deadline matching the given {@code matcher} should be scheduled
     * @param matcher       the matcher defining the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineMatching(Instant scheduledTime,
                                                         Matcher<? super DeadlineMessage<?>> matcher);

    /**
     * Asserts that <b>no</b> deadline equal to the given {@code deadline} has been scheduled at the given {@code
     * scheduledTime}. Can be used to validate if a deadline has never been set or has been canceled at an exact moment
     * in time.
     * <p/>
     * If the {@code scheduledTime} is calculated based on the "current time", use the {@link
     * TestExecutor#currentTime()} to get the time to use as "current time".
     * <p/>
     * Note that the source attribute of the deadline is ignored when comparing deadlines. Deadlines are compared using
     * an "equals" check on all fields in the deadlines.
     *
     * @param scheduledTime the time at which no deadline equal to the given {@code deadline} should be scheduled
     * @param deadline      the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadline(Instant scheduledTime, Object deadline);

    /**
     * Asserts that <b>no</b> deadline with the given {@code deadlineType} has been scheduled at the given {@code
     * scheduledTime}. Can be used to validate if a deadline has never been set or has been canceled at an exact moment
     * in time.
     *
     * @param scheduledTime the time at which no deadline of {@code deadlineType} should be scheduled
     * @param deadlineType  the type of the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineOfType(Instant scheduledTime, Class<?> deadlineType);

    /**
     * Asserts that <b>no</b> deadline with the given {@code deadlineName} has been scheduled at the given {@code
     * scheduledTime}. Can be used to validate if a deadline has never been set or has been canceled at an exact moment
     * in time.
     *
     * @param scheduledTime the time at which no deadline of {@code deadlineName} should be scheduled
     * @param deadlineName  the name of the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineWithName(Instant scheduledTime, String deadlineName);

    /**
     * Asserts that <b>no</b> deadline matching the given {@code matcher} has been scheduled between the {@code to} and {@code from} times, where {@code to} and
     * {@code from} are inclusive. Can be used to validate if a deadline has never been set or has been canceled within a given timeframe.
     *
     * @param from    the time from which no deadline equal to the given {@code deadline} should be scheduled (inclusive)
     * @param to      the time until which no deadline equal to the given {@code deadline} should be scheduled (inclusive)
     * @param matcher the matcher defining the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineMatching(Instant from, Instant to, Matcher<? super DeadlineMessage<?>> matcher);

    /**
     * Asserts that <b>no</b> deadline equal to the given {@code deadline} has been scheduled between the {@code to} and {@code from} times, where {@code to}
     * and {@code from} are inclusive. Can be used to validate if a deadline has never been set or has been canceled within a given timeframe.
     *
     * @param from     the time from which no deadline equal to the given {@code deadline} should be scheduled (inclusive)
     * @param to       the time until which no deadline equal to the given {@code deadline} should be scheduled (inclusive)
     * @param deadline the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadline(Instant from, Instant to, Object deadline);

    /**
     * Asserts that <b>no</b> deadline with the given {@code deadlineType} has been scheduled between the {@code to} and {@code from} times, where {@code to}
     * and {@code from} are inclusive. Can be used to validate if a deadline has never been set or has been canceled within a given timeframe.
     *
     * @param from         the time from which no deadline equal to the given {@code deadline} should be scheduled (inclusive)
     * @param to           the time until which no deadline equal to the given {@code deadline} should be scheduled (inclusive)
     * @param deadlineType the type of the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineOfType(Instant from, Instant to, Class<?> deadlineType);

    /**
     * Asserts that <b>no</b> deadline with the given {@code deadlineName} has been scheduled between the {@code to} and {@code from} times, where {@code to}
     * and {@code from} are inclusive. Can be used to validate if a deadline has never been set or has been canceled within a given timeframe.
     *
     * @param from         the time from which no deadline equal to the given {@code deadline} should be scheduled (inclusive)
     * @param to           the time until which no deadline equal to the given {@code deadline} should be scheduled (inclusive)
     * @param deadlineName the name of the deadline which should not be scheduled
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNoScheduledDeadlineWithName(Instant from, Instant to, String deadlineName);

    /**
     * Asserts that deadlines matching the given {@code matcher} have been triggered for this aggregate.
     *
     * @param matcher the matcher that defines the expected list of deadlines
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectTriggeredDeadlinesMatching(Matcher<? extends List<? super DeadlineMessage<?>>> matcher);

    /**
     * Asserts that given {@code expected} deadlines have been triggered. Deadlines are compared by their type
     * and fields using "equals".
     *
     * @param expected the sequence of deadlines expected to have been triggered
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectTriggeredDeadlines(Object... expected);

    /**
     * Asserts that the given {@code expectedDeadlineNames} have been triggered. Matches that the given names are
     * complete, in the same order and match the triggered deadlines by validating with {@link
     * DeadlineMessage#getDeadlineName()}.
     *
     * @param expectedDeadlineNames the sequence of deadline names expected to have been triggered
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectTriggeredDeadlinesWithName(String... expectedDeadlineNames);

    /**
     * Asserts that the given {@code expectedDeadlineTypes} have been triggered. Matches that the given types are
     * complete, in the same order and match the triggered deadlines by validating with {@link
     * DeadlineMessage#getPayloadType()}.
     *
     * @param expectedDeadlineTypes the sequence of deadline types expected to have been triggered
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectTriggeredDeadlinesOfType(Class<?>... expectedDeadlineTypes);

    /**
     * Asserts that the Aggregate has been marked for deletion.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectMarkedDeleted();

    /**
     * Asserts that the Aggregate has NOT been marked for deletion.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator<T> expectNotMarkedDeleted();
}
