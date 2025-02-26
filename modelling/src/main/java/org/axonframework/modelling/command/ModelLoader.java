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

package org.axonframework.modelling.command;

import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * Loader of a model of type {@code M} with identifier of type {@code ID}.
 *
 * @param <I> The type of the identifier of the model
 * @param <M>  The type of model to load
 */
@FunctionalInterface
public interface ModelLoader<I, M> {

    /**
     * Load the model with the given {@code id} and {@code context}.
     *
     * @param id      The identifier of the model to load
     * @param context The context to load the model in
     * @return a {@link CompletableFuture} which resolves to the model instance
     */
    CompletableFuture<M> load(I id, ProcessingContext context);
}
