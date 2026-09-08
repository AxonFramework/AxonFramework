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

import org.axonframework.test.fixture.AxonTestPhase;

import java.util.Objects;

/**
 * Default implementation of {@link FixtureExecutionResult}, asserting against the
 * {@link AxonTestPhase.Then then-phase} the "when" phase produced.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @since 5.4.0
 */
class FixtureExecutionResultImpl implements FixtureExecutionResult {

    private final Class<?> sagaType;
    private final AxonTestPhase.Then.Event then;

    /**
     * Constructs a {@code FixtureExecutionResultImpl} asserting on the given {@code then} phase.
     *
     * @param sagaType the type of Saga under test, used to filter the store on the association assertions
     * @param then     the then-phase of the fixture the Saga was driven through
     */
    FixtureExecutionResultImpl(Class<?> sagaType, AxonTestPhase.Then.Event then) {
        this.sagaType = Objects.requireNonNull(sagaType, "The sagaType may not be null.");
        this.then = Objects.requireNonNull(then, "The then-phase may not be null.");
    }

    @Override
    public FixtureExecutionResult expectActiveSagas(int expected) {
        then.expect(SagaAssertions.activeSagas(expected));
        return this;
    }

    @Override
    public FixtureExecutionResult expectAssociationWith(String associationKey, Object associationValue) {
        then.expect(SagaAssertions.associationWith(sagaType, associationKey, associationValue));
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoAssociationWith(String associationKey, Object associationValue) {
        then.expect(SagaAssertions.noAssociationWith(sagaType, associationKey, associationValue));
        return this;
    }
}
