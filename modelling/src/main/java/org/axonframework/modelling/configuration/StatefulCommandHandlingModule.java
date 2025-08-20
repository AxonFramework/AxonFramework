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

package org.axonframework.modelling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.StatefulCommandHandler;
import org.axonframework.serialization.Converter;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.axonframework.configuration.MessagingConfigurationDefaults.MESSAGE_CONVERTER_NAME;

/**
 * A {@link Module} and {@link ModuleBuilder} implementation providing operation to construct a stateful command
 * handling application module.
 * <p>
 * The {@code StatefulCommandHandlingModule} follows a builder paradigm, wherein several
 * {@link EntityModule entity modules} and {@link StatefulCommandHandler StatefulCommandHandlers} can be registered in
 * either order. Entities defined in the registered entity modules will be available in the
 * {@link org.axonframework.modelling.StateManager} of the stateful command handlers defined in this module.
 * <p>
 * To initiate entity registration, you should move into the entity registration phase by invoking
 * {@link SetupPhase#entities()}. To register command handlers, a similar registration phase switch should be made, by
 * invoking {@link SetupPhase#commandHandlers()}.
 * <p>
 * Here's an example of how to register two stateful command handler lambdas, one state-based entity with a repository
 * and another state-based entity using a separate loader and persister:
 * <pre>
 * StatefulCommandHandlingModule.named("my-module")
 *                              .entities()
 *                              .entity(StateBasedEntityModule.declarative(CourseId.class, Course.class)
 *                                                            .repository(config -> ...))
 *                              .entity(StateBasedEntityModule.declarative(StudentId.class, Student.class)
 *                                                            .loader(config -> ...)
 *                                                            .persister(config -> ...))
 *                              .commandHandlers()
 *                              .commandHandler(new QualifiedName(RenameCourseCommand.class),
 *                                              (cmd, course, context) -> { ...command handling logic... })
 *                              .commandHandler(new QualifiedName(ChangeCourseClassRoomCommand.class),
 *                                              (cmd, entity, context) -> { ...command handling logic... });
 * </pre>
 * <p>
 * Note that users do not have to invoke {@link #build()} themselves when using this interface, as the
 * {@link org.axonframework.configuration.ApplicationConfigurer} takes care of that.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface StatefulCommandHandlingModule extends Module, ModuleBuilder<StatefulCommandHandlingModule> {

    /**
     * Starts a {@code StatefulCommandHandlingModule} module with the given {@code moduleName}.
     *
     * @param moduleName The name of the {@code StatefulCommandHandlingModule} under construction.
     * @return The setup phase of this module, for a fluent API.
     */
    static SetupPhase named(@Nonnull String moduleName) {
        return new SimpleStatefulCommandHandlingModule(moduleName);
    }

    /**
     * The setup phase of the stateful command handling module.
     * <p>
     * Allows for two paths when building a stateful command handling module. Firstly, the {@link #commandHandlers()}
     * method allows users to start configuring all the {@link StatefulCommandHandler StatefulCommandHandlers} for this
     * module. The second option allows for moving to the {@link #entities() entity} registration flow of this module.
     */
    interface SetupPhase {

        /**
         * Initiates the command handler configuration phase for this module.
         *
         * @return The command handler phase of this module, for a fluent API.
         */
        CommandHandlerPhase commandHandlers();

        /**
         * Initiates the command handler configuration phase for this module, as well as performing the given
         * {@code configurationLambda} within this phase.
         *
         * @param configurationLambda A consumer of the command handler phase, performing command handler configuration
         *                            right away.
         * @return The command handler phase of this module, for a fluent API.
         */
        default CommandHandlerPhase commandHandlers(@Nonnull Consumer<CommandHandlerPhase> configurationLambda) {
            CommandHandlerPhase commandHandlerPhase = commandHandlers();
            requireNonNull(configurationLambda, "The command handler configuration lambda cannot be null.")
                    .accept(commandHandlerPhase);
            return commandHandlerPhase;
        }

        /**
         * Initiates the entity configuration phase for this module.
         *
         * @return The entity phase of this module, for a fluent API.
         */
        EntityPhase entities();

        /**
         * Initiates the entity configuration phase for this module, as well as performing the given
         * {@code configurationLambda} within this phase.
         *
         * @param configurationLambda A consumer of the entity phase, performing entity configuration right away.
         * @return The setup phase of this module, for a fluent API.
         */
        default EntityPhase entities(@Nonnull Consumer<EntityPhase> configurationLambda) {
            EntityPhase entityPhase = entities();
            requireNonNull(configurationLambda, "The entity configuration lambda cannot be null.")
                    .accept(entityPhase);
            return entityPhase;
        }
    }

    /**
     * The command handler configuration phase of the stateful command handling module.
     * <p>
     * Every registered {@link StatefulCommandHandler} will be subscribed with the
     * {@link org.axonframework.commandhandling.CommandBus} of the
     * {@link org.axonframework.configuration.ApplicationConfigurer} this module is given to.
     * <p>
     * Provides roughly two options for configuring stateful command handlers. Firstly, a stateful command handler can
     * be registered as is, through the {@link #commandHandler(QualifiedName, StatefulCommandHandler)} method. Secondly,
     * if the stateful command handler provides components from the {@link Configuration}, a
     * {@link ComponentBuilder builder} of the stateful command handler can be registered through the
     * {@link #commandHandler(QualifiedName, ComponentBuilder)} method.
     */
    interface CommandHandlerPhase extends SetupPhase, ModuleBuilder<StatefulCommandHandlingModule> {

        /**
         * Registers the given {@code commandHandler} for the given qualified {@code commandName} within this module.
         * <p>
         * Use this command handler registration method when the command handler in question does not require entities
         * or receives entities through another mechanism.
         * <p>
         * Once this module is finalized, the command handler will be subscribed with the
         * {@link org.axonframework.commandhandling.CommandBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
         *
         * @param commandName    The qualified name of the command the given {@code commandHandler} can handle.
         * @param commandHandler The stateful command handler to register with this module.
         * @return The command handler phase of this builder, for a fluent API.
         */
        default CommandHandlerPhase commandHandler(@Nonnull QualifiedName commandName,
                                                   @Nonnull CommandHandler commandHandler) {
            requireNonNull(commandHandler, "The command handler cannot be null.");
            return commandHandler(commandName, (command, state, context) -> commandHandler.handle(command, context));
        }

        /**
         * Registers the given stateful {@code commandHandler} for the given qualified {@code commandName} within this
         * module.
         * <p>
         * Once this module is finalized, the stateful command handler will be subscribed with the
         * {@link org.axonframework.commandhandling.CommandBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
         *
         * @param commandName    The qualified name of the command the given {@code commandHandler} can handle.
         * @param commandHandler The stateful command handler to register with this module.
         * @return The command handler phase of this builder, for a fluent API.
         */
        default CommandHandlerPhase commandHandler(@Nonnull QualifiedName commandName,
                                                   @Nonnull StatefulCommandHandler commandHandler) {
            requireNonNull(commandName, "The command name cannot be null.");
            requireNonNull(commandHandler, "The stateful command handler cannot be null.");
            return commandHandler(commandName, c -> commandHandler);
        }

        /**
         * Registers the given {@code commandHandlerBuilder} for the given qualified {@code commandName} within this
         * module.
         * <p>
         * Once this module is finalized, the stateful command handler from the {@code commandHandlerBuilder} will be
         * subscribed with the {@link org.axonframework.commandhandling.CommandBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
         *
         * @param commandName           The qualified name of the command the {@link StatefulCommandHandler} created by
         *                              the given {@code commandHandlerBuilder}.
         * @param commandHandlerBuilder A builder of a {@link StatefulCommandHandler}. Provides the
         *                              {@link Configuration} to retrieve components from to use during construction of
         *                              the stateful command handler.
         * @return The command handler phase of this builder, for a fluent API.
         */
        CommandHandlerPhase commandHandler(
                @Nonnull QualifiedName commandName,
                @Nonnull ComponentBuilder<StatefulCommandHandler> commandHandlerBuilder
        );

        /**
         * Registers the given {@code handlingComponentBuilder} within this module.
         * <p>
         * Use this command handler registration method when the command handling component in question does not require
         * entities or receives entities through another mechanism.
         * <p>
         * Once this module is finalized, the resulting {@link CommandHandlingComponent} from the
         * {@code handlingComponentBuilder} will be subscribed with the
         * {@link org.axonframework.commandhandling.CommandBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
         *
         * @param handlingComponentBuilder A builder of a {@link CommandHandlingComponent}. Provides the
         *                                 {@link Configuration} to retrieve components from to use during construction
         *                                 of the command handling component.
         * @return The command handler phase of this builder, for a fluent API.
         */
        CommandHandlerPhase commandHandlingComponent(
                @Nonnull ComponentBuilder<CommandHandlingComponent> handlingComponentBuilder
        );

        /**
         * Registers the given {@code handlingComponentBuilder} as an {@link AnnotatedCommandHandlingComponent} within
         * this module.
         * <p>
         * This will scan the given {@code handlingComponentBuilder} for methods annotated with {@link CommandHandler}
         * and register them as command handlers for the {@link org.axonframework.commandhandling.CommandBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer}.
         *
         * @param handlingComponentBuilder A builder of a {@link CommandHandlingComponent}. Provides the
         *                                 {@link Configuration} to retrieve components from to use during construction
         *                                 of the command handling component.
         * @return The command handler phase of this builder, for a fluent API.
         */
        default CommandHandlerPhase annotatedCommandHandlingComponent(
                @Nonnull ComponentBuilder<Object> handlingComponentBuilder
        ) {
            requireNonNull(handlingComponentBuilder, "The handling component builder cannot be null.");
            return commandHandlingComponent(c -> new AnnotatedCommandHandlingComponent<>(
                    handlingComponentBuilder.build(c),
                    c.getComponent(ParameterResolverFactory.class),
                    c.getComponent(Converter.class, MESSAGE_CONVERTER_NAME)
            ));
        }
    }

    /**
     * The entity phase of the stateful command handling module, where {@link EntityModule entity modules} can be
     * registered. The entities defined in the registered modules will be available in the
     * {@link org.axonframework.modelling.StateManager} of the stateful command handlers defined in this module.
     */
    interface EntityPhase extends SetupPhase, ModuleBuilder<StatefulCommandHandlingModule> {

        /**
         * Registers the given {@code entityModule} with this module. This will make the entity available in the
         * {@link org.axonframework.modelling.StateManager} of the stateful command handlers defined in this module.
         *
         * @param entityModule The entity module to register with this module.
         * @param <I>          The type of identifier used to identify the entity that's being built.
         * @param <E>          The type of the entity being built.
         * @return The entity phase of this module, for a fluent API.
         */
        <I, E> EntityPhase entity(@Nonnull EntityModule<I, E> entityModule);
    }
}
