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

package org.axonframework.eventsourcing.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;

/**
 * Functional interface towards creating a new instance of an entity of the given {@code entityType} and the given
 * {@code id}. Optionally, the {@link #createEntityBasedOnFirstEventMessage(Object, EventMessage)} method can be
 * overridden to create an entity based on the first event message, enabling the creation of an entity with immutable
 * state.
 *
 * @param <ID> The type of the identifier of the entity to create.
 * @param <E>  The type of the entity to create.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@FunctionalInterface
public interface EventSourcedEntityFactory<ID, E> {

    /**
     * Creates a new instance of an entity of the given {@code entityType} and {@code id}.
     *
     * @param id The identifier of the entity to create.
     * @return A new instance of the entity.
     */
    E createEmptyEntity(@Nonnull ID id);

    /**
     * Creates a new instance of an entity of the given {@code entityType}, {@code idType}, and the
     * {@code firstEventMessage} that is present for the entity. Unless overridden, this method will create an empty
     * entity through {@link #createEmptyEntity(Object)}.
     *
     * @param id                The identifier of the entity to create.
     * @param firstEventMessage The first event message to be applied to the entity.
     * @return A new instance of the entity.
     */
    default E createEntityBasedOnFirstEventMessage(@Nonnull ID id,
                                                   @Nonnull EventMessage<?> firstEventMessage) {
        return createEmptyEntity(id);
    }
}