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

package org.axonframework.test;

import org.hamcrest.Matcher;

/**
 * Interface describing the operations available on the "validate result" (a.k.a. "then") stage of the test execution.
 * The underlying fixture expects a test to have been executed succesfully using a {@link
 * org.axonframework.test.TestExecutor}.
 * <p>
 * There are several things to validate:<ul><li>the published events,<li>the stored events, and<li>the command
 * handler's
 * execution result, which is one of <ul><li>a regular return value,<li>a <code>void</code> return value, or<li>an
 * exception.</ul></ul>
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface ResultValidator {

    /**
     * Expect the given set of events to have been stored and published. Note that this assertion will fail if events
     * were published but not saved. If you expect events (e.g. Application Events) to have been dispatched, use the
     * {@link #expectPublishedEvents(Object...)} and {@link #expectStoredEvents(Object...)} methods instead.
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
     * Expect the published events to match the given <code>matcher</code>. Note that this assertion will fail if
     * events
     * were published but not saved.
     * <p>
     * Note: if no events were published or stored, the matcher receives an empty List.
     *
     * @param matcher The matcher to match with the actually published events
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectEventsMatching(Matcher<? extends Iterable<?>> matcher);

    /**
     * Expect the given set of events to have been published on the events bus. If you expect the same events to be
     * stored, too, consider using the {@link #expectEvents(Object...)} instead.
     * <p>
     * All events are compared for equality using a shallow equals comparison on all the fields of the events. This
     * means that all assigned values on the events' fields should have a proper equals implementation.
     * <p>
     * Note that the event identifier is ignored in the comparison. For Application and System events, however, the
     * <code>source</code> of the events must be equal, too.
     *
     * @param expectedEvents The expected events, in the exact order they are expected to be dispatched.
     * @return the current ResultValidator, for fluent interfacing
     * @deprecated Use {@link #expectEvents(Object...)}
     */
    @Deprecated
    ResultValidator expectPublishedEvents(Object... expectedEvents);

    /**
     * Expect the list of published event to match the given <code>matcher</code>. This method will only take into
     * account the events that have been published. Stored events that have not been published to the event bus are
     * ignored.
     * <p>
     * Note: if no events were published, the matcher receives an empty List.
     *
     * @param matcher The matcher which validates the actual list of published events.
     * @return the current ResultValidator, for fluent interfacing
     * @deprecated Use {@link #expectEventsMatching(Matcher)}
     */
    @Deprecated
    ResultValidator expectPublishedEventsMatching(Matcher<? extends Iterable<?>> matcher);

    /**
     * Expect the given set of events to have been stored in the event store. If you expect the same events to be
     * published, too, consider using the {@link #expectEvents(Object...)} instead.
     * <p>
     * All events are compared for equality using a shallow equals comparison on all the fields of the events. This
     * means that all assigned values on the events' fields should have a proper equals implementation.
     * <p>
     * Note that the event identifier is ignored in the comparison. For Application and System events, however, the
     * <code>source</code> of the events must be equal, too.
     *
     * @param expectedEvents The expected events, in the exact order they are expected to be stored.
     * @return the current ResultValidator, for fluent interfacing
     * @deprecated Use {@link #expectEvents(Object...)}
     */
    @Deprecated
    ResultValidator expectStoredEvents(Object... expectedEvents);

    /**
     * Expect the list of stored event to match the given <code>matcher</code>. This method will only take into account
     * the events that have been stored. Stored events that have not been stored in the event store are ignored.
     * <p>
     * Note: if no events were stored, the matcher receives an empty List.
     *
     * @param matcher The matcher which validates the actual list of stored events.
     * @return the current ResultValidator, for fluent interfacing
     * @deprecated Use {@link #expectEventsMatching(Matcher)}
     */
    @Deprecated
    ResultValidator expectStoredEventsMatching(Matcher<? extends Iterable<?>> matcher);

    /**
     * Expect the command handler to return the given <code>expectedReturnValue</code> after execution. The actual and
     * expected values are compared using their equals methods.
     *
     * @param expectedReturnValue The expected return value of the command execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectReturnValue(Object expectedReturnValue);

    /**
     * Expect the command handler to return a value that matches the given <code>matcher</code> after execution.
     *
     * @param matcher The matcher to verify the actual return value against
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectReturnValue(Matcher<?> matcher);

    /**
     * Expect the given <code>expectedException</code> to occur during command handler execution. The actual exception
     * should be exactly of that type, subclasses are not accepted.
     *
     * @param expectedException The type of exception expected from the command handler execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectException(Class<? extends Throwable> expectedException);

    /**
     * Expect an exception to occur during command handler execution that matches with the given <code>matcher</code>.
     *
     * @param matcher The matcher to validate the actual exception
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectException(Matcher<?> matcher);

    /**
     * Explicitly expect a <code>void</code> return type on the given command handler. <code>void</code> is the
     * recommended return value for all command handlers as they allow for a more scalable architecture.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectVoidReturnType();
}
