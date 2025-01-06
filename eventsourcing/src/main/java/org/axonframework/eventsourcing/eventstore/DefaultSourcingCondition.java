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

import java.util.HashSet;
import java.util.Set;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * The default {@link SourcingCondition} implementation.
 * <p>
 * The {@code start} and {@code end} refer to the window of events that is of interest to this
 * {@link SourcingCondition}.
 *
 * @param start    The start position in the event sequence to retrieve of the model to source.
 * @param end      The end position in the event sequence to retrieve of the model to source.
 * @param criteria The {@link EventCriteria} of the model to source.
 * @author Steven van Beelen
 * @since 5.0.0
 */
record DefaultSourcingCondition(long start, long end, @Nonnull Set<EventCriteria> criteria
) implements SourcingCondition {

    DefaultSourcingCondition {
        assertNonNull(criteria, "The EventCriteria cannot be null");
    }

    public DefaultSourcingCondition(long start, long end, @Nonnull EventCriteria eventCriteria) {
        this(start, end, Set.of(eventCriteria));
    }

    @Override
    public SourcingCondition or(@Nonnull SourcingCondition other) {
        var combined = new HashSet<>(criteria());
        combined.addAll(other.criteria());
        return new DefaultSourcingCondition(Math.min(this.start, other.start()),
                                            Math.max(this.end, other.end()),
                                            combined);
    }
}
