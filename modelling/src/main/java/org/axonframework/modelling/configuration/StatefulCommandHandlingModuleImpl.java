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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Basis implementation of the {@link StatefulCommandHandlingModule}.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO I really, really don't like "Impl." Similarly, I do not like "Default" or "Simple." Suggestions?
class StatefulCommandHandlingModuleImpl
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
    private final AtomicReference<StatefulCommandHandlingComponent> handlingComponentReference;

    StatefulCommandHandlingModuleImpl(@Nonnull String moduleName) {
        Assert.nonEmpty(moduleName, "The module name cannot be null");
        this.moduleName = moduleName;
        this.stateManagerName = "StateManager[" + moduleName + "]";
        this.statefulCommandHandlingComponentName = "StatefulCommandHandlingComponent[" + moduleName + "]";
        this.entityBuilders = new HashMap<>();
        this.handlerFactories = new HashMap<>();
        this.handlingComponentReference = new AtomicReference<>();
    }

    @Override
    public CommandHandlerPhase commandHandlers() {
        return this;
    }

    @Override
    public CommandHandlerPhase commandHandler(@Nonnull QualifiedName commandName,
                                              @Nonnull ComponentFactory<StatefulCommandHandler> commandHandlerBuilder) {
        this.handlerFactories.put(commandName, commandHandlerBuilder);
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
            StatefulCommandHandlingComponent handlingComponent = StatefulCommandHandlingComponent.create(
                    statefulCommandHandlingComponentName,
                    c.getComponent(StateManager.class, stateManagerName)
            );
            // TODO DISCUSS - do we want separate command handler registrations?
            // Not for now - add issue for the future
            handlerFactories.forEach((key, value) -> handlingComponent.subscribe(key, value.build(c)));
            handlingComponentReference.set(handlingComponent);
            return handlingComponent;
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
