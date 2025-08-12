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
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.common.FutureUtils;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.HierarchicalStateManagerConfigurationEnhancer;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;
import org.axonframework.modelling.command.StatefulCommandHandler;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.axonframework.configuration.ComponentDefinition.ofTypeAndName;

/**
 * Simple implementation of the {@link CommandHandlingModule}. Registers the
 * {@link HierarchicalStateManagerConfigurationEnhancer} enhancer to the module so that message handlers get access to
 * entities via defining parameters, such as entitiy classes with {@link InjectEntity} or the {@link StateManager}
 * itself.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
class StatefulSimpleCommandHandlingModule
        extends BaseModule<StatefulSimpleCommandHandlingModule>
        implements CommandHandlingModule,
        CommandHandlingModule.SetupPhase,
        CommandHandlingModule.CommandHandlerPhase
{

    private final String statefulCommandHandlingComponentName;
    private final Map<QualifiedName, ComponentBuilder<StatefulCommandHandler>> handlerBuilders;
    private final List<ComponentBuilder<CommandHandlingComponent>> handlingComponentBuilders;

    StatefulSimpleCommandHandlingModule(@Nonnull String moduleName) {
        super(requireNonNull(moduleName, "The module name cannot be null."));
        this.statefulCommandHandlingComponentName = "StatefulCommandHandlingComponent[" + moduleName + "]";
        this.handlerBuilders = new HashMap<>();
        this.handlingComponentBuilders = new ArrayList<>();
    }

    @Override
    public CommandHandlerPhase commandHandlers() {
        return this;
    }

    @Override
    public CommandHandlerPhase commandHandler(@Nonnull QualifiedName commandName,
                                              @Nonnull ComponentBuilder<StatefulCommandHandler> commandHandlerBuilder) {
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
    public CommandHandlingModule build() {
        registerStatefulCommandHandlingComponent();
        return this;
    }

    private void registerStatefulCommandHandlingComponent() {
        componentRegistry(cr -> cr.registerComponent(getStatefulCommandHandlingComponentComponentDefinition()));
    }

    private ComponentDefinition<StatefulCommandHandlingComponent> getStatefulCommandHandlingComponentComponentDefinition() {
        return ofTypeAndName(StatefulCommandHandlingComponent.class, statefulCommandHandlingComponentName)
                .withBuilder(c -> {
                    StatefulCommandHandlingComponent statefulCommandHandler = StatefulCommandHandlingComponent.create(
                            statefulCommandHandlingComponentName,
                            c.getComponent(StateManager.class)
                    );
                    handlingComponentBuilders.forEach(handlingComponent -> statefulCommandHandler.subscribe(
                            handlingComponent.build(c)));
                    handlerBuilders.forEach((key, value) -> statefulCommandHandler.subscribe(key, value.build(c)));
                    return statefulCommandHandler;
                })
                .onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, (configuration, component) -> {
                    configuration.getComponent(CommandBus.class)
                                 .subscribe(configuration.getComponent(StatefulCommandHandlingComponent.class,
                                                                       statefulCommandHandlingComponentName));
                    return FutureUtils.emptyCompletedFuture();
                });
    }
}
