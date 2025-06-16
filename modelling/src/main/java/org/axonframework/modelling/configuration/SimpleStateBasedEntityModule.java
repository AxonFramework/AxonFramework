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
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.lifecycle.Phase;
import org.axonframework.modelling.SimpleRepository;
import org.axonframework.modelling.SimpleRepositoryEntityLoader;
import org.axonframework.modelling.SimpleRepositoryEntityPersister;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.repository.Repository;

import static java.util.Objects.requireNonNull;

/**
 * Basis implementation of the {@link StateBasedEntityModule}.
 *
 * @param <I> The type of identifier used to identify the state-based entity that's being built.
 * @param <E> The type of the state-based entity being built.
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SimpleStateBasedEntityModule<I, E>
        extends BaseModule<SimpleStateBasedEntityModule<I, E>> implements
        StateBasedEntityModule<I, E>,
        StateBasedEntityModule.RepositoryPhase<I, E>,
        StateBasedEntityModule.PersisterPhase<I, E>,
        StateBasedEntityModule.EntityModelPhase<I, E>,
        StateBasedEntityModule.EntityIdResolverPhase<I, E> {

    private final Class<I> idType;
    private final Class<E> entityType;
    private ComponentBuilder<SimpleRepositoryEntityLoader<I, E>> loader;
    private ComponentBuilder<SimpleRepositoryEntityPersister<I, E>> persister;
    private ComponentBuilder<Repository<I, E>> repository;
    private ComponentBuilder<EntityModel<E>> entityModel;
    private ComponentBuilder<EntityIdResolver<I>> entityIdResolver;

    SimpleStateBasedEntityModule(@Nonnull Class<I> idType,
                                 @Nonnull Class<E> entityType) {
        super("DefaultStateBasedEntityModule<%s, %s>".formatted(idType.getSimpleName(), entityType.getSimpleName()));
        this.idType = requireNonNull(idType, "The identifier type cannot be null.");
        this.entityType = requireNonNull(entityType, "The entity type cannot be null.");
    }

    @Override
    public EntityModelPhase<I, E> persister(
            @Nonnull ComponentBuilder<SimpleRepositoryEntityPersister<I, E>> persister
    ) {
        this.persister = requireNonNull(persister, "The repository persister builder cannot be null.");
        return this;
    }

    @Override
    public PersisterPhase<I, E> loader(@Nonnull ComponentBuilder<SimpleRepositoryEntityLoader<I, E>> loader) {
        this.loader = requireNonNull(loader, "The repository loader builder cannot be null.");
        return this;
    }

    @Override
    public EntityModelPhase<I, E> repository(
            @Nonnull ComponentBuilder<Repository<I, E>> repository
    ) {
        this.repository = requireNonNull(repository, "The repository builder cannot be null.");
        return this;
    }

    @Override
    public String entityName() {
        return entityType.getSimpleName() + "#" + idType.getSimpleName();
    }

    @Override
    public Class<I> idType() {
        return idType;
    }

    @Override
    public Class<E> entityType() {
        return entityType;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        validate();
        registerRepository();
        registerCommandHandlingComponentIfModelIsPresent();
        return super.build(parent, lifecycleRegistry);
    }

    private void validate() {
        if (repository == null) {
            requireNonNull(loader, "The repository loader builder must be provided if no repository is given.");
            requireNonNull(persister, "The repository persister builder must be provided if no repository is given.");
        }
        if(entityModel != null) {
            requireNonNull(entityIdResolver, "The entity ID resolver builder must be provided if an EntityModel is given.");
        }
    }

    private void registerRepository() {
        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofTypeAndName(
                                (ComponentDefinition.TypeReference<Repository<I, E>>) () -> Repository.class,
                                entityName()
                        )
                        .withBuilder(c -> {
                            if (repository != null) {
                                return repository.build(c);
                            }
                            return new SimpleRepository<>(
                                    idType,
                                    entityType,
                                    loader.build(c),
                                    persister.build(c)
                            );
                        })
                        .onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, (config, component) -> {
                            config.getComponent(StateManager.class).register(component);
                            return FutureUtils.emptyCompletedFuture();
                        })
        ));
    }

    private void registerCommandHandlingComponentIfModelIsPresent() {
        if (entityModel == null) {
            return;
        }
        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofTypeAndName(
                                (ComponentDefinition.TypeReference<EntityModel<E>>) () -> EntityModel.class,
                                entityName()
                        )
                        .withBuilder(entityModel)));

        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofTypeAndName(
                                (ComponentDefinition.TypeReference<EntityIdResolver<I>>) () -> EntityIdResolver.class,
                                entityName()
                        )
                        .withBuilder(entityIdResolver)));

        //noinspection unchecked
        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofTypeAndName(CommandHandlingComponent.class, entityName())
                        .withBuilder(c -> new EntityCommandHandlingComponent<I, E>(
                                c.getComponent(Repository.class, entityName()),
                                c.getComponent(EntityModel.class, entityName()),
                                c.getComponent(EntityIdResolver.class, entityName())))
                        .onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, (config, component) -> {
                            config.getComponent(CommandBus.class).subscribe(component);
                            return FutureUtils.emptyCompletedFuture();
                        })
        ));
    }

    @Override
    public EntityIdResolverPhase<I, E> modeled(@Nonnull ComponentBuilder<EntityModel<E>> entityFactory) {
        this.entityModel = requireNonNull(entityFactory, "The EntityModel builder cannot be null.");
        return this;
    }

    @Override
    public StateBasedEntityModule<I, E> withoutModel() {
        return this;
    }

    @Override
    public StateBasedEntityModule<I, E> entityIdResolver(
            @Nonnull ComponentBuilder<EntityIdResolver<I>> entityIdResolver) {
        this.entityIdResolver = requireNonNull(entityIdResolver, "The EntityIdResolver builder cannot be null.");
        return this;
    }
}
