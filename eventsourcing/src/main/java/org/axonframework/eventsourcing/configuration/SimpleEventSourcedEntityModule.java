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

package org.axonframework.eventsourcing.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.TypeReference;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.lifecycle.Phase;
import org.axonframework.modelling.EntityIdResolver;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.configuration.EntityMetamodelConfigurationBuilder;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.modelling.repository.Repository;

import static java.util.Objects.requireNonNull;

/**
 * Simple implementation of the {@link EventSourcedEntityModule}.
 *
 * @param <ID> The type of identifier used to identify the event-sourced entity that's being built.
 * @param <E>  The type of the event-sourced entity being built.
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SimpleEventSourcedEntityModule<ID, E> extends BaseModule<SimpleEventSourcedEntityModule<ID, E>>
        implements
        EventSourcedEntityModule<ID, E>,
        EventSourcedEntityModule.MessagingModelPhase<ID, E>,
        EventSourcedEntityModule.EntityFactoryPhase<ID, E>,
        EventSourcedEntityModule.CriteriaResolverPhase<ID, E>,
        EventSourcedEntityModule.EntityIdResolverPhase<ID, E> {

    private final Class<ID> idType;
    private final Class<E> entityType;

    private ComponentBuilder<EventSourcedEntityFactory<ID, E>> entityFactory;
    private ComponentBuilder<CriteriaResolver<ID>> criteriaResolver;
    private ComponentBuilder<EntityMetamodel<E>> entityModel;
    private ComponentBuilder<EntityIdResolver<ID>> entityIdResolver;

    SimpleEventSourcedEntityModule(@Nonnull Class<ID> idType,
                                   @Nonnull Class<E> entityType) {
        super("SimpleEventSourcedEntityModule<%s, %s>".formatted(idType.getName(), entityType.getName()));
        this.idType = requireNonNull(idType, "The identifier type cannot be null.");
        this.entityType = requireNonNull(entityType, "The entity type cannot be null.");
    }

    @Override
    public EntityFactoryPhase<ID, E> messagingModel(
            @Nonnull EntityMetamodelConfigurationBuilder<E> metamodelFactory) {
        requireNonNull(metamodelFactory, "The metamodelFactory cannot be null.");
        this.entityModel = c -> metamodelFactory.build(c, EntityMetamodel.forEntityType(entityType));
        return this;
    }

    @Override
    public CriteriaResolverPhase<ID, E> entityFactory(
            @Nonnull ComponentBuilder<EventSourcedEntityFactory<ID, E>> entityFactory
    ) {
        this.entityFactory = requireNonNull(entityFactory, "The entity factory cannot be null.");
        return this;
    }

    @Override
    public EntityIdResolverPhase<ID, E> criteriaResolver(
            @Nonnull ComponentBuilder<CriteriaResolver<ID>> criteriaResolver
    ) {
        this.criteriaResolver = requireNonNull(criteriaResolver, "The criteria resolver cannot be null.");
        return this;
    }

    @Override
    public EventSourcedEntityModule<ID, E> entityIdResolver(
            @Nonnull ComponentBuilder<EntityIdResolver<ID>> entityIdResolver) {
        this.entityIdResolver = requireNonNull(entityIdResolver, "The entity ID resolver cannot be null.");
        return this;
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
        registerComponents();
        return super.build(parent, lifecycleRegistry);
    }

    private void validate() {
        requireNonNull(entityFactory, "The EntityFactory must be provided to module [%s].".formatted(name()));
        requireNonNull(criteriaResolver, "The CriteriaResolver must be provided to module [%s].".formatted(name()));
        requireNonNull(entityModel, "The EntityModel must be provided to module [%s].".formatted(name()));
    }

    private void registerComponents() {
        componentRegistry(cr -> {
            cr.registerComponent(entityFactory());
            cr.registerComponent(criteriaResolver());
            cr.registerComponent(entityModel());
            cr.registerComponent(repository());

            if (entityIdResolver != null) {
                cr.registerComponent(idResolver());
                cr.registerComponent(commandHandlingComponent());
            }
        });
    }

    private ComponentDefinition<EntityMetamodel<E>> entityModel() {
        TypeReference<EntityMetamodel<E>> type = new TypeReference<>() {
        };
        return ComponentDefinition.ofTypeAndName(type, entityName())
                                  .withBuilder(entityModel);
    }

    private ComponentDefinition<EntityIdResolver<ID>> idResolver() {
        TypeReference<EntityIdResolver<ID>> type = new TypeReference<>() {
        };
        return ComponentDefinition.ofTypeAndName(type, entityName())
                                  .withBuilder(entityIdResolver);
    }

    private ComponentDefinition<Repository<ID, E>> repository() {
        TypeReference<Repository<ID, E>> type = new TypeReference<>() {
        };
        return ComponentDefinition
                .ofTypeAndName(type, entityName())
                .withBuilder(config -> {
                    //noinspection unchecked
                    return new EventSourcingRepository<ID, E>(
                            idType,
                            entityType,
                            config.getComponent(EventStore.class),
                            config.getComponent(EventSourcedEntityFactory.class, entityName()),
                            config.getComponent(CriteriaResolver.class, entityName()),
                            config.getComponent(EntityMetamodel.class, entityName())
                    );
                })
                .onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS,
                         (config, component) -> {
                             config.getComponent(StateManager.class).register(component);
                             return FutureUtils.emptyCompletedFuture();
                         }
                );
    }

    private ComponentDefinition<CommandHandlingComponent> commandHandlingComponent() {
        //noinspection unchecked
        return ComponentDefinition
                .ofTypeAndName(CommandHandlingComponent.class, entityName())
                .withBuilder(c -> new EntityCommandHandlingComponent<ID, E>(
                        c.getComponent(Repository.class, entityName()),
                        c.getComponent(EntityMetamodel.class, entityName()),
                        c.getComponent(EntityIdResolver.class, entityName())
                ))
                .onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS,
                         (config, component) -> {
                             config.getComponent(CommandBus.class).subscribe(component);
                             return FutureUtils.emptyCompletedFuture();
                         }
                );
    }

    private ComponentDefinition<EventSourcedEntityFactory<ID, E>> entityFactory() {
        TypeReference<EventSourcedEntityFactory<ID, E>> type = new TypeReference<>() {
        };

        return ComponentDefinition
                .ofTypeAndName(type, entityName())
                .withBuilder(entityFactory);
    }

    private ComponentDefinition<CriteriaResolver<ID>> criteriaResolver() {
        TypeReference<CriteriaResolver<ID>> type = new TypeReference<>() {
        };
        return ComponentDefinition
                .ofTypeAndName(type, entityName())
                .withBuilder(criteriaResolver);
    }

    @Override
    public EventSourcedEntityModule<ID, E> build() {
        return this;
    }
}
