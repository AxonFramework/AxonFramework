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
import org.axonframework.modelling.SimpleRepository;
import org.axonframework.modelling.SimpleRepositoryEntityLoader;
import org.axonframework.modelling.SimpleRepositoryEntityPersister;
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
 * The single implementation of the {@link StatefulCommandHandlingModule} and it's {@link SetupPhase builder} flow.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
class StatefulCommandHandlingModuleImpl
        extends AbstractConfigurer<StatefulCommandHandlingModule>
        implements StatefulCommandHandlingModule,
        StatefulCommandHandlingModule.SetupPhase,
        StatefulCommandHandlingModule.CommandHandlerPhase,
        StatefulCommandHandlingModule.EntityPhase,
        StatefulCommandHandlingModule.BuildPhase {

    private final String moduleName;
    private final String stateManagerName;
    private final String statefulCommandHandlingComponentName;

    private final Map<String, EntityConfigurer<?, ?>> entities;
    private final Map<QualifiedName, ComponentFactory<StatefulCommandHandler>> handlerFactories;
    private final AtomicReference<StatefulCommandHandlingComponent> handlingComponentReference;

    StatefulCommandHandlingModuleImpl(@Nonnull String moduleName) {
        Assert.nonEmpty(moduleName, "The module name cannot be null");
        this.moduleName = moduleName;
        this.stateManagerName = "StateManager[" + moduleName + "]";
        this.statefulCommandHandlingComponentName = "StatefulCommandHandlingComponent[" + moduleName + "]";

        this.entities = new HashMap<>();
        this.handlerFactories = new HashMap<>();
        this.handlingComponentReference = new AtomicReference<>();
    }

    @Override
    public CommandHandlerPhase commandHandlers() {
        return this;
    }

    @Override
    public CommandHandlerPhase handler(@Nonnull QualifiedName commandName,
                                       @Nonnull ComponentFactory<StatefulCommandHandler> commandHandlerBuilder) {
        this.handlerFactories.put(commandName, commandHandlerBuilder);
        return this;
    }

    @Override
    public EntityPhase entities() {
        return this;
    }

    @Override
    public <I, E> RepositoryPhase<I, E> entity(@Nonnull Class<I> idType,
                                               @Nonnull Class<E> entityType) {
        EntityConfigurer<I, E> entityConfigurer = new EntityConfigurer<>(this, idType, entityType);
        entities.put(entityConfigurer.entityName(), entityConfigurer);
        return entityConfigurer;
    }

    @Override
    public StatefulCommandHandlingModule build() {
        registerRepositories();
        registerComponent(StateManager.class, stateManagerName, this::stateManagerFactory);
        registerCommandHandlers();
        return this;
    }

    private void registerRepositories() {
        // TODO DISCUSS - We lose the generics now when retrieving the AsyncRepository. Sad yes/no?
        entities.forEach((name, entityBuilder) -> registerComponent(AsyncRepository.class,
                                                                    name,
                                                                    entityBuilder.repository()));
    }

    private SimpleStateManager stateManagerFactory(NewConfiguration config) {
        SimpleStateManager.Builder managerBuilder = SimpleStateManager.builder(stateManagerName);
        for (String repositoryName : entities.keySet()) {
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
        // TODO DISCUSS - do we want to subscribe separate command handlers?
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

    private static class EntityConfigurer<I, E> implements
            EntityPhase,
            RepositoryPhase<I, E>,
            PersisterPhase<I, E> {

        // Parent State Configurer for circling back.
        private final StatefulCommandHandlingModuleImpl parent;
        // Entity type information
        private final Class<I> idType;
        private final Class<E> entityType;
        // Repository information
        private ComponentFactory<SimpleRepositoryEntityLoader<I, E>> loaderFactory;
        private ComponentFactory<SimpleRepositoryEntityPersister<I, E>> persisterFactory;
        private ComponentFactory<AsyncRepository<I, E>> repositoryFactory;

        private EntityConfigurer(StatefulCommandHandlingModuleImpl parent,
                                 Class<I> idType,
                                 Class<E> entityType) {
            this.parent = parent;
            this.idType = requireNonNull(idType, "The identifier type cannot be null.");
            this.entityType = requireNonNull(entityType, "The entity type cannot be null.");
        }

        @Override
        public <ID, T> RepositoryPhase<ID, T> entity(@Nonnull Class<ID> idType, @Nonnull Class<T> entityType) {
            return parent.entity(idType, entityType);
        }

        @Override
        public PersisterPhase<I, E> loader(@Nonnull ComponentFactory<SimpleRepositoryEntityLoader<I, E>> loader) {
            this.loaderFactory = requireNonNull(loader, "The repository loader factory cannot be null.");
            return this;
        }

        @Override
        public EntityPhase persister(@Nonnull ComponentFactory<SimpleRepositoryEntityPersister<I, E>> persister) {
            this.persisterFactory = requireNonNull(persister, "The repository persister factory cannot be null.");
            return this;
        }

        @Override
        public EntityPhase repository(@Nonnull ComponentFactory<AsyncRepository<I, E>> repository) {
            this.repositoryFactory = requireNonNull(repository, "The repository factory cannot be null.");
            return this;
        }

        @Override
        public CommandHandlerPhase commandHandlers() {
            return parent.commandHandlers();
        }

        @Override
        public EntityPhase entities() {
            return this;
        }

        @Override
        public StatefulCommandHandlingModule build() {
            return parent.build();
        }

        private String entityName() {
            return entityType.getSimpleName() + "#" + idType.getSimpleName();
        }

        private ComponentFactory<AsyncRepository<I, E>> repository() {
            return repositoryFactory != null
                    ? repositoryFactory
                    : c -> new SimpleRepository<>(idType,
                                                  entityType,
                                                  loaderFactory.build(c),
                                                  persisterFactory.build(c));
        }
    }
}
