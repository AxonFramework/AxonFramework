/*
 * Copyright (c) 2010-2024. Axon Framework
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

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * The default {@link SourcingCondition} implementation.
 * <p>
 * The {@code start} and {@code end} refer to the window of events that is of interest to this
 * {@link SourcingCondition}.
 *
 * @param criteria The {@link EventCriteria} of the model to source.
 * @param start    The start position in the event sequence to retrieve of the model to source.
 * @param end      The end position in the event sequence to retrieve of the model to source.
 * @author Steven van Beelen
 * @since 5.0.0
 */
record DefaultSourcingCondition(@Nonnull EventCriteria criteria,
                                long start,
                                long end) implements SourcingCondition {

    DefaultSourcingCondition {
        assertNonNull(criteria, "The EventCriteria cannot be null");
    }

    @Override
    public SourcingCondition combine(@Nonnull SourcingCondition other) {
        return new DefaultSourcingCondition(
                criteria().combine(other.criteria()),
                Math.min(this.start, other.start()),
                Math.max(this.end, other.end())
        );
    }
}
