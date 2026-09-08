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

import org.axonframework.test.AxonAssertionError;

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
}
