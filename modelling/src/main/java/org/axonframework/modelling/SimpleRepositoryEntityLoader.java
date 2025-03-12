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

import java.util.concurrent.CompletableFuture;

/**
 * Functional interface describing a component capable of loading an entity with the given identifier for the
 * {@link SimpleRepository}. The entity is loaded within the given {@link ProcessingContext}.
 *
 * @param <I> The type of the identifier of the entity.
 * @param <T> The type of the entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@FunctionalInterface
public interface SimpleRepositoryEntityLoader<I, T> {

    /**
     * Load an entity with given {@code id} within the given {@code context}.
     *
     * @param id      The identifier of the entity to load.
     * @param context The context in which the entity should be loaded.
     * @return a CompletableFuture that resolves to the loaded entity.
     */
    CompletableFuture<? extends T> load(@Nonnull I id, @Nonnull ProcessingContext context);
}
