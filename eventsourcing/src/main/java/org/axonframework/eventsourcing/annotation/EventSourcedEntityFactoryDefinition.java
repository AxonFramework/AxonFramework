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

/**
 * Defines how an {@link EventSourcedEntityFactory} should be constructed for an {@link EventSourcedEntity} annotated
 * class. The definition received the {@code entityType} and {@code idType} to create a factory for the given types.
 *
 * @param <E>  The type of the entity to create.
 * @param <ID> The type of the identifier of the entity to create.
 * @author Mitchell Herrijgers
 * @see EventSourcedEntity
 * @since 5.0.0
 */
public interface EventSourcedEntityFactoryDefinition<E, ID> {

    /**
     * Creates a new {@link EventSourcedEntityFactory} for the given {@code entityType} and {@code idType}.
     *
     * @param entityType The type of the entity to create.
     * @param idType     The identifier type of the entity to create.
     * @return A new {@link EventSourcedEntityFactory} for the given {@code entityType} and {@code idType}.
     */
    EventSourcedEntityFactory<E, ID> createFactory(@Nonnull Class<E> entityType, @Nonnull Class<ID> idType);
}
