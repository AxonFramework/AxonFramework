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

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple implementation of the {@link ModelRegistry} interface. This implementation is thread-safe and allows for
 * registration of model definitions. The model definitions are used to resolve models based on their type and
 * identifier.
 * <p>
 * This implementation's {@link ModelContainer} will cache the loaded models for the duration of the context.
 *
 * @author Mitchell Herrijgers
 * @see ModelRegistry
 * @see ModelContainer
 * @since 5.0.0
 */
public class SimpleModelRegistry implements ModelRegistry, DescribableComponent {

    private final List<ModelDefinition<?, ?>> modelDefinitions = new CopyOnWriteArrayList<>();
    private final String name;

    /**
     * Constructs a new simple {@link ModelRegistry} instance.
     */
    private SimpleModelRegistry(String name) {
        // No direct instantiation
        this.name = name;
    }

    /**
     * Constructs a new simple {@link ModelRegistry} instance.
     *
     * @param name The name of the registry, used for describing it to the {@link DescribableComponent}
     */
    public static SimpleModelRegistry create(String name) {
        return new SimpleModelRegistry(name);
    }

    @Override
    public <I, M> SimpleModelRegistry registerModel(
            Class<I> idClass,
            Class<M> modelClass,
            ModelLoader<I, M> loadFunction) {
        if (!getModelDefinitionsFor(modelClass).isEmpty()) {
            throw new ModelAlreadyRegisteredException(modelClass);
        }
        modelDefinitions.add(new ModelDefinition<>(idClass, modelClass, loadFunction));
        return this;
    }

    @Override
    public ModelContainer modelContainer(ProcessingContext context) {
        return context.computeResourceIfAbsent(ModelContainer.RESOURCE_KEY, () -> new SimplemodelContainer(context));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("modelDefinitions", modelDefinitions);
    }

    /**
     * A definition of a model that can be registered. Used for resolving of the models. Solely used for internal
     * purposes.
     */
    private record ModelDefinition<I, M>(
            Class<I> idClass,
            Class<M> modelClass,
            ModelLoader<I, M> loader
    ) {

    }

    /**
     * A simple implementation of the {@link ModelContainer} interface. This implementation is thread-safe and caches
     * loaded models for the duration of the context.
     */
    private class SimplemodelContainer implements ModelContainer {

        private final ProcessingContext context;
        private final List<LoadedModelDefinition<?, ?>> loadedModels = new CopyOnWriteArrayList<>();

        /**
         * Constructs a new simple {@link ModelContainer} instance.
         *
         * @param context The context this container is bound to
         */
        private SimplemodelContainer(ProcessingContext context) {
            this.context = context;
        }

        @Nonnull
        @Override
        public <M> CompletableFuture<M> getModel(@Nonnull Class<M> modelType, @Nonnull Object identifier) {
            M loadedModel = alreadyLoadedModelOf(modelType, identifier);
            if (loadedModel != null) {
                return CompletableFuture.completedFuture(loadedModel);
            }

            var definitions = getModelDefinitionsFor(modelType);
            if (definitions.isEmpty()) {
                return CompletableFuture.failedFuture(new MissingModelDefinitionException(modelType));
            }
            return loadModel(definitions.getFirst(), identifier);
        }

        /**
         * Loads a model using the given model definition and identifier. The loaded model is then cached in this
         * container.
         *
         * @param definition The model definition to use for loading the model
         * @param id         The identifier of the model to load
         * @param <M>        The type of the model to load
         * @return A {@link CompletableFuture} which resolves to the loaded model
         */
        private <M> CompletableFuture<M> loadModel(ModelDefinition<Object, M> definition, Object id) {
            return definition.loader()
                             .load(id, context)
                             .thenApply(model -> {
                                 loadedModels.add(new LoadedModelDefinition<>(
                                         definition.idClass(),
                                         definition.modelClass(),
                                         id,
                                         model));
                                 return model;
                             });
        }

        /**
         * Retrieves a model already loaded for this container. If the model is not found, null is returned.
         *
         * @param modelType  The type of the model to retrieve
         * @param identifier The identifier of the model to retrieve
         * @param <I>       The type of the identifier
         * @param <M>        The type of the model
         * @return The model if it is already loaded, otherwise null
         */
        private <I, M> M alreadyLoadedModelOf(@Nonnull Class<M> modelType, I identifier) {
            //noinspection unchecked // The cast is checked in the stream
            return loadedModels
                    .stream()
                    .filter(lmd -> lmd.modelClass().equals(modelType))
                    .map(lmd -> (LoadedModelDefinition<I, M>) lmd)
                    .filter(md -> identifier.equals(md.identifier()))
                    .findFirst()
                    .map(LoadedModelDefinition::model)
                    .orElse(null);
        }

        /**
         * Represents an already loaded model for this container. Used to keep track of the loaded models.
         */
        private record LoadedModelDefinition<I, M>(
                Class<I> idClass,
                Class<M> modelClass,
                I identifier,
                M model
        ) {

        }
    }

    /**
     * Get all matching definitions for the given model type.
     *
     * @param modelType The model type to get definitions for
     * @param <M>       The type of the model
     * @return A list of definitions matching the given model type and name
     */
    @SuppressWarnings("unchecked") // The cast is checked in the stream
    private <I, M> List<ModelDefinition<I, M>> getModelDefinitionsFor(
            @Nonnull Class<M> modelType
    ) {
        return modelDefinitions
                .stream()
                .filter(md -> md.modelClass().equals(modelType))
                .map(md -> (ModelDefinition<I, M>) md)
                .toList();
    }
}
