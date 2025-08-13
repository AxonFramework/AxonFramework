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

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Module} and {@link ModuleBuilder} implementation providing operations to construct a stateless command
 * handling application module.
 * <p>
 * The {@code StatelessCommandHandlingModule} follows a builder paradigm, wherein several {@link CommandHandler}
 * instances or {@link CommandHandlingComponent} builders can be registered. Unlike the {@link StatefulCommandHandlingModule},
 * this module does not provide access to a {@link org.axonframework.modelling.StateManager} and is designed for
 * command handlers that don't require entity state management.
 * <p>
 * To register command handlers, invoke {@link #commandHandlers()} to enter the command handler registration phase.
 * <p>
 * Here's an example of how to register stateless command handlers:
 * <pre>
 * StatelessCommandHandlingModule.named("my-stateless-module")
 *                               .commandHandlers()
 *                               .commandHandler(new QualifiedName(CreateOrderCommand.class),
 *                                               (cmd, context) -> { ...command handling logic... })
 *                               .commandHandler(new QualifiedName(SendEmailCommand.class),
 *                                               (cmd, context) -> { ...command handling logic... });
 * </pre>
 * <p>
 * Note that users do not have to invoke {@link #build()} themselves when using this interface, as the
 * {@link org.axonframework.configuration.ApplicationConfigurer} takes care of that.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface StatelessCommandHandlingModule extends Module, ModuleBuilder<StatelessCommandHandlingModule> {

    /**
     * Starts a {@code StatelessCommandHandlingModule} module with the given {@code moduleName}.
     *
     * @param moduleName The name of the {@code StatelessCommandHandlingModule} under construction.
     * @return The command handler phase of this module, for a fluent API.
     */
    static CommandHandlerPhase named(@Nonnull String moduleName) {
        return new SimpleStatelessCommandHandlingModule(moduleName);
    }

    /**
     * The command handler configuration phase of the stateless command handling module.
     * <p>
     * Every registered {@link CommandHandler} will be subscribed with the
     * {@link org.axonframework.commandhandling.CommandBus} of the
     * {@link org.axonframework.configuration.ApplicationConfigurer} this module is given to.
     * <p>
     * Provides roughly two options for configuring stateless command handlers. Firstly, a command handler can
     * be registered as is, through the {@link #commandHandler(QualifiedName, CommandHandler)} method. Secondly,
     * if the command handler requires components from the {@link Configuration}, a
     * {@link ComponentBuilder builder} of the command handler can be registered through the
     * {@link #commandHandler(QualifiedName, ComponentBuilder)} method.
     */
    interface CommandHandlerPhase extends ModuleBuilder<StatelessCommandHandlingModule> {

        /**
         * Registers the given {@code commandHandler} for the given qualified {@code commandName} within this module.
         * <p>
         * Once this module is finalized, the command handler will be subscribed with the
         * {@link org.axonframework.commandhandling.CommandBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
         *
         * @param commandName    The qualified name of the command the given {@code commandHandler} can handle.
         * @param commandHandler The stateless command handler to register with this module.
         * @return The command handler phase of this builder, for a fluent API.
         */
        default CommandHandlerPhase commandHandler(@Nonnull QualifiedName commandName,
                                                   @Nonnull CommandHandler commandHandler) {
            requireNonNull(commandName, "The command name cannot be null.");
            requireNonNull(commandHandler, "The command handler cannot be null.");
            return commandHandler(commandName, c -> commandHandler);
        }

        /**
         * Registers the given {@code commandHandlerBuilder} for the given qualified {@code commandName} within this
         * module.
         * <p>
         * Once this module is finalized, the command handler from the {@code commandHandlerBuilder} will be
         * subscribed with the {@link org.axonframework.commandhandling.CommandBus} of the
         * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
         *
         * @param commandName           The qualified name of the command the {@link CommandHandler} created by
         *                              the given {@code commandHandlerBuilder}.
         * @param commandHandlerBuilder A builder of a {@link CommandHandler}. Provides the
         *                              {@link Configuration} to retrieve components from to use during construction of
         *                              the command handler.
         * @return The command handler phase of this builder, for a fluent API.
         */
        CommandHandlerPhase commandHandler(
                @Nonnull QualifiedName commandName,
                @Nonnull ComponentBuilder<CommandHandler> commandHandlerBuilder
        );

        /**
         * Registers the given {@code handlingComponentBuilder} within this module.
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
         * This will scan the given {@code handlingComponentBuilder} for methods annotated with {@link org.axonframework.commandhandling.CommandHandler}
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

        /**
         * Registers multiple command handlers using the given {@code configurationLambda} within this module.
         * <p>
         * This is a convenience method for cases where multiple command handlers need to be registered at once.
         *
         * @param configurationLambda A consumer of the command handler phase, performing command handler configuration.
         * @return The command handler phase of this builder, for a fluent API.
         */
        default CommandHandlerPhase commandHandlers(@Nonnull Consumer<CommandHandlerPhase> configurationLambda) {
            requireNonNull(configurationLambda, "The command handler configuration lambda cannot be null.")
                    .accept(this);
            return this;
        }
    }
}