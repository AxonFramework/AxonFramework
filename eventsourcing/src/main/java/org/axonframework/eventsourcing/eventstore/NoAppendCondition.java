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

/**
 * An {@link AppendCondition} implementation that has {@link EventCriteria#anyEvent() no criteria}.
 * <p>
 * Only use this {@code AppendCondition} when appending events that <em>do not</em> partake in the consistency boundary
 * of any model(s).
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
final class NoAppendCondition implements AppendCondition {

    /**
     * Default instance of the {@link NoAppendCondition}.
     */
    static final NoAppendCondition INSTANCE = new NoAppendCondition();

    private NoAppendCondition() {
        // No-arg constructor to enforce use of INSTANCE constant.
    }

    @Override
    public long consistencyMarker() {
        return -1;
    }

    @Override
    public EventCriteria criteria() {
        return EventCriteria.anyEvent();
    }

    @Override
    public AppendCondition with(@Nonnull SourcingCondition condition) {
        return AppendCondition.from(condition);
    }

    @Override
    public AppendCondition withMarker(long consistencyMarker) {
        throw new UnsupportedOperationException("Cannot add a consistency marker without any criteria");
    }
}
