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
import org.axonframework.common.Assert;
import org.axonframework.configuration.AbstractConfigurer;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.LifecycleSupportingConfiguration;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.StatefulCommandHandler;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.repository.AsyncRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of the {@link StatefulCommandHandlingModule}.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
class DefaultStatefulCommandHandlingModule
        extends AbstractConfigurer<StatefulCommandHandlingModule>
        implements StatefulCommandHandlingModule,
        StatefulCommandHandlingModule.SetupPhase,
        StatefulCommandHandlingModule.CommandHandlerPhase,
        StatefulCommandHandlingModule.EntityPhase {

    private final String moduleName;
    private final String stateManagerName;
    private final String statefulCommandHandlingComponentName;
    private final Map<String, EntityBuilder<?, ?>> entityBuilders;
    private final Map<QualifiedName, ComponentFactory<StatefulCommandHandler>> handlerFactories;
    private final List<ComponentFactory<CommandHandlingComponent>> handlingComponentFactories;

    DefaultStatefulCommandHandlingModule(@Nonnull String moduleName) {
        Assert.nonEmpty(moduleName, "The module name cannot be null");
        this.moduleName = moduleName;
        this.stateManagerName = "StateManager[" + moduleName + "]";
        this.statefulCommandHandlingComponentName = "StatefulCommandHandlingComponent[" + moduleName + "]";
        this.entityBuilders = new HashMap<>();
        this.handlerFactories = new HashMap<>();
        this.handlingComponentFactories = new ArrayList<>();
    }

    @Override
    public CommandHandlerPhase commandHandlers() {
        return this;
    }

    @Override
    public CommandHandlerPhase commandHandler(@Nonnull QualifiedName commandName,
                                              @Nonnull ComponentFactory<StatefulCommandHandler> commandHandlerBuilder) {
        handlerFactories.put(requireNonNull(commandName, "The command name cannot be null."),
                             requireNonNull(commandHandlerBuilder, "The command handler builder cannot be null."));
        return this;
    }

    @Override
    public CommandHandlerPhase commandHandlingComponent(
            @Nonnull ComponentFactory<CommandHandlingComponent> handlingComponentBuilder
    ) {
        handlingComponentFactories.add(
                requireNonNull(handlingComponentBuilder, "The command handling component builder cannot be null.")
        );
        return this;
    }

    @Override
    public EntityPhase entities() {
        return this;
    }

    @Override
    public <I, E> EntityPhase entity(@Nonnull EntityBuilder<I, E> entityBuilder) {
        requireNonNull(entityBuilder, "The entity builder cannot be null.");
        entityBuilders.put(entityBuilder.entityName(), entityBuilder);
        return this;
    }

    @Override
    public StatefulCommandHandlingModule build() {
        registerRepositories();
        registerComponent(StateManager.class, stateManagerName, this::stateManagerFactory);
        registerCommandHandlers();
        return this;
    }

    private void registerRepositories() {
        entityBuilders.forEach((name, entityBuilder) -> registerComponent(AsyncRepository.class,
                                                                          name,
                                                                          entityBuilder.repository()));
    }

    private SimpleStateManager stateManagerFactory(NewConfiguration config) {
        SimpleStateManager.Builder managerBuilder = SimpleStateManager.builder(stateManagerName);
        for (String repositoryName : entityBuilders.keySet()) {
            //noinspection unchecked
            managerBuilder.register(config.getComponent(AsyncRepository.class, repositoryName));
        }
        return managerBuilder.build();
    }

    private void registerCommandHandlers() {
        registerComponent(StatefulCommandHandlingComponent.class, statefulCommandHandlingComponentName, c -> {
            StatefulCommandHandlingComponent statefulCommandHandler = StatefulCommandHandlingComponent.create(
                    statefulCommandHandlingComponentName,
                    c.getComponent(StateManager.class, stateManagerName)
            );
            handlerFactories.forEach((key, value) -> statefulCommandHandler.subscribe(key, value.build(c)));
            handlingComponentFactories.forEach(
                    handlingComponent -> statefulCommandHandler.subscribe(handlingComponent.build(c))
            );
            return statefulCommandHandler;
        });
    }

    @Override
    public String name() {
        return this.moduleName;
    }

    @Override
    public NewConfiguration build(@Nonnull LifecycleSupportingConfiguration parent) {
        super.setParent(Objects.requireNonNull(parent, "The parent Configuration cannot be null."));
        super.enhanceInvocationAndModuleConstruction();
        LifecycleSupportingConfiguration moduleConfig = super.config();
        parent.onStart(
                Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS,
                () -> parent.getComponent(CommandBus.class)
                            .subscribe(moduleConfig.getComponent(
                                    StatefulCommandHandlingComponent.class,
                                    statefulCommandHandlingComponentName
                            ))
        );
        return moduleConfig;
    }
}
