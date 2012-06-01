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

import org.axonframework.eventsourcing.EventSourcedAggregateRoot;

/**
 * Utility class providing access to fixture instances in the Axon Test module.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class Fixtures {

    private Fixtures() {
        // prevent instantiation
    }

    /**
     * Returns a new given-when-then style test fixture in configuration mode. See {@link
     * org.axonframework.test.FixtureConfiguration} for more detailed usage information.
     *
     * @param aggregateType The aggregate under test
     * @param <T>           The type of aggregate tested in the fixture
     * @return a new given-when-then style test fixture in configuration mode
     */
    public static <T extends EventSourcedAggregateRoot> FixtureConfiguration<T> newGivenWhenThenFixture(
            Class<T> aggregateType) {
        return new GivenWhenThenTestFixture<T>(aggregateType);
    }
}
