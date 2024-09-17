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

import org.axonframework.eventsourcing.eventstore.Index;

import java.util.function.Function;

/**
 * Functional interface describing a resolver of an {@link Index} based on an identifier of type {@code ID}.
 *
 * @param <ID> The type of identifier to resolve to an {@link Index}.
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface IndexResolver<ID> extends Function<ID, Index> {
    // TODO - Discuss: Does ID fit the bill or is this the model we inject here?
    // TODO - Discuss: Should we return a Set<Index> instead of a single Index?

    /**
     * Resolves the given {@code identifier} to an {@link Index}.
     *
     * @param identifier The instance to resolve to an {@link Index} representation.
     * @return The given {@code identifier} resolved to an {@link Index}.
     */
    default Index resolve(ID identifier) {
        return apply(identifier);
    }
}
