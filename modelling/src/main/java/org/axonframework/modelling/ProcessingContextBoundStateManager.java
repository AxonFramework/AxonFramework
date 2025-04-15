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

public class ProcessingContextBoundStateManager {

    private final ProcessingContext processingContext;
    private final StateManager delegate;


    public ProcessingContextBoundStateManager(ProcessingContext processingContext, StateManager delegate) {
        this.processingContext = processingContext;
        this.delegate = delegate;
    }

    @Nonnull
    public <I, T> CompletableFuture<T> loadEntity(
            @Nonnull Class<T> type,
            @Nonnull I id
    ) {
        return delegate.loadManagedEntity(type, id, this.processingContext).thenApply(ManagedEntity::entity);
    }

    /**
     * Retrieves a {@link ManagedEntity} of the given {@code type} and {@code id}. The {@link CompletableFuture} will
     * resolve to a {@link ManagedEntity}, or complete exceptionally if it could not be resolved.
     *
     * @param type The type of state to retrieve.
     * @param id   The id of the state to retrieve.
     * @param <I>  The type of the identifier of the entity.
     * @param <T>  The type of the entity.
     * @return a {@link CompletableFuture} which resolves to the model instance.
     */
    public <I, T> CompletableFuture<ManagedEntity<I, T>> loadManagedEntity(
            @Nonnull Class<T> type,
            @Nonnull I id
    ) {
        return delegate.loadManagedEntity(type, id, processingContext);
    }

    /**
     * The types of entities that are registered with this {@code StateManager}.
     *
     * @return the types of entities that are registered with this {@code StateManager}.
     */
    public Set<Class<?>> registeredEntities() {
        return delegate.registeredEntities();
    }

    /**
     * The types of identifiers that are registered with this {@code StateManager} for the given {@code entityType}.
     *
     * @param entityType The type of the entity.
     * @return the types of identifiers that are registered with this {@code StateManager} for the given
     * {@code entityType}.
     */
    public Set<Class<?>> registeredIdsFor(@Nonnull Class<?> entityType) {
        return delegate.registeredIdsFor(entityType);
    }

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
    public <I, T> AsyncRepository<I, T> repository(@Nonnull Class<T> entityType, @Nonnull Class<I> idType) {
        return delegate.repository(entityType, idType);
    }
}
