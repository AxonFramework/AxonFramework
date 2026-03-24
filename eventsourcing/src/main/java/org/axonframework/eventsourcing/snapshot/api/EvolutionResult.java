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

package org.axonframework.eventsourcing.snapshot.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Represents metrics collected while sourcing an event-sourced entity to its current state.
 * <p>
 * {@code EvolutionResult} captures information about the sourcing process,
 * including how many events were applied, how long the process took,
 * and whether a snapshot was requested during sourcing.
 * <p>
 * This information is provided to the {@link Snapshotter} once sourcing
 * has completed, allowing it to make an informed decision about creating
 * and persisting a snapshot.
 *
 * @param eventsApplied the number of events applied while sourcing the entity,
 *                      never negative
 * @param sourcingTime the total time spent sourcing the entity, never {@code null}
 * @author John Hendrikx
 * @since 5.1.0
 */
public record EvolutionResult(long eventsApplied, Duration sourcingTime) {

    /**
     * Creates a new {@code EvolutionResult}.
     *
     * @throws NullPointerException if {@code sourcingTime} is {@code null}
     * @throws IllegalArgumentException if {@code eventsApplied} is negative
     */
    public EvolutionResult {
        Objects.requireNonNull(sourcingTime, "The sourcingTime parameter must not be null.");

        if (eventsApplied < 0) {
            throw new IllegalArgumentException("The eventsApplied parameter must be non-negative.");
        }
    }
}