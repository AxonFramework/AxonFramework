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
import org.axonframework.eventstreaming.EventCriteria;

import static java.util.Objects.requireNonNull;

/**
 * The default {@link SourcingCondition} implementation.
 * <p>
 * The {@code start} refers to the start point of the event stream that is of interest to this
 * {@link SourcingCondition}.
 *
 * @param start    The start position in the event sequence to retrieve of the entity to source.
 * @param criteria The {@link EventCriteria} set of the entity to source.
 * @author Steven van Beelen
 * @author John Hendrikx
 * @since 5.0.0
 */
record DefaultSourcingCondition(
        @Nonnull Position start,
        @Nonnull EventCriteria criteria
) implements SourcingCondition {

    DefaultSourcingCondition {
        requireNonNull(start, "start cannot be null");
        requireNonNull(criteria, "criteria cannot be null");
    }

    @Override
    public SourcingCondition or(@Nonnull SourcingCondition other) {
        return new DefaultSourcingCondition(
            other.start().min(start),
            other.criteria().or(criteria)
        );
    }
}
