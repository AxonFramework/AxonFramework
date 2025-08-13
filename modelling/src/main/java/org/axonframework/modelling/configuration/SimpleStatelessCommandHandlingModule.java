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
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.common.FutureUtils;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.QualifiedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.axonframework.configuration.ComponentDefinition.ofTypeAndName;

/**
 * Simple implementation of the {@link StatelessCommandHandlingModule}. This implementation creates a single
 * {@link CommandHandlingComponent} that combines all registered command handlers and subscribes it to the
 * {@link CommandBus}.
 * <p>
 * Unlike the {@link SimpleStatefulCommandHandlingModule}, this implementation does not register any state management
 * components and is designed for command handlers that don't require entity state.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SimpleStatelessCommandHandlingModule
        extends BaseModule<SimpleStatelessCommandHandlingModule>
        implements StatelessCommandHandlingModule,
        StatelessCommandHandlingModule.CommandHandlerPhase {

    private final String moduleName;
    private final String commandHandlingComponentName;
    private final Map<QualifiedName, ComponentBuilder<CommandHandler>> handlerBuilders;
    private final List<ComponentBuilder<CommandHandlingComponent>> handlingComponentBuilders;

    SimpleStatelessCommandHandlingModule(@Nonnull String moduleName) {
        super(moduleName);
        this.moduleName = requireNonNull(moduleName, "The module name cannot be null.");
        this.commandHandlingComponentName = "StatelessCommandHandlingComponent[" + moduleName + "]";
        this.handlerBuilders = new HashMap<>();
        this.handlingComponentBuilders = new ArrayList<>();
    }

    @Override
    public CommandHandlerPhase commandHandler(@Nonnull QualifiedName commandName,
                                              @Nonnull ComponentBuilder<CommandHandler> commandHandlerBuilder) {
        handlerBuilders.put(requireNonNull(commandName, "The command name cannot be null."),
                            requireNonNull(commandHandlerBuilder, "The command handler builder cannot be null."));
        return this;
    }

    @Override
    public CommandHandlerPhase commandHandlingComponent(
            @Nonnull ComponentBuilder<CommandHandlingComponent> handlingComponentBuilder
    ) {
        handlingComponentBuilders.add(
                requireNonNull(handlingComponentBuilder, "The command handling component builder cannot be null.")
        );
        return this;
    }

    @Override
    public StatelessCommandHandlingModule build() {
        registerStatelessCommandHandlingComponent();
        return this;
    }

    private void registerStatelessCommandHandlingComponent() {
        componentRegistry(cr -> cr.registerComponent(getStatelessCommandHandlingComponentComponentDefinition()));
    }

    private ComponentDefinition<CommandHandlingComponent> getStatelessCommandHandlingComponentComponentDefinition() {
        return ofTypeAndName(CommandHandlingComponent.class, commandHandlingComponentName)
                .withBuilder(c -> {
                    SimpleCommandHandlingComponent commandHandlingComponent = SimpleCommandHandlingComponent.create(
                            commandHandlingComponentName
                    );
                    
                    // Subscribe all command handling components
                    handlingComponentBuilders.forEach(handlingComponent -> commandHandlingComponent.subscribe(
                            handlingComponent.build(c)));
                    
                    // Subscribe all individual command handlers
                    handlerBuilders.forEach((commandName, handlerBuilder) -> commandHandlingComponent.subscribe(
                            commandName, handlerBuilder.build(c)));
                    
                    return commandHandlingComponent;
                })
                .onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, (configuration, component) -> {
                    configuration.getComponent(CommandBus.class)
                                 .subscribe(configuration.getComponent(CommandHandlingComponent.class,
                                                                       commandHandlingComponentName));
                    return FutureUtils.emptyCompletedFuture();
                });
    }
}