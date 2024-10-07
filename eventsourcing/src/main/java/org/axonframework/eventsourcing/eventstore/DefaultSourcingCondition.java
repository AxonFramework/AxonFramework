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

import java.util.Objects;
import java.util.OptionalLong;

/**
 * The default {@link SourcingCondition} implementation.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class DefaultSourcingCondition implements SourcingCondition {

    private final EventCriteria criteria;
    private final Long start;
    private final Long end;

    /**
     * Constructs a {@link DefaultSourcingCondition} using the given {@code criteria} to source through.
     * <p>
     * The {@code start} and {@code end} refer to the window of events that is of interest to this
     * {@link SourcingCondition}.
     *
     * @param criteria The {@link EventCriteria} of the model to source.
     * @param start    The start position in the event sequence to retrieve of the model to source.
     * @param end      The end position in the event sequence to retrieve of the model to source.
     */
    DefaultSourcingCondition(@Nonnull EventCriteria criteria,
                             Long start,
                             Long end) {
        this.criteria = criteria;
        this.start = start != null ? start : -1;
        this.end = end;
    }

    @Override
    public EventCriteria criteria() {
        return criteria;
    }

    @Override
    public long start() {
        return start;
    }

    @Override
    public OptionalLong end() {
        return this.end == null ? OptionalLong.empty() : OptionalLong.of(this.end);
    }

    @Override
    public SourcingCondition combine(@Nonnull SourcingCondition other) {
        return new DefaultSourcingCondition(
                criteria().combine(other.criteria()),
                Math.min(this.start, other.start()),
                Math.max(this.end, other.end().orElse(this.end))
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultSourcingCondition that = (DefaultSourcingCondition) o;
        return Objects.equals(criteria, that.criteria) && Objects.equals(start, that.start)
                && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(criteria, start, end);
    }
}
