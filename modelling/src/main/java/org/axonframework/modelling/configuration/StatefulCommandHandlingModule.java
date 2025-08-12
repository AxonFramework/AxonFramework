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
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.Module;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.stateful.Stateful;
import org.axonframework.modelling.stateful.StatefulDelegatingModule;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public interface StatefulCommandHandlingModule extends CommandHandlingModule, Stateful<CommandHandlingModule> {

    /**
     * Starts a {@code StatefulCommandHandlingModule} module with the given {@code moduleName}.
     *
     * @param moduleName The name of the {@code StatefulCommandHandlingModule} under construction.
     * @return The setup phase of this module, for a fluent API.
     */
    static SetupPhase named(@Nonnull String moduleName) {
        return new SimpleStatefulCommandHandlingModule(moduleName);
    }

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
    }

    interface CommandHandlerPhase extends CommandHandlingModule.CommandHandlerPhase {

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
                                                                         @Nonnull CommandHandler commandHandler){
            requireNonNull(commandHandler, "The command handler cannot be null.");
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
                @Nonnull ComponentBuilder<CommandHandler> commandHandlerBuilder
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
                    c.getComponent(ParameterResolverFactory.class)
            ));
        }
    }

}
