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

package org.axonframework.modelling.repository;

import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * The {@link AsyncRepository} provides an abstraction for the storage of entities.
 * <p>
 * When interacting with the {@code Repository} the framework expects an active {@link ProcessingContext}. If there is
 * no active {@code UnitOfWork} an {@link IllegalStateException} is thrown.
 *
 * @param <T>  The type of entity this repository stores.
 * @param <ID> The type of identifier for entities in this repository.
 * @author Allard Buijze
 * @implNote Implementations of this interface must implement {@link AsyncRepository.LifecycleManagement} instead.
 * @since 0.1
 * TODO rename to Repository once the previous repository is removed
 */
public sealed interface AsyncRepository<ID, T>
        extends DescribableComponent
        permits AsyncRepository.LifecycleManagement {

    /**
     * The type of entity stored in this repository.
     *
     * @return The type of entity stored in this repository.
     */
    @Nonnull
    Class<T> entityType();

    /**
     * The type of the identifier used to identify entities in this repository.
     *
     * @return The type of the identifier used to identify entities in this repository.
     */
    @Nonnull
    Class<ID> idType();

    /**
     * Load the entity with the given unique identifier. No version checks are done when loading an entity, meaning that
     * concurrent access will not be checked for.
     *
     * @param identifier        The identifier of the entity to load.
     * @param processingContext The processing context in which to manage the lifecycle of the entity.
     * @return A {@link CompletableFuture} resolving to the {@link ManagedEntity} with the given identifier, or
     * {@code null} if it can't be found.
     */
    CompletableFuture<ManagedEntity<ID, T>> load(@Nonnull ID identifier,
                                                         @Nonnull ProcessingContext processingContext);

    /**
     * Loads an entity from the repository.
     *
     * @param identifier        The identifier of the entity to load.
     * @param processingContext The processing context in which to manage the lifecycle of the entity.
     * @return A {@link CompletableFuture} resolving to the {@link ManagedEntity} with the given identifier, or a newly
     * constructed entity instance based on the {@code factoryMethod}.
     */
    CompletableFuture<ManagedEntity<ID, T>> loadOrCreate(@Nonnull ID identifier,
                                                         @Nonnull ProcessingContext processingContext);

    /**
     * Persists the given {@code entity} in this repository
     *
     * @param identifier        The identifier of the entity.
     * @param entity            The current state of the entity to store.
     * @param processingContext The {@link ProcessingContext} in which the entity is active.
     * @return a {@link ManagedEntity} wrapping the entity managed in the {@link ProcessingContext}.
     */
    ManagedEntity<ID, T> persist(@Nonnull ID identifier,
                                 @Nonnull T entity,
                                 @Nonnull ProcessingContext processingContext);

    /**
     * Specialization of the {@link AsyncRepository} interface that <em>must</em> be implemented by all implementations
     * of the {@code AsyncRepository}. It exposes some methods that are required to perform lifecycle management
     * operations that are not typically required outside of repository implementation.
     * <p>
     * More specifically, these methods are meant for implementations of a Repository wrapping another to be able to
     * properly have lifecycle operations registered with downstream {@code AsyncRepository} implementations.
     *
     * @param <T>  The type of entity this repository stores.
     * @param <ID> The type of identifier for entities in this repository.
     */
    non-sealed interface LifecycleManagement<ID, T> extends AsyncRepository<ID, T> {

        /**
         * Ensures that the given {@code entity} has its lifecycle managed in the given {@code processingContext}. This
         * ensures that when the {@code processingContext} commits, any changes detected in the entity state are
         * persisted in this repository's underlying storage, if present.
         * <p>
         * If a managed entity for this identifier was already present in the {@link ProcessingContext}, the new
         * instance will replace it.
         * <p>
         * Repositories may wrap entities. In that case, the returned instance may not be exactly the same (`==`
         * comparison) as the instance provided. It is always recommended to use the returned instance.
         *
         * @param entity            The entity to have its lifecycle attached to the given processing context.
         * @param processingContext The processing context to link the lifecycle with.
         * @return The instance of the entity whose lifecycle is managed by this repository.
         */
        ManagedEntity<ID, T> attach(@Nonnull ManagedEntity<ID, T> entity, @Nonnull ProcessingContext processingContext);
    }
}
