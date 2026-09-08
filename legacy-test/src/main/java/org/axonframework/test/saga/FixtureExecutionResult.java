/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.test.saga;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.test.AxonAssertionError;
import org.hamcrest.Matcher;

import java.util.List;

/**
 * Interface towards an object that contains the results of a Saga test fixture execution. Assertions are made against
 * the state of the Sagas and the messages they produced during the "when" phase.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public interface FixtureExecutionResult {

    /**
     * Asserts that the repository contains the given {@code expected} amount of active Sagas.
     * <p>
     * Counts every Saga in the store, whatever its type, as Axon Framework 4 did. That makes this assertion asymmetric
     * with {@link #expectAssociationWith(String, Object)}, which does filter on the Saga type under test.
     *
     * @param expected the expected number of active Sagas in this fixture
     * @return the FixtureExecutionResult for method chaining
     * @throws AxonAssertionError when the store holds another number of Sagas
     */
    FixtureExecutionResult expectActiveSagas(int expected);

    /**
     * Asserts that at least one of the active Sagas is associated with the given {@code associationKey} and
     * {@code associationValue}.
     * <p>
     * The {@code associationValue} is compared by its {@link Object#toString() string representation}, as Axon
     * Framework 4 did.
     *
     * @param associationKey   the key of the association
     * @param associationValue the value of the association
     * @return the FixtureExecutionResult for method chaining
     * @throws AxonAssertionError when no Saga holds the association
     */
    FixtureExecutionResult expectAssociationWith(String associationKey, Object associationValue);

    /**
     * Asserts that none of the active Sagas is associated with the given {@code associationKey} and
     * {@code associationValue}.
     * <p>
     * The {@code associationValue} is compared by its {@link Object#toString() string representation}, as Axon
     * Framework 4 did.
     *
     * @param associationKey   the key of the association
     * @param associationValue the value of the association
     * @return the FixtureExecutionResult for method chaining
     * @throws AxonAssertionError when a Saga holds the association
     */
    FixtureExecutionResult expectNoAssociationWith(String associationKey, Object associationValue);

    /**
     * Asserts that the Sagas dispatched the given commands, in the exact sequence given.
     * <p>
     * Each element is either a {@link CommandMessage}, in which case payload and metadata are both compared, or a
     * payload, in which case only the payload is compared.
     *
     * @param commands the commands expected to have been dispatched
     * @return the FixtureExecutionResult for method chaining
     * @throws AxonAssertionError when another set of commands was dispatched
     */
    FixtureExecutionResult expectDispatchedCommands(Object... commands);

    /**
     * Asserts that the Sagas dispatched commands matching the given {@code matcher}.
     *
     * @param matcher the matcher validating the dispatched commands
     * @return the FixtureExecutionResult for method chaining
     * @throws AxonAssertionError when the dispatched commands do not match
     */
    FixtureExecutionResult expectDispatchedCommandsMatching(Matcher<? extends List<? super CommandMessage>> matcher);

    /**
     * Asserts that the Sagas dispatched no commands.
     *
     * @return the FixtureExecutionResult for method chaining
     * @throws AxonAssertionError when any command was dispatched
     */
    FixtureExecutionResult expectNoDispatchedCommands();

    /**
     * Asserts that the Sagas published the given events, in the exact sequence given.
     * <p>
     * Each element is either an {@link EventMessage} or a payload; a message is unwrapped to its payload, so only
     * payloads are compared and metadata is not, as in Axon Framework 4. The event that drove the "when" phase is not
     * part of this set, since the test published it rather than the Saga.
     *
     * @param expected the events expected to have been published
     * @return the FixtureExecutionResult for method chaining
     * @throws AxonAssertionError when another set of events was published
     */
    FixtureExecutionResult expectPublishedEvents(Object... expected);

    /**
     * Asserts that the Sagas published events matching the given {@code matcher}.
     * <p>
     * The event that drove the "when" phase is not part of the set the matcher sees, since the test published it
     * rather than the Saga.
     *
     * @param matcher the matcher validating the published events
     * @return the FixtureExecutionResult for method chaining
     * @throws AxonAssertionError when the published events do not match
     */
    FixtureExecutionResult expectPublishedEventsMatching(Matcher<? extends List<? super EventMessage>> matcher);

    /**
     * Asserts that the Saga handled the "when" event without failing.
     * <p>
     * Unlike Axon Framework 4, a failing {@code @SagaEventHandler} propagates by default rather than being logged and
     * swallowed, so this assertion holds unless the Saga threw. A Saga that suppresses its own failures with an
     * {@code @ExceptionHandler} passes it, as it did in Axon Framework 4.
     *
     * @return the FixtureExecutionResult for method chaining
     * @throws AxonAssertionError when handling the "when" event failed
     */
    FixtureExecutionResult expectSuccessfulHandlerExecution();
}
