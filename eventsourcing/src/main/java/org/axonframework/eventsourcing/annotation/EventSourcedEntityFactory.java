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
 * Functional interface towards creating a new instance of an entity of the given {@code entityType} and the given
 * {@code id}.
 *
 * @since 5.0.0
 * @author Mitchell Herrijgers
 * @param <ID> The type of the identifier of the entity to create.
 * @param <E>  The type of the entity to create.
 */
@FunctionalInterface
public interface EventSourcedEntityFactory<ID, E> {

    /**
     * Creates a new instance of an entity of the given {@code entityType} and {@code idType}.
     *
     * @param entityType The type of the entity to create.
     * @param id         The identifier of the entity to create.
     * @return A new instance of the entity.
     */
    E createEntity(@Nonnull Class<E> entityType, @Nonnull ID id);
}