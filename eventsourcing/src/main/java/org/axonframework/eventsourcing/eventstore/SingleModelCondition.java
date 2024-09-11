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

import jakarta.validation.constraints.NotEmpty;

import java.util.OptionalLong;

/**
 * A {@link SourcingCondition} implementation intended to source a single model instance, based on the given
 * {@code identifierName} to {@code identifierValue} pair.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SingleModelCondition implements SourcingCondition {

    private final EventCriteria identifierCriteria;
    private final Long start;
    private final Long end;

    /**
     * Constructs a {@link SingleModelCondition} using the given {@code identifierName} and {@code identifierValue} to
     * construct an {@link EventCriteria#hasIdentifier(String, String) identifier-based EventCriteria}. The
     * {@code start} and {@code end} refer to the window of events that is of interest to this
     * {@link SourcingCondition}.
     *
     * @param identifierName  The name of the identifier of the model to source.
     * @param identifierValue The value of the identifier of the model to source.
     * @param start           The start position in the event sequence to retrieve of the model to source.
     * @param end             The end position in the event sequence to retrieve of the model to source.
     */
    SingleModelCondition(@NotEmpty String identifierName,
                         @NotEmpty String identifierValue,
                         Long start,
                         Long end) {
        this.identifierCriteria = EventCriteria.hasIdentifier(identifierName, identifierValue);
        this.start = start != null ? start : -1;
        this.end = end;
    }

    @Override
    public EventCriteria criteria() {
        return this.identifierCriteria;
    }

    @Override
    public long start() {
        return this.start;
    }

    @Override
    public OptionalLong end() {
        return this.end == null ? OptionalLong.empty() : OptionalLong.of(this.end);
    }
}
