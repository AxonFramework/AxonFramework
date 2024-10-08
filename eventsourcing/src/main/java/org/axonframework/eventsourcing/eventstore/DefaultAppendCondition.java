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
 * Default implementation of the {@link AppendCondition}, using the given {@code consistencyMarker} and {@code criteria}
 * as output for the {@link #consistencyMarker()} and {@link #criteria()} operations respectively.
 *
 * @param consistencyMarker The {@code long} to return on the {@link #consistencyMarker()} operation.
 * @param criteria          The {@link EventCriteria} to return on the {@link #criteria()} operation.
 * @author Steven van Beelen
 * @since 5.0.0
 */
record DefaultAppendCondition(
        long consistencyMarker,
        @Nonnull EventCriteria criteria
) implements AppendCondition {

    DefaultAppendCondition {
        assertNonNull(criteria, "The EventCriteria cannot be null");
    }

    @Override
    public AppendCondition with(@Nonnull SourcingCondition condition) {
        return new DefaultAppendCondition(
                condition.end()
                         .stream()
                         .map(end -> Math.min(end, consistencyMarker))
                         .findFirst()
                         .orElse(consistencyMarker),
                criteria.combine(condition.criteria())
        );
    }

    @Override
    public AppendCondition withMarker(long consistencyMarker) {
        return new DefaultAppendCondition(Math.min(consistencyMarker, this.consistencyMarker), criteria);
    }
}
