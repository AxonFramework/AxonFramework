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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A {@link CommandHandlingComponent} implementation which allows for stateful handling of commands.
 * <p>
 * Models can be registered to this component through the {@link #registerModel(String, Class, Function, ModelLoader)}
 * method. These models can be resolved during the handling of commands by the {@link StatefulCommandHandler StatefulCommandHandlers}
 * that are subscribed to this component. The models are resolved based on the command that is being handled.
 * <p>
 * Can load the models eagerly, meaning that all models are loaded when the command is handled and the resolved
 * identifier is non-null, or lazily, meaning that
 * the models are only loaded when they are requested.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class StatefulCommandHandlingComponent implements
        CommandHandlingComponent,
        StatefulCommandHandlerRegistry<StatefulCommandHandlingComponent>,
        DescribableComponent {

    private final String name;
    private final SimpleCommandHandlingComponent handlingComponent;
    private final List<ModelDefinition<?, ?>> modelDefinitions = new CopyOnWriteArrayList<>();
    private boolean loadModelsEagerly = false;

    /**
     * Initializes a new stateful command handling component with the given {@code name}.
     * @param name The name of the component
     */
    private StatefulCommandHandlingComponent(String name) {
        this.name = name;
        this.handlingComponent = SimpleCommandHandlingComponent.forComponent(name);
    }

    /**
     * Creates a new stateful command handling component with the given {@code name}.
     * @param name The name of the component
     * @return A new stateful command handling component with the given {@code name}
     */
    public static StatefulCommandHandlingComponent forName(String name) {
        return new StatefulCommandHandlingComponent(name);
    }

    /**
     * Tells this component to load models eagerly. This means that all models are loaded when the command is handled
     * and the resolved identifier is non-null.
     *
     * @return this component for fluent interfacing
     */
    public StatefulCommandHandlingComponent loadModelsEagerly() {
        loadModelsEagerly = true;
        return this;
    }


    @Override
    public StatefulCommandHandlingComponent subscribe(
            @Nonnull QualifiedName name,
            @Nonnull StatefulCommandHandler commandHandler
    ) {
        handlingComponent.subscribe(name, ((command, context) -> {
            try {
                var models = new StatefulCommandModelModelContainer(
                        command,
                        context,
                        loadModelsEagerly
                );
                return commandHandler.handle(command, models, context);
            } catch (Exception e) {
                return MessageStream.failed(e);
            }
        }));
        return this;
    }

    @Nonnull
    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                   @Nonnull ProcessingContext context) {
        return handlingComponent.handle(command, context);
    }

    @Override
    public StatefulCommandHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                      @Nonnull CommandHandler commandHandler) {
        handlingComponent.subscribe(name, commandHandler);
        return this;
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return handlingComponent.supportedCommands();
    }

    @Override
    public <ID, M> StatefulCommandHandlingComponent registerModel(
            String name,
            Class<M> modelClass,
            Function<CommandMessage<?>, ID> commandIdResolver,
            ModelLoader<ID, M> loadFunction) {
        if (modelDefinitions.stream().anyMatch(md -> md.name().equals(name) && md.modelClass().equals(modelClass))) {
            throw new IllegalStateException("Model with name " + name + " already registered");
        }
        modelDefinitions.add(new ModelDefinition<>(name, modelClass, commandIdResolver, loadFunction));
        return this;
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("loadModelsEagerly", loadModelsEagerly);
        descriptor.describeProperty("handlingComponent", handlingComponent);
        descriptor.describeProperty("modelDefinitions", modelDefinitions);
    }

    /**
     * A definition of a model that can be registered. Used for resolving of the models during execution of the stateful
     * command handlers that are subscribed to this component. Solely used for internal purposes.
     */
    private record ModelDefinition<ID, M>(
            String name,
            Class<M> modelClass,
            Function<CommandMessage<?>, ID> commandIdResolver,
            ModelLoader<ID, M> loader
    ) {

    }

    /**
     * A container for models that either loads models lazily or eagerly. Used during the handling of commands by the
     * stateful command handlers.
     * <p>
     * Any defined model will only be initialized once per command handling process.
     */
    private class StatefulCommandModelModelContainer implements ModelContainer {

        private final CommandMessage<?> command;
        private final ProcessingContext context;
        private final List<LoadedModelDefinition<?, ?>> loadedModels = new CopyOnWriteArrayList<>();

        /**
         * Initializes a new {@link ModelContainer} for the given {@code command} and {@code context} that lazily loads
         * the models.
         *
         * @param command The command to load models for
         * @param context The context in which the command is handled
         */
        private StatefulCommandModelModelContainer(CommandMessage<?> command, ProcessingContext context,
                                                   boolean loadEagerly) {
            this.command = command;
            this.context = context;

            if (loadEagerly) {
                modelDefinitions.forEach(this::eagerLoadModelIfHasIdentifier);
            }
        }

        @Override
        public <T> T modelOf(@Nonnull Class<T> modelType, String name) {
            T loadedModel = alreadyLoadedModelOf(modelType, name);
            if (loadedModel != null) {
                return loadedModel;
            }

            ModelDefinition<?, T> definition = getModelDefinitionFor(modelType, name);
            return loadModel(definition);
        }

        private <ID, T> T loadModel(ModelDefinition<ID, T> definition) {
            ID id = definition.commandIdResolver().apply(command);
            if (id == null) {
                throw new IllegalStateException(
                        "No identifier found for model of type " + definition.modelClass() + " with name "
                                + definition.name());
            }
            T model = definition.loader().load(id, context).join();
            loadedModels.add(new LoadedModelDefinition<>(definition.name(), definition.modelClass(), id, model));
            return model;
        }

        private <ID, T> void eagerLoadModelIfHasIdentifier(ModelDefinition<ID, T> definition) {
            ID id = definition.commandIdResolver().apply(command);
            if (id != null) {
                loadModel(definition);
            }
        }

        private <T> T alreadyLoadedModelOf(@Nonnull Class<T> modelType, String name) {
            //noinspection unchecked // The cast is checked in the stream
            List<? extends LoadedModelDefinition<?, T>> matchingLoadedModel = loadedModels
                    .stream()
                    .filter(lmd -> lmd.modelClass().equals(modelType))
                    .map(lmd -> (LoadedModelDefinition<?, T>) lmd)
                    .filter(md -> name == null || md.name().equals(name))
                    .toList();
            if (matchingLoadedModel.size() > 1) {
                throw new IllegalStateException("Multiple model definitions found for model type: %s%s".formatted(
                        modelType, name != null ? (" and name: " + name) : "")
                );
            }
            if (matchingLoadedModel.isEmpty()) {
                throw new IllegalStateException(
                        "No model definition found for model type: %s and name: %s".formatted(
                                modelType, name != null ? (" and name: " + name) : "")
                );
            }
            return matchingLoadedModel.getFirst().model();
        }

        /**
         * Represents an already loaded model for this container. Used to keep track of the loaded models.
         */
        private record LoadedModelDefinition<ID, M>(
                String name,
                Class<M> modelClass,
                ID resolverIdentifier,
                M model
        ) {

        }
    }

    /**
     * Retrieves exactly one model definition for the given model type and name. The name can be null to get the model
     * definition solely based on the model type. Will throw and exception if no model definition is found, or if
     * multiple model definitions are found.
     *
     * @param modelType The model type to get the definition for
     * @param name      The name of the model to get the definition for. Can be null to get the definition solely based
     *                  on the model type.
     * @param <T>       The type of the model
     * @return The model definition for the given model type and name
     */
    @Nonnull
    private <T> ModelDefinition<?, T> getModelDefinitionFor(@Nonnull Class<T> modelType, @Nullable String name) {
        var definitions = getModelDefinitionsFor(modelType, name);

        if (definitions.size() > 1) {
            throw new IllegalStateException(
                    "Multiple model definitions found for model type: %s and name: %s".formatted(
                            modelType, name != null ? (" and name: " + name) : ""));
        }
        if (definitions.isEmpty()) {
            throw new IllegalStateException(
                    "No model definition found for model type: %s and name: %s".formatted(
                            modelType, name != null ? (" and name: " + name) : "")
            );
        }
        return definitions.getFirst();
    }

    /**
     * Get all matching definitions for the given model type and name. If the name is null, all definitions for the
     * given model type are returned.
     *
     * @param modelType The model type to get definitions for
     * @param name      The name of the model to get definitions for. Can be null to return all definitions for the
     *                  given model type.
     * @param <T>       The type of the model
     * @return A list of definitions matching the given model type and name
     */
    @SuppressWarnings("unchecked") // The cast is checked in the stream
    private <T> List<? extends ModelDefinition<?, T>> getModelDefinitionsFor(
            @Nonnull Class<T> modelType,
            @Nullable String name) {
        return modelDefinitions
                .stream()
                .filter(md -> md.modelClass().equals(modelType))
                .map(md -> (ModelDefinition<?, T>) md)
                .filter(md -> name == null || md.name().equals(name))
                .toList();
    }
}
