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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A {@link CommandHandlingComponent} implementation which allows for stateful handling of commands. Models can be
 * registered to it, which can be loaded and used by the command handlers.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class StatefulCommandHandlingComponent implements
        CommandHandlingComponent,
        StatefulCommandHandlerRegistry<StatefulCommandHandlingComponent> {

    private final SimpleCommandHandlingComponent handlingComponent;
    private final List<ModelDefinition<?, ?>> modelDefinitions = new CopyOnWriteArrayList<>();
    private boolean loadEagerly = false;

    private StatefulCommandHandlingComponent(
            String name
    ) {
        this.handlingComponent = SimpleCommandHandlingComponent.forComponent(name); // TODO: Name in constructor
    }

    public static StatefulCommandHandlingComponent forName(String name) {
        return new StatefulCommandHandlingComponent(name);
    }

    public StatefulCommandHandlingComponent loadModelsEagerly() {
        loadEagerly = true;
        return this;
    }


    @Override
    public StatefulCommandHandlingComponent subscribe(
            @Nonnull QualifiedName name,
            @Nonnull StatefulCommandHandler commandHandler
    ) {
        handlingComponent.subscribe(name, ((command, context) -> {
            try {
                var models = createModelContainer(command, context);
                return commandHandler.handle(command, models, context);
            } catch (Exception e) {
                return MessageStream.failed(e);
            }
        }));
        return this;
    }

    private ModelContainer createModelContainer(CommandMessage<?> command,
                                                ProcessingContext context) {
        if (loadEagerly) {
            return new EagerLoadingStatefulCommandModelModelContainer(command, context);
        }
        return new LazyLoadingStatefulCommandModelModelContainer(
                command,
                context);
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
    public StatefulCommandHandlingComponent self() {
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
            BiFunction<ID, ProcessingContext, CompletableFuture<M>> loadFunction) {
        if (modelDefinitions.stream().anyMatch(md -> md.name().equals(name) && md.modelClass().equals(modelClass))) {
            throw new IllegalStateException("Model with name " + name + " already registered");
        }
        modelDefinitions.add(new ModelDefinition<>(name, modelClass, commandIdResolver, loadFunction));
        return this;
    }

    /**
     * A definition of a model that can be registered. Used for resolving of the models during execution of the stateful
     * command handlers that are subscribed to this component. Solely used for internal purposes.
     */
    private record ModelDefinition<ID, M>(
            String name,
            Class<M> modelClass,
            Function<CommandMessage<?>, ID> commandIdResolver,
            BiFunction<ID, ProcessingContext, CompletableFuture<M>> loadFunction
    ) {

    }

    /**
     * A container for models that are lazily loaded when requested. This container is used to load models for command
     * handlers that require them.
     */
    private class LazyLoadingStatefulCommandModelModelContainer implements ModelContainer {

        private final CommandMessage<?> command;
        private final ProcessingContext context;

        /**
         * Initializes a new {@link ModelContainer} for the given {@code command} and {@code context} that lazily loads
         * the models.
         *
         * @param command The command to load models for
         * @param context The context in which the command is handled
         */
        private LazyLoadingStatefulCommandModelModelContainer(CommandMessage<?> command, ProcessingContext context) {
            this.command = command;
            this.context = context;
        }

        @Override
        public <T> T modelOf(@Nonnull Class<T> modelType, String name) {
            ModelDefinition<?, T> definition = getModelDefinitionFor(modelType, name);
            return loadModel(definition);
        }

        private <ID, T> T loadModel(ModelDefinition<ID, T> definition) {
            return definition.loadFunction().apply(definition.commandIdResolver().apply(command), context).join();
        }
    }

    /**
     * A container for models that are eagerly loaded before the command handler is invoked.
     */
    private class EagerLoadingStatefulCommandModelModelContainer implements ModelContainer {

        private final CommandMessage<?> command;
        private final ProcessingContext context;
        private final List<LoadedModelDefinition<?, ?>> loadedModels = new CopyOnWriteArrayList<>();

        /**
         * Initializes a new {@link ModelContainer} for the given {@code command} and {@code context} that lazily loads
         * the models. The models are only loaded if the {@link ModelDefinition#commandIdResolver()} returns a non-null
         * value.
         *
         * @param command The command to load models for
         * @param context The context in which the command is handled
         */
        private EagerLoadingStatefulCommandModelModelContainer(CommandMessage<?> command, ProcessingContext context) {
            this.command = command;
            this.context = context;

            modelDefinitions.forEach(this::loadModelIfApplicable);
        }

        private <ID, T> void loadModelIfApplicable(ModelDefinition<ID, T> definition) {
            ID id = definition.commandIdResolver().apply(command);
            if (id != null) {
                T model = loadModel(definition);
                loadedModels.add(new LoadedModelDefinition<>(definition.name(), definition.modelClass(), model));
            }
        }

        @Override
        @SuppressWarnings("unchecked") // The cast is checked in the stream
        public <T> T modelOf(@Nonnull Class<T> modelType, String name) {
            List<? extends LoadedModelDefinition<?, T>> matchingModels = loadedModels
                    .stream()
                    .filter(lmd -> lmd.modelClass().equals(modelType))
                    .map(lmd -> (LoadedModelDefinition<?, T>) lmd)
                    .filter(md -> name == null || md.name().equals(name))
                    .toList();

            if (matchingModels.size() > 1) {
                throw new IllegalStateException("Multiple model definitions found for model type: " + modelType);
            }
            if (matchingModels.isEmpty()) {
                throw new IllegalStateException("No model definition found for model type: " + modelType);
            }
            return matchingModels.getFirst().model();
        }

        private <ID, T> T loadModel(ModelDefinition<ID, T> definition) {
            return definition.loadFunction().apply(definition.commandIdResolver().apply(command), context).join();
        }

        private record LoadedModelDefinition<ID, M>(
                String name,
                Class<M> modelClass,
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
            throw new IllegalStateException("Multiple model definitions found for model type: " + modelType);
        }
        if (definitions.isEmpty()) {
            throw new IllegalStateException("No model definition found for model type: " + modelType);
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
