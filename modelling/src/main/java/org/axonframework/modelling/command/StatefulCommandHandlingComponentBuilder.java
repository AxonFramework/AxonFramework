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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.configuration.StatefulComponentBuilder;

import static java.util.Objects.requireNonNull;

public interface StatefulCommandHandlingComponentBuilder extends StatefulComponentBuilder<StatefulCommandHandlingComponent> {

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
    default StatefulCommandHandlingComponentBuilder commandHandler(@Nonnull QualifiedName commandName,
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
    default StatefulCommandHandlingComponentBuilder commandHandler(@Nonnull QualifiedName commandName,
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
    StatefulCommandHandlingComponentBuilder commandHandler(
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
    StatefulCommandHandlingComponentBuilder commandHandlingComponent(
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
    default StatefulCommandHandlingComponentBuilder annotatedCommandHandlingComponent(
            @Nonnull ComponentBuilder<Object> handlingComponentBuilder
    ) {
        requireNonNull(handlingComponentBuilder, "The handling component builder cannot be null.");
        return commandHandlingComponent(c -> new AnnotatedCommandHandlingComponent<>(
                handlingComponentBuilder.build(c),
                c.getComponent(ParameterResolverFactory.class)
        ));
    }

    ComponentDefinition<StatefulCommandHandlingComponent> toComponentDefinition();

}
