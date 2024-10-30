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

package org.axonframework.eventsourcing;

import org.axonframework.eventsourcing.eventstore.EventCriteria;

import java.util.function.Function;

/**
 * Functional interface describing a resolver of an {@link EventCriteria} based on an identifier of type {@code ID}.
 *
 * @param <ID> The type of identifier to resolve to an {@link EventCriteria}.
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface CriteriaResolver<ID> extends Function<ID, EventCriteria> {

    /**
     * Resolves the given {@code identifier} to an {@link EventCriteria}.
     *
     * @param identifier The instance to resolve to an {@link EventCriteria}.
     * @return The given {@code identifier} resolved to an {@link EventCriteria}.
     */
    default EventCriteria resolve(ID identifier) {
        return apply(identifier);
    }
}
