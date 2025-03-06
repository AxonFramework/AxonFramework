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

import java.util.concurrent.CompletableFuture;

/**
 * The {@code StateManager} enables applications to load state of a certain type (Java class), based on an id. To be
 * able to load state, the {@code StateManager} requires an {@link AsyncRepository} to be registered first.
 * <p>
 * If no {@link AsyncRepository} is registered for a certain type, the {@code StateManager} will throw a
 * {@link MissingRepositoryException}. Only one {@link AsyncRepository} can be registered for a type.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface StateManager {

    /**
     * Registers an {@link AsyncRepository} for state type {@code M} with id of type {@code I}.
     * <p>
     * Only one {@link AsyncRepository} can be registered for a type.
     *
     * @param idType     The type of the identifier.
     * @param stateType  The type of the state.
     * @param repository The {@link AsyncRepository} to use for loading state.
     * @param <I>        The type of id.
     * @param <M>        The type of state.
     * @return This instance for fluent interfacing.
     */
    <I, M> StateManager register(
            @Nonnull Class<I> idType,
            @Nonnull Class<M> stateType,
            @Nonnull AsyncRepository<I, M> repository
    );


    /**
     * Retrieves state of the given {@code type} and {@code id}. The {@link CompletableFuture} will resolve to state, or
     * complete exceptionally if it could not be resolved.
     *
     * @param <M>     The type of state to retrieve.
     * @param type    The type of state to retrieve.
     * @param id      The id of the state to retrieve.
     * @param context The {@link ProcessingContext context} to load the model in.
     * @return a {@link CompletableFuture} which resolves to the model instance.
     */
    @Nonnull
    <I, M> CompletableFuture<M> load(@Nonnull Class<M> type, @Nonnull I id, ProcessingContext context);
}
