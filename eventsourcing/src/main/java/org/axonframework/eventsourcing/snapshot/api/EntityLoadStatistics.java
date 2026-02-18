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
 * Represents statistical information collected during the loading of an event-sourced entity.
 * <p>
 * {@code EntityLoadStatistics} captures metrics about the replay process,
 * including the number of events applied and the total time taken to load
 * and evolve the entity.
 * <p>
 * This information can be used for monitoring, diagnostics, and snapshot
 * decision policies.
 *
 * @param evolutionsCount the number of events applied to the entity during loading,
 *                        never negative
 * @param loadTime the total time spent loading and evolving the entity,
 *                 never {@code null}
 * @author John Hendrikx
 * @since 5.1.0
 */
public record EntityLoadStatistics(long evolutionsCount, Duration loadTime) {

    /**
     * Creates a new {@code EntityLoadStatistics}.
     *
     * @throws NullPointerException if {@code loadTime} is {@code null}
     * @throws IllegalArgumentException if {@code evolutionsCount} is negative
     */
    public EntityLoadStatistics {
        Objects.requireNonNull(loadTime, "loadTime");

        if (evolutionsCount < 0) {
            throw new IllegalArgumentException("evolutionsCount must be non-negative");
        }
    }
}