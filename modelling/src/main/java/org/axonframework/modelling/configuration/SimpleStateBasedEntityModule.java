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
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandlingComponent;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.TypeReference;
import org.axonframework.common.configuration.BaseModule;
import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.axonframework.common.lifecycle.Phase;
import org.axonframework.modelling.EntityIdResolver;
import org.axonframework.modelling.repository.SimpleRepository;
import org.axonframework.modelling.repository.SimpleRepositoryEntityLoader;
import org.axonframework.modelling.repository.SimpleRepositoryEntityPersister;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.modelling.repository.Repository;

import static java.util.Objects.requireNonNull;

/**
 * Basis implementation of the {@link StateBasedEntityModule}.
 *
 * @param <ID> The type of identifier used to identify the state-based entity that's being built.
 * @param <E>  The type of the state-based entity being built.
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SimpleStateBasedEntityModule<ID, E>
        extends BaseModule<SimpleStateBasedEntityModule<ID, E>> implements
        StateBasedEntityModule<ID, E>,
        StateBasedEntityModule.RepositoryPhase<ID, E>,
        StateBasedEntityModule.PersisterPhase<ID, E>,
        StateBasedEntityModule.MessagingMetamodelPhase<ID, E>,
        StateBasedEntityModule.EntityIdResolverPhase<ID, E> {

    private final Class<ID> idType;
    private final Class<E> entityType;
    private ComponentBuilder<SimpleRepositoryEntityLoader<ID, E>> loader;
    private ComponentBuilder<SimpleRepositoryEntityPersister<ID, E>> persister;
    private ComponentBuilder<Repository<ID, E>> repository;
    private ComponentBuilder<EntityMetamodel<E>> entityModel;
    private ComponentBuilder<EntityIdResolver<ID>> entityIdResolver;

    SimpleStateBasedEntityModule(@Nonnull Class<ID> idType,
                                 @Nonnull Class<E> entityType) {
        super("SimpleStateBasedEntityModule<%s, %s>".formatted(idType.getName(), entityType.getName()));
        this.idType = requireNonNull(idType, "The identifier type cannot be null.");
        this.entityType = requireNonNull(entityType, "The entity type cannot be null.");
    }

    @Override
    public MessagingMetamodelPhase<ID, E> persister(
            @Nonnull ComponentBuilder<SimpleRepositoryEntityPersister<ID, E>> persister
    ) {
        this.persister = requireNonNull(persister, "The repository persister builder cannot be null.");
        return this;
    }

    @Override
    public PersisterPhase<ID, E> loader(@Nonnull ComponentBuilder<SimpleRepositoryEntityLoader<ID, E>> loader) {
        this.loader = requireNonNull(loader, "The repository loader builder cannot be null.");
        return this;
    }

    @Override
    public MessagingMetamodelPhase<ID, E> repository(
            @Nonnull ComponentBuilder<Repository<ID, E>> repository
    ) {
        this.repository = requireNonNull(repository, "The repository builder cannot be null.");
        return this;
    }


    @Override
    public EntityIdResolverPhase<ID, E> messagingModel(
            @Nonnull EntityMetamodelConfigurationBuilder<E> metamodelFactory) {
        requireNonNull(metamodelFactory, "The metamodelFactory cannot be null.");
        this.entityModel = c -> metamodelFactory.build(c, EntityMetamodel.forEntityType(entityType));
        return this;
    }

    @Override
    public StateBasedEntityModule<ID, E> entityIdResolver(
            @Nonnull ComponentBuilder<EntityIdResolver<ID>> entityIdResolver) {
        this.entityIdResolver = requireNonNull(entityIdResolver, "The EntityIdResolver builder cannot be null.");
        return this;
    }

    @Override
    public String entityName() {
        return entityType.getSimpleName() + "#" + idType.getSimpleName();
    }

    @Override
    public Class<ID> idType() {
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
        if (entityModel != null) {
            requireNonNull(entityIdResolver,
                           "The entity ID resolver builder must be provided if an EntityModel is given.");
        }
    }

    private void registerRepository() {
        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofTypeAndName(new TypeReference<Repository<ID, E>>() {
                                       }, entityName()
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
                        .ofTypeAndName(new TypeReference<EntityMetamodel<E>>() {
                                       }, entityName()
                        )
                        .withBuilder(entityModel)));

        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofTypeAndName(new TypeReference<EntityIdResolver<ID>>() {
                                       }, entityName()
                        )
                        .withBuilder(entityIdResolver)));

        //noinspection unchecked
        componentRegistry(cr -> cr.registerComponent(
                ComponentDefinition
                        .ofTypeAndName(CommandHandlingComponent.class, entityName())
                        .withBuilder(c -> new EntityCommandHandlingComponent<ID, E>(
                                c.getComponent(Repository.class, entityName()),
                                c.getComponent(EntityMetamodel.class, entityName()),
                                c.getComponent(EntityIdResolver.class, entityName())))
                        .onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS, (config, component) -> {
                            config.getComponent(CommandBus.class).subscribe(component);
                            return FutureUtils.emptyCompletedFuture();
                        })
        ));
    }

    @Override
    public StateBasedEntityModule<ID, E> build() {
        return this;
    }
}
