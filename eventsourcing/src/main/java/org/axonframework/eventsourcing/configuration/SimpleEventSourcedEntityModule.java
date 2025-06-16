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
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.FutureUtils;
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
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.repository.Repository;

import static java.util.Objects.requireNonNull;

/**
 * Basis implementation of the {@link EventSourcedEntityModule}.
 *
 * @param <I> The type of identifier used to identify the event-sourced entity that's being built.
 * @param <E> The type of the event-sourced entity being built.
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SimpleEventSourcedEntityModule<I, E>
        extends BaseModule<SimpleEventSourcedEntityModule<I, E>>
        implements
        EventSourcedEntityModule<I, E>,
        EventSourcedEntityModule.EntityModelPhase<I, E>,
        EventSourcedEntityModule.EntityFactoryPhase<I, E>,
        EventSourcedEntityModule.CriteriaResolverPhase<I, E>,
        EventSourcedEntityModule.EntityIdResolverPhase<I, E> {

    private final Class<I> idType;
    private final Class<E> entityType;
    private ComponentBuilder<EventSourcedEntityFactory<I, E>> entityFactory;
    private ComponentBuilder<CriteriaResolver<I>> criteriaResolver;
    private ComponentBuilder<EntityModel<E>> entityModel;
    private ComponentBuilder<EntityIdResolver<I>> entityIdResolver;

    SimpleEventSourcedEntityModule(@Nonnull Class<I> idType,
                                   @Nonnull Class<E> entityType) {
        super("SimpleEventSourcedEntityModule<%s, %s>".formatted(idType.getSimpleName(), entityType.getSimpleName()));
        this.idType = requireNonNull(idType, "The identifier type cannot be null.");
        this.entityType = requireNonNull(entityType, "The entity type cannot be null.");
    }


    @Override
    public CriteriaResolverPhase<I, E> entityFactory(
            @Nonnull ComponentBuilder<EventSourcedEntityFactory<I, E>> entityFactory
    ) {
        this.entityFactory = requireNonNull(entityFactory, "The entity factory cannot be null.");
        return this;
    }

    @Override
    public EntityIdResolverPhase<I, E> criteriaResolver(
            @Nonnull ComponentBuilder<CriteriaResolver<I>> criteriaResolver
    ) {
        this.criteriaResolver = requireNonNull(criteriaResolver, "The criteria resolver cannot be null.");
        return this;
    }

    @Override
    public EntityFactoryPhase<I, E> entityModel(@Nonnull ComponentBuilder<EntityModel<E>> entityFactory) {
        this.entityModel = requireNonNull(entityFactory, "The entity model cannot be null.");
        return this;
    }

    @Override
    public EventSourcedEntityModule<I, E> entityIdResolver(
            @Nonnull ComponentBuilder<EntityIdResolver<I>> entityIdResolver) {
        this.entityIdResolver = requireNonNull(entityIdResolver, "The entity ID resolver cannot be null.");
        return this;
    }

    @Override
    public EventSourcedEntityModule<I, E> withoutCommandHandling() {
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
        registerComponents();
        return super.build(parent, lifecycleRegistry);
    }

    private void validate() {
        BuilderUtils.assertNonNull(entityFactory,
                                   "The EntityFactory must be provided to module [%s].".formatted(name()));
        BuilderUtils.assertNonNull(criteriaResolver,
                                   "The CriteriaResolver must be provided to module [%s].".formatted(name()));
        BuilderUtils.assertNonNull(entityModel, "The EntityModel must be provided to module [%s].".formatted(name()));
    }

    private void registerComponents() {
        componentRegistry(cr -> {
            cr.registerComponent(entityFactory());
            cr.registerComponent(criteriaResolver());
            cr.registerComponent(entityModel());
            cr.registerComponent(repository());

            if(entityIdResolver != null) {
                cr.registerComponent(idResolver());
                cr.registerComponent(commandHandlingComponent());
            }
        });
    }

    private ComponentDefinition<EntityModel<E>> entityModel() {
        return ComponentDefinition.ofTypeAndName(
                (ComponentDefinition.TypeReference<EntityModel<E>>) () -> EntityModel.class,
                entityName()
        ).withBuilder(entityModel);
    }

    private ComponentDefinition<EntityIdResolver<I>> idResolver() {
        return ComponentDefinition.ofTypeAndName(
                (ComponentDefinition.TypeReference<EntityIdResolver<I>>) () -> EntityIdResolver.class,
                entityName()
        ).withBuilder(entityIdResolver);
    }

    private ComponentDefinition<Repository<I, E>> repository() {
        return ComponentDefinition
                .ofTypeAndName((ComponentDefinition.TypeReference<Repository<I, E>>) () -> Repository.class,
                               entityName())
                .withBuilder(config -> {
                    //noinspection unchecked
                    return new EventSourcingRepository<I, E>(
                            idType,
                            entityType,
                            config.getComponent(EventStore.class),
                            config.getComponent(EventSourcedEntityFactory.class, entityName()),
                            config.getComponent(CriteriaResolver.class, entityName()),
                            config.getComponent(EntityModel.class, entityName())
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
                .withBuilder(c -> new EntityCommandHandlingComponent<I, E>(
                        c.getComponent(Repository.class, entityName()),
                        c.getComponent(EntityModel.class, entityName()),
                        c.getComponent(EntityIdResolver.class, entityName())
                ))
                .onStart(Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS,
                         (config, component) -> {
                             config.getComponent(CommandBus.class).subscribe(component);
                             return FutureUtils.emptyCompletedFuture();
                         }
                );
    }

    private ComponentDefinition<EventSourcedEntityFactory<I, E>> entityFactory() {
        return ComponentDefinition
                .ofTypeAndName((ComponentDefinition.TypeReference<EventSourcedEntityFactory<I, E>>) () -> EventSourcedEntityFactory.class,
                               entityName())
                .withBuilder(entityFactory);
    }

    private ComponentDefinition<CriteriaResolver<I>> criteriaResolver() {
        return ComponentDefinition
                .ofTypeAndName((ComponentDefinition.TypeReference<CriteriaResolver<I>>) () -> CriteriaResolver.class,
                               entityName())
                .withBuilder(criteriaResolver);
    }
}
