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
public class SimpleModelRegistry implements ModelRegistry {

    private final List<ModelDefinition<?, ?>> modelDefinitions = new CopyOnWriteArrayList<>();

    /**
     * Constructs a new simple {@link ModelRegistry} instance.
     */
    private SimpleModelRegistry() {
        // No direct instantiation
    }

    /**
     * Constructs a new simple {@link ModelRegistry} instance.
     */
    public static SimpleModelRegistry create() {
        return new SimpleModelRegistry();
    }

    @Override
    public <ID, T> SimpleModelRegistry registerModel(
            Class<ID> idClass,
            Class<T> modelClass,
            ModelLoader<ID, T> loadFunction) {
        if (!getModelDefinitionsFor(modelClass).isEmpty()) {
            throw new IllegalStateException(
                    "Model with type [%s] already registered".formatted(modelClass.getName())
            );
        }
        modelDefinitions.add(new ModelDefinition<>(idClass, modelClass, loadFunction));
        return this;
    }

    @Override
    public ModelContainer modelContainer(ProcessingContext context) {
        return context.computeResourceIfAbsent(ModelContainer.RESOURCE_KEY, () -> new SimplemodelContainer(context));
    }

    /**
     * A definition of a model that can be registered. Used for resolving of the models. Solely used for internal
     * purposes.
     */
    private record ModelDefinition<ID, M>(
            Class<ID> idClass,
            Class<M> modelClass,
            ModelLoader<ID, M> loader
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
        public <T> CompletableFuture<T> getModel(@Nonnull Class<T> modelType, @Nonnull Object identifier) {
            T loadedModel = alreadyLoadedModelOf(modelType, identifier);
            if (loadedModel != null) {
                return CompletableFuture.completedFuture(loadedModel);
            }

            ModelDefinition<Object, T> definition = getModelDefinitionFor(modelType);
            return loadModel(definition, identifier);
        }

        /**
         * Loads a model using the given model definition and identifier. The loaded model is then cached in this
         * container.
         *
         * @param definition The model definition to use for loading the model
         * @param id         The identifier of the model to load
         * @param <T>        The type of the model to load
         * @return A {@link CompletableFuture} which resolves to the loaded model
         */
        private <T> CompletableFuture<T> loadModel(ModelDefinition<Object, T> definition, Object id) {
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
         * Retrieves a model that is already loaded for this container. If the model is not found, null is returned.
         *
         * @param modelType  The type of the model to retrieve
         * @param identifier The identifier of the model to retrieve
         * @param <ID>       The type of the identifier
         * @param <T>        The type of the model
         * @return The model if it is already loaded, otherwise null
         */
        private <ID, T> T alreadyLoadedModelOf(@Nonnull Class<T> modelType, ID identifier) {
            //noinspection unchecked // The cast is checked in the stream
            return loadedModels
                    .stream()
                    .filter(lmd -> lmd.modelClass().equals(modelType))
                    .map(lmd -> (LoadedModelDefinition<ID, T>) lmd)
                    .filter(md -> identifier.equals(md.identifier()))
                    .findFirst()
                    .map(LoadedModelDefinition::model)
                    .orElse(null);
        }

        /**
         * Represents an already loaded model for this container. Used to keep track of the loaded models.
         */
        private record LoadedModelDefinition<ID, M>(
                Class<ID> idClass,
                Class<M> modelClass,
                ID identifier,
                M model
        ) {

        }
    }

    /**
     * Retrieves exactly one model definition for the given model type. Will throw and exception if no model definition
     * is found.
     *
     * @param modelType The model type to get the definition for
     * @param <T>       The type of the model
     * @return The model definition for the given model type and name
     */
    @Nonnull
    private <T> ModelDefinition<Object, T> getModelDefinitionFor(
            @Nonnull Class<T> modelType
    ) {
        var definitions = getModelDefinitionsFor(modelType);

        if (definitions.isEmpty()) {
            throw new IllegalStateException(
                    "No model definition found for model type: %s".formatted(modelType)
            );
        }
        return definitions.getFirst();
    }

    /**
     * Get all matching definitions for the given model type.
     *
     * @param modelType The model type to get definitions for
     * @param <T>       The type of the model
     * @return A list of definitions matching the given model type and name
     */
    @SuppressWarnings("unchecked") // The cast is checked in the stream
    private <ID, T> List<? extends ModelDefinition<ID, T>> getModelDefinitionsFor(
            @Nonnull Class<T> modelType
    ) {
        return modelDefinitions
                .stream()
                .filter(md -> md.modelClass().equals(modelType))
                .map(md -> (ModelDefinition<ID, T>) md)
                .toList();
    }
}
