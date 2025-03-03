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
import org.axonframework.messaging.Context;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * A container of different models that can be accessed using the model type and an identifier. The implementation may
 * choose to cache models or retrieve them from a store on each invocation.
 * <p>
 * The container is not in charge of model registration, that is the {@link ModelRegistry}. A container can be retrieved
 * for a {@link ProcessingContext} through its {@link ModelRegistry#modelContainer(ProcessingContext)} method.
 *
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@FunctionalInterface
public interface ModelContainer {

    /**
     * The {@link Context.ResourceKey} used to store the {@link ModelContainer} in the {@link ProcessingContext}.
     */
    Context.ResourceKey<ModelContainer> RESOURCE_KEY = Context.ResourceKey.withLabel("ModelContainer");

    /**
     * Retrieves a model of given {@code modelType} and {@code identifier} from the container.
     * The {@link CompletableFuture} will resolve to the model instance, or complete exceptionally if the
     * model could not be found.
     *
     * @param modelType  The type of model to retrieve.
     * @param identifier The identifier of the model to retrieve.
     * @param <M>        The type of model to retrieve.
     * @return a {@link CompletableFuture} which resolves to the model instance.
     */
    @Nonnull
    <M> CompletableFuture<M> getModel(@Nonnull Class<M> modelType, @Nonnull Object identifier);
}
