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
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Base interface for command handler registration phases that provides common command handler registration
 * functionality. This interface contains all the shared methods for registering {@link CommandHandler CommandHandlers}
 * and {@link CommandHandlingComponent CommandHandlingComponents} that are used by both stateful and stateless
 * command handling modules.
 * <p>
 * Every registered command handler will be subscribed with the {@link org.axonframework.commandhandling.CommandBus}
 * of the {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
 *
 * @param <T> The type of the command handler phase, allowing for fluent API method chaining.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface BaseCommandHandlerPhase<T extends BaseCommandHandlerPhase<T>> {

    /**
     * Registers the given {@code commandHandlerBuilder} for the given qualified {@code commandName} within this
     * module.
     * <p>
     * Once this module is finalized, the command handler from the {@code commandHandlerBuilder} will be subscribed
     * with the {@link org.axonframework.commandhandling.CommandBus} of the
     * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
     *
     * @param commandName           The qualified name of the command the {@link CommandHandler} created by the given
     *                              {@code commandHandlerBuilder}.
     * @param commandHandlerBuilder A builder of a {@link CommandHandler}. Provides the {@link Configuration} to
     *                              retrieve components from to use during construction of the command handler.
     * @return This command handler phase, for a fluent API.
     */
    T commandHandler(@Nonnull QualifiedName commandName,
                     @Nonnull ComponentBuilder<CommandHandler> commandHandlerBuilder);

    /**
     * Registers the given {@code commandHandler} for the given qualified {@code commandName} within this module.
     * <p>
     * Once this module is finalized, the command handler will be subscribed with the
     * {@link org.axonframework.commandhandling.CommandBus} of the
     * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
     *
     * @param commandName    The qualified name of the command the given {@code commandHandler} can handle.
     * @param commandHandler The command handler to register with this module.
     * @return This command handler phase, for a fluent API.
     */
    default T commandHandler(@Nonnull QualifiedName commandName, @Nonnull CommandHandler commandHandler) {
        requireNonNull(commandName, "The command name cannot be null.");
        requireNonNull(commandHandler, "The command handler cannot be null.");
        return commandHandler(commandName, c -> commandHandler);
    }

    /**
     * Registers the given {@code handlingComponentBuilder} within this module.
     * <p>
     * Once this module is finalized, the resulting {@link CommandHandlingComponent} from the
     * {@code handlingComponentBuilder} will be subscribed with the
     * {@link org.axonframework.commandhandling.CommandBus} of the
     * {@link org.axonframework.configuration.ApplicationConfigurer} the module is registered on.
     *
     * @param handlingComponentBuilder A builder of a {@link CommandHandlingComponent}. Provides the
     *                                 {@link Configuration} to retrieve components from to use during construction of
     *                                 the command handling component.
     * @return This command handler phase, for a fluent API.
     */
    T commandHandlingComponent(@Nonnull ComponentBuilder<CommandHandlingComponent> handlingComponentBuilder);

    /**
     * Registers the given {@code handlingComponentBuilder} as an {@link AnnotatedCommandHandlingComponent} within
     * this module.
     * <p>
     * This will scan the given {@code handlingComponentBuilder} for methods annotated with
     * {@link org.axonframework.commandhandling.CommandHandler} and register them as command handlers for the
     * {@link org.axonframework.commandhandling.CommandBus} of the
     * {@link org.axonframework.configuration.ApplicationConfigurer}.
     *
     * @param handlingComponentBuilder A builder of a {@link CommandHandlingComponent}. Provides the
     *                                 {@link Configuration} to retrieve components from to use during construction of
     *                                 the command handling component.
     * @return This command handler phase, for a fluent API.
     */
    default T annotatedCommandHandlingComponent(@Nonnull ComponentBuilder<Object> handlingComponentBuilder) {
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
     * @return This command handler phase, for a fluent API.
     */
    default T commandHandlers(@Nonnull Consumer<T> configurationLambda) {
        requireNonNull(configurationLambda, "The command handler configuration lambda cannot be null.")
                .accept((T) this);
        return (T) this;
    }
}