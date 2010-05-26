/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.Event;

/**
 * Interface describing the operations available on the "validate result" (a.k.a. "then") stage of the test execution.
 * The underlying fixture expects a test to have been executed succesfully using a {@link
 * org.axonframework.test.TestExecutor}.
 * <p/>
 * There are several things to validate:<ul><li>the published events,<li>the stored events, and<li>the command handler's
 * execution result, which is one of <ul><li>a regular return value,<li>a <code>void</code> return value, or<li>an
 * exception.</ul></ul>
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface ResultValidator {

    /**
     * Expect the given set of events to have been stored and published. Note that this assertion will fail if events
     * were published but not saved. IF you expect events (e.g. Application Events) to have been dispathed, use the
     * {@link #expectPublishedEvents(org.axonframework.domain.Event...)} and {@link
     * #expectStoredEvents(org.axonframework.domain.DomainEvent...)} methods instead.
     * <p/>
     * All events are compared for equality using a shallow equals comparison on all the fields of the events. This
     * means that all assigned values on the events' fields should have a proper equals implementation.
     * <p/>
     * Note that the event identifier is ignored in the comparison.
     *
     * @param expectedEvents The expected events, in the exact order they are expected to be dispatched and stored.
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectEvents(DomainEvent... expectedEvents);

    /**
     * Expect the given set of events to have been published on the events bus. If you expect the same events to be
     * stored, too, consider using the {@link #expectEvents(org.axonframework.domain.DomainEvent...)} instead.
     * <p/>
     * All events are compared for equality using a shallow equals comparison on all the fields of the events. This
     * means that all assigned values on the events' fields should have a proper equals implementation.
     * <p/>
     * Note that the event identifier is ignored in the comparison. For Application and System events, however, the
     * <code>source</code> of the events must be equal, too.
     *
     * @param expectedEvents The expected events, in the exact order they are expected to be dispatched.
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectPublishedEvents(Event... expectedEvents);

    /**
     * Expect the given set of events to have been stored in the event store. If you expect the same events to be
     * published, too, consider using the {@link #expectEvents(org.axonframework.domain.DomainEvent...)} instead.
     * <p/>
     * All events are compared for equality using a shallow equals comparison on all the fields of the events. This
     * means that all assigned values on the events' fields should have a proper equals implementation.
     * <p/>
     * Note that the event identifier is ignored in the comparison. For Application and System events, however, the
     * <code>source</code> of the events must be equal, too.
     *
     * @param expectedEvents The expected events, in the exact order they are expected to be stored.
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectStoredEvents(DomainEvent... expectedEvents);

    /**
     * Expect the command handler to return the given <code>expectedReturnValue</code> after execution. The actual and
     * expected values are compared using their equals methods.
     *
     * @param expectedReturnValue The expected return value of the command execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectReturnValue(Object expectedReturnValue);

    /**
     * Expect the given <code>expectedException</code> to occur during command handler execution. The actual exception
     * should be exactly of that type, subclasses are not accepted.
     *
     * @param expectedException The type of exception expected from the command handler execution
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectException(Class<? extends Throwable> expectedException);

    /**
     * Explicitly expect a <code>void</code> return type on the given command handler. <code>void</code> is the
     * recommended return value for all command handlers as they allow for a more scalable architecture.
     *
     * @return the current ResultValidator, for fluent interfacing
     */
    ResultValidator expectVoidReturnType();
}
