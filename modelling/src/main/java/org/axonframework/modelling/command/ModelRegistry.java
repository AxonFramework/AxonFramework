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

/**
 * Registry of models, which can be used to load models based on their identifier.
 * <p>
 * Models can be retrieved by using the {@link ModelContainer} which is obtained through the
 * {@link #modelContainer(ProcessingContext)} method. This container is responsible for binding the models to the
 * current {@link ProcessingContext}. Depending on the implementation, the container might cache models for the duration
 * of the context.
 * <p>
 * Models can be registered through the {@link #registerModel(Class, Class, ModelLoader)} method. The
 * {@link ModelLoader} is used to load the model based on the identifier whenever it's requested via the
 * {@link ModelContainer}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface ModelRegistry {

    /**
     * Registers a model of type {@code M} with identifier of type {@code ID} and a {@link ModelLoader} to load the
     * model based on the identifier.
     *
     * @param idClass      The class of the identifier
     * @param modelClass   The class of the model
     * @param loadFunction The function to load the model based on the identifier
     * @param <ID>         The type of the identifier of the model
     * @param <M>          The type of model to load
     * @return The {@link ModelRegistry} instance for fluent interfacing
     */
    <ID, M> ModelRegistry registerModel(
            Class<ID> idClass,
            Class<M> modelClass,
            ModelLoader<ID, M> loadFunction
    );

    /**
     * Retrieve the {@link ModelContainer} for the given {@link ProcessingContext}.
     * Should return the same container for the same context.
     *
     * @param context The context to retrieve the container for
     * @return The container for the given context
     */
    ModelContainer modelContainer(ProcessingContext context);
}
