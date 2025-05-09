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
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.EventMessage;

/**
 * Functional interface towards creating a new instance of an entity of the given {@code entityType} and the given
 * {@code id}. If you use immutable entities, the {@link #createFromFirstEvent(Object, EventMessage)} method must be
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
     * Creates an “empty” instance of the entity for the given {@code id}.
     * <ul>
     *   <li><strong>Mutable entities:</strong> Must return a non-null, empty instance.</li>
     *   <li><strong>Immutable entities:</strong> You may return {@code null} here <em>only</em> if you also
     *       override {@link #createFromFirstEvent(Object, EventMessage)} to build the entity
     *       from the first event.</li>
     * </ul>
     *
     * @param id The identifier of the entity to create.
     * @return A new, empty instance of the entity, or {@code null} if you defer to the first-event constructor.
     */
    @Nullable
    E createEmptyEntity(@Nonnull ID id);

    /**
     * Creates an instance of the entity from its very first event.
     * <p>
     * Default implementation delegates to {@link #createEmptyEntity(Object)}:
     * <ul>
     *   <li>If that returns non-null, it is returned.</li>
     *   <li>If that returns {@code null}, an {@link IllegalStateException} is thrown,
     *       forcing you to override this method when using immutable entities.</li>
     * </ul>
     * <p>
     * Please note that overriding this method does not imply the {@link org.axonframework.modelling.EntityEvolver} will
     * not be called for the first event. The {@link org.axonframework.modelling.EntityEvolver} will
     * always be called for all events, regardless of the constructor used to create the entity.
     *
     * @param id                The identifier of the entity to create.
     * @param firstEventMessage The very first event in the stream for that entity.
     * @return A new, non-null instance of the entity.
     * @throws IllegalStateException if {@code createEmptyEntity(id)} returned null.
     */
    @Nonnull
    default E createFromFirstEvent(@Nonnull ID id,
                                   @Nonnull EventMessage<?> firstEventMessage) {
        E entity = createEmptyEntity(id);
        if (entity == null) {
            throw new IllegalStateException("The EventSourcedEntityFactory.createEmptyEntity() method can not return a null entity after the first event message has been applied."
                                                    + "Please provide a custom implementation of the createEntityBasedOnFirstEventMessage() method.");
        }
        return entity;
    }
}