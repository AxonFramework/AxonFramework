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

package org.axonframework.modelling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.AsyncRepository;
import org.axonframework.modelling.repository.ManagedEntity;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code StateManager} enables applications to load entities based on the type of the entity and an id, and to
 * persist them.
 * <p>
 * Entities are registered by their type in combination with their id. In case an id is given that matches
 * two repositories, the repository that is registered for the most specific type is used. If no repository is found
 * for the given type and id, a {@link MissingRepositoryException} is thrown.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface StateManager {

    /**
     * Retrieves an entity of the given {@code type} and {@code id}. The {@link CompletableFuture} will resolve to
     * the entity, or complete exceptionally if it could not be resolved.
     * <p>
     * If multiple repositories are registered for the given {@code entityType} that can handle the given {@code id}
     * (through superclass registration), the most specific repository is used.
     *
     * @param <T>     The type of state to retrieve.
     * @param type    The type of state to retrieve.
     * @param id      The id of the state to retrieve.
     * @param context The {@link ProcessingContext context} to load the model in.
     * @return a {@link CompletableFuture} which resolves to the model instance.
     */
    @Nonnull
    default <I, T> CompletableFuture<T> loadEntity(
            @Nonnull Class<T> type,
            @Nonnull I id,
            @Nonnull ProcessingContext context
    ) {
        return loadManagedEntity(type, id, context).thenApply(ManagedEntity::entity);
    }

    /**
     * Retrieves a {@link ManagedEntity} of the given {@code type} and {@code id}. The {@link CompletableFuture} will
     * resolve to a {@link ManagedEntity}, or complete exceptionally if it could not be resolved.
     *
     * @param type    The type of state to retrieve.
     * @param id      The id of the state to retrieve.
     * @param context The {@link ProcessingContext context} to load the model in.
     * @param <I>     The type of the identifier of the entity.
     * @param <T>     The type of the entity.
     * @return a {@link CompletableFuture} which resolves to the model instance.
     */
    <I, T> CompletableFuture<ManagedEntity<I, T>> loadManagedEntity(
            @Nonnull Class<T> type,
            @Nonnull I id,
            @Nonnull ProcessingContext context);

    /**
     * The types of entities that are registered with this {@code StateManager}.
     * @return the types of entities that are registered with this {@code StateManager}.
     */
    Set<Class<?>> registeredEntities();

    /**
     * The types of identifiers that are registered with this {@code StateManager} for the given {@code entityType}.
     *
     * @param entityType The type of the entity.
     * @return the types of identifiers that are registered with this {@code StateManager} for the given
     * {@code entityType}.
     */
    Set<Class<?>> registeredIdsFor(@Nonnull Class<?> entityType);

    /**
     * Returns the {@link AsyncRepository} for the given {@code type}. Returns {@code null} if no repository is
     * registered for the given type and id.
     *
     * @param <I>        The type of the identifier of the entity.
     * @param <T>        The type of the entity.
     * @param entityType The type of the entity.
     * @param idType     The type of the identifier of the entity.
     * @return The {@link AsyncRepository} for the given {@code idType} and {@code entityType}.
     */
    <I, T> AsyncRepository<I, T> repository(@Nonnull Class<T> entityType, @Nonnull Class<I> idType);
}
