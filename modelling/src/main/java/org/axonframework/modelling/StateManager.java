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
import org.axonframework.modelling.repository.ManagedEntity;
import org.axonframework.modelling.repository.Repository;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code StateManager} enables applications to load entities based on the type of the entity and an id, and to
 * persist them. Implementations may specify whether they load entities through
 * {@link Repository#load(Object, ProcessingContext)} or {@link Repository#loadOrCreate(Object, ProcessingContext)}.
 * <p>
 * Entities are {@link #register(Repository) registered} by their type in combination with their id. The combination of
 * {@link Repository#entityType() entity type} and {@link Repository#idType() id type} of all repositories must be
 * unique and unambiguous. This means you cannot register a repository if another conflicting repository already exists.
 * If you do, a {@link ConflictingRepositoryAlreadyRegisteredException} will be thrown. Note that superclasses and
 * subclasses of each other are considered conflicting.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface StateManager {

    /**
     * Registers an {@link Repository} for use with this {@code StateManager}. The combination of
     * {@link Repository#entityType()} and {@link Repository#idType()} must be unique for all registered repositories.
     * If a repository with the same combination is already registered, a
     * {@link ConflictingRepositoryAlreadyRegisteredException} is thrown.
     *
     * @param repository The {@link Repository} to use for loading state.
     * @param <I>        The type of id.
     * @param <T>        The type of the entity.
     * @return This {@code StateManager} for fluent interfacing.
     */
    <I, T> StateManager register(Repository<I, T> repository);

    /**
     * Registers a load and save function for state type {@code T} with id of type {@code I}. Creates a
     * {@link SimpleRepository} for the given type with the given load and save functions.
     *
     * @param idType     The type of the identifier.
     * @param entityType The type of the state.
     * @param loader     The function to load state.
     * @param persister  The function to persist state.
     * @param <I>        The type of id.
     * @param <T>        The type of state.
     * @return This {@code StateManager} for fluent interfacing.
     */
    default <I, T> StateManager register(Class<I> idType,
                                         Class<T> entityType,
                                         SimpleRepositoryEntityLoader<I, T> loader,
                                         SimpleRepositoryEntityPersister<I, T> persister
    ) {
        return register(new SimpleRepository<>(idType, entityType, loader, persister));
    }

    /**
     * Retrieves an entity of the given {@code type} and {@code id}. The {@link CompletableFuture} will resolve to the
     * entity, or complete exceptionally if it could not be resolved.
     * <p>
     * If multiple repositories are registered for the given {@code entityType} that can handle the given {@code id}
     * (through superclass registration), the most specific repository is used.
     *
     * @param <T>     The type of state to retrieve.
     * @param type    The type of state to retrieve.
     * @param id      The id of the state to retrieve.
     * @param context The {@link ProcessingContext context} to load the entity in.
     * @param <I>     The type of the identifier of the entity.
     * @return a {@link CompletableFuture} which resolves to the entity instance.
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
     * @param context The {@link ProcessingContext context} to load the entity in.
     * @param <I>     The type of the identifier of the entity.
     * @param <T>     The type of the entity.
     * @return a {@link CompletableFuture} which resolves to the entity instance.
     */
    <I, T> CompletableFuture<ManagedEntity<I, T>> loadManagedEntity(
            @Nonnull Class<T> type,
            @Nonnull I id,
            @Nonnull ProcessingContext context);

    /**
     * The types of entities that are registered with this {@code StateManager}.
     *
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
     * Returns the {@link Repository} for the given {@code type}. Returns {@code null} if no repository is registered
     * for the given type and id.
     *
     * @param <I>        The type of the identifier of the entity.
     * @param <T>        The type of the entity.
     * @param entityType The type of the entity.
     * @param idType     The type of the identifier of the entity.
     * @return The {@link Repository} for the given {@code idType} and {@code entityType}.
     */
    <I, T> Repository<I, T> repository(@Nonnull Class<T> entityType, @Nonnull Class<I> idType);
}
