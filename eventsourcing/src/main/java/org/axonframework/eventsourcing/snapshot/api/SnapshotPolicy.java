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
 * Defines a policy for determining when an event-sourced entity should be snapshotted.
 * <p>
 * A {@code SnapshotPolicy} encapsulates the logic that decides whether a snapshot
 * is required based on the number of events applied since the last snapshot
 * and/or the time taken to load the entity.
 * <p>
 * Policies are <strong>immutable and thread-safe</strong>, so a single instance
 * can be shared across multiple entities and asynchronous operations.
 * <p>
 * Policies can be composed using {@link #or(SnapshotPolicy)} to combine multiple conditions.
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * SnapshotPolicy policy = SnapshotPolicy.afterEvents(100)
 *                                       .or(SnapshotPolicy.whenLoadTimeExceeds(Duration.ofMillis(50)));
 * }</pre>
 *
 * @author John Hendrikx
 * @since 5.1.0
 */
public interface SnapshotPolicy {

    /**
     * Creates a policy that triggers a snapshot once more than the specified number
     * of events have been applied since the last snapshot.
     *
     * @param eventCount the minimum number of events after which a snapshot should be made
     * @return a snapshot policy based on event count, never {@code null}
     */
    public static SnapshotPolicy afterEvents(int eventCount) {
        return stats -> stats.evolutionsCount() > eventCount;
    }

    /**
     * Creates a policy that triggers a snapshot if the load time of the entity
     * exceeds the specified duration.
     *
     * @param duration the maximum load duration before a snapshot is triggered, cannot be {@code null}
     * @return a snapshot policy based on load time, never {@code null}
     * @throws NullPointerException if {@code duration} is {@code null}
     */
    public static SnapshotPolicy whenLoadTimeExceeds(Duration duration) {
        return stats -> stats.loadTime().compareTo(duration) > 0;
    }

    /**
     * Determines whether a snapshot is needed given the current evolution state.
     *
     * @param loadStatistics information about the load process to base the decision on, cannot be {@code null}
     * @return {@code true} if a snapshot should be made, {@code false} otherwise
     * @implNote implementations should be thread-safe and side-effect free
     */
    boolean needsSnapshot(EntityLoadStatistics loadStatistics);

    /**
     * Composes this policy with another policy using logical OR.
     * <p>
     * The resulting policy triggers a snapshot if either this policy or the
     * other policy requires it.
     *
     * @param other another snapshot policy, cannot be {@code null}
     * @return a new {@code SnapshotPolicy} representing the logical OR of this and the other, never {@code null}
     * @throws NullPointerException if {@code other} is {@code null}
     */
    default SnapshotPolicy or(SnapshotPolicy other) {
        Objects.requireNonNull(other, "other");

        return stats -> this.needsSnapshot(stats) || other.needsSnapshot(stats);
    }
}
