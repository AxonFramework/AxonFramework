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
import org.axonframework.eventhandling.TrackingToken;

import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of the {@link StreamingCondition}.
 *
 * @param position The {@link TrackingToken} defining the {@link #position()} of this condition.
 * @param criteria The {@link EventCriteria} defining the {@link #criteria()} of this condition.
 * @author Steven van Beelen
 * @since 5.0.0
 */
record DefaultStreamingCondition(
        @Nonnull TrackingToken position,
        @Nonnull Set<EventCriteria> criteria
) implements StreamingCondition {

    DefaultStreamingCondition {
        requireNonNull(position, "The position cannot be null");
        requireNonNull(criteria, "The EventCriteria cannot be null");
    }

    public DefaultStreamingCondition(@Nonnull TrackingToken position, @Nonnull EventCriteria... criteria) {
        this(position, Set.of(criteria));
    }

    @Override
    public StreamingCondition or(@Nonnull EventCriteria criteria) {
        if (this.criteria.contains(criteria)) {
            return this;
        }
        Set<EventCriteria> newCriteria = new HashSet<>(this.criteria);
        newCriteria.add(criteria);
        return new DefaultStreamingCondition(this.position, newCriteria);
    }
}
