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
 * persist them. Only one {@link AsyncRepository} or load and save function can be registered for a type, as this is
 * considered their identity.
 * <p>
 * Entities are registered by their type, and can be registered by either:
 * <ul>
 *     <li>Registering an {@link AsyncRepository} for a certain type, or</li>
 *     <li>Registering a load and save function for a certain type.</li>
 * </ul>
 * <p>
 * In the latter case, the state manager will create a {@link SimpleEntityAsyncRepository} for the given type with
 * the given load and save functions.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface StateManager {

    /**
     * Registers an {@link AsyncRepository} for state type {@code T} with id of type {@code I}. Only one
     * {@link AsyncRepository} can be registered for a type.
     *
     * @param idType     The type of the identifier.
     * @param stateType  The type of the state.
     * @param repository The {@link AsyncRepository} to use for loading state.
     * @param <I>        The type of id.
     * @param <T>        The type of state.
     * @return This instance for fluent interfacing.
     */
    <I, T> StateManager register(
            @Nonnull Class<I> idType,
            @Nonnull Class<T> stateType,
            @Nonnull AsyncRepository<I, T> repository
    );

    /**
     * Registers a load and save function for state type {@code T} with id of type {@code I}. Creates a
     * {@link SimpleEntityAsyncRepository} for the given type with the given load and save functions.
     *
     * @param idType    The type of the identifier.
     * @param stateType The type of the state.
     * @param loader    The function to load state.
     * @param persister The function to persist state.
     * @param <I>       The type of id.
     * @param <T>       The type of state.
     * @return This instance for fluent interfacing.
     */
    default <I, T> StateManager register(
            @Nonnull Class<I> idType,
            @Nonnull Class<T> stateType,
            @Nonnull SimpleEntityLoader<I, T> loader,
            @Nonnull SimpleEntityPersister<I, T> persister
    ) {
        return register(idType, stateType, new SimpleEntityAsyncRepository<>(idType, stateType, loader, persister));
    }

    /**
     * Retrieves an entity of the given {@code type} and {@code id}. The {@link CompletableFuture} will resolve to
     * state, or complete exceptionally if it could not be resolved.
     * <p>
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
     * @param <E>     The type of the entity.
     * @return a {@link CompletableFuture} which resolves to the model instance.
     */
    <I, E> CompletableFuture<ManagedEntity<I, E>> loadManagedEntity(
            @Nonnull Class<E> type,
            @Nonnull I id,
            @Nonnull ProcessingContext context);

    /**
     * Returns the types of entities that are registered with this {@code StateManager}.
     * @return the types of entities that are registered with this {@code StateManager}.
     */
    Set<Class<?>> registeredTypes();

    /**
     * Returns the {@link AsyncRepository} for the given {@code type}. Throws an exception if no repository is
     * registered for the given type.
     *
     * @param type The type of the entity.
     * @param <T> The type of the entity.
     * @return The {@link AsyncRepository} for the given {@code type}.
     */
    <T> AsyncRepository<?, T> repository(Class<T> type);
}
