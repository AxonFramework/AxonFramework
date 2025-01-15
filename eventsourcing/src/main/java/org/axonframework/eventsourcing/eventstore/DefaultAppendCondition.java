/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of the {@link AppendCondition}, using the given {@code consistencyMarker} and {@code criteria}
 * as output for the {@link #consistencyMarker()} and {@link #criteria()} operations respectively.
 *
 * @param consistencyMarker The consistency marker obtained while sourcing events.
 * @param criteria          The criteria defining which changes are considered conflicting
 * @author Steven van Beelen
 * @since 5.0.0
 */
record DefaultAppendCondition(
        @Nonnull ConsistencyMarker consistencyMarker,
        @Nonnull Set<EventCriteria> criteria
) implements AppendCondition {

    DefaultAppendCondition {
        requireNonNull(consistencyMarker, "The ConsistencyMarker cannot be null");
        requireNonNull(criteria, "The EventCriteria cannot be null");
    }

    /**
     * Creates an appendCondition with given {@code consistencyMarker} and a single {@code eventCriteria}.
     *
     * @param consistencyMarker The consistency marker for this append condition.
     * @param eventCriteria     The criteria for the append condition.
     */
    public DefaultAppendCondition(@Nonnull ConsistencyMarker consistencyMarker, @Nonnull EventCriteria eventCriteria) {
        this(consistencyMarker, Set.of(requireNonNull(eventCriteria, "EventCriteria may not be null")));
    }

    @Override
    public AppendCondition withMarker(ConsistencyMarker consistencyMarker) {
        return new DefaultAppendCondition(consistencyMarker, criteria);
    }
}
