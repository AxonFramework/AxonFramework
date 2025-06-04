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
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.PayloadBasedEntityEvolver;
import org.axonframework.modelling.SimpleEntityEvolvingComponent;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Basis implementation of the {@link EventSourcedEntityBuilder}.
 *
 * @param <I> The type of identifier used to identify the event-sourced entity that's being built.
 * @param <E> The type of the event-sourced entity being built.
 * @author Steven van Beelen
 * @since 5.0.0
 */
class DefaultEventSourcedEntityBuilder<I, E> implements
        EventSourcedEntityBuilder<I, E>,
        EventSourcedEntityBuilder.EntityFactoryPhase<I, E>,
        EventSourcedEntityBuilder.CriteriaResolverPhase<I, E>,
        EventSourcedEntityBuilder.EventSourcingHandlerPhase<I, E> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Class<I> idType;
    private final Class<E> entityType;
    private ComponentFactory<EventSourcedEntityFactory<I, E>> entityFactory;
    private ComponentFactory<CriteriaResolver<I>> criteriaResolver;
    private ComponentFactory<EntityEvolver<E>> entityEvolver;
    private final Map<QualifiedName, ComponentFactory<EntityEvolver<E>>> entityEvolverPerName = new HashMap<>();

    DefaultEventSourcedEntityBuilder(@Nonnull Class<I> idType,
                                     @Nonnull Class<E> entityType) {
        this.idType = requireNonNull(idType, "The identifier type cannot be null.");
        this.entityType = requireNonNull(entityType, "The entity type cannot be null.");
    }

    @Override
    public CriteriaResolverPhase<I, E> entityFactory(
            @Nonnull ComponentFactory<EventSourcedEntityFactory<I, E>> entityFactory
    ) {
        this.entityFactory = requireNonNull(entityFactory, "The entity factory cannot be null.");
        return this;
    }

    @Override
    public EventSourcingHandlerPhase<I, E> criteriaResolver(
            @Nonnull ComponentFactory<CriteriaResolver<I>> criteriaResolver
    ) {
        this.criteriaResolver = requireNonNull(criteriaResolver, "The criteria resolver cannot be null.");
        return this;
    }

    @Override
    public EventSourcedEntityBuilder<I, E> entityEvolver(@Nonnull ComponentFactory<EntityEvolver<E>> entityEvolver) {
        this.entityEvolver = requireNonNull(entityEvolver, "The event state applier cannot be null.");
        return this;
    }

    @Override
    public <P> EventSourcingHandlerPhase<I, E> eventSourcingHandler(@Nonnull QualifiedName eventName,
                                                                    @Nonnull Class<P> payloadType,
                                                                    @Nonnull BiFunction<E, P, E> eventSourcingHandler) {
        if (entityEvolverPerName.containsKey(eventName)) {
            throw new IllegalArgumentException("Event Sourcing Handler for name [" + eventName + "] already exists");
        }
        entityEvolverPerName.put(
                eventName,
                c -> new PayloadBasedEntityEvolver<>(payloadType, eventSourcingHandler)
        );
        return this;
    }

    @Override
    public String entityName() {
        return entityType.getSimpleName() + "#" + idType.getSimpleName();
    }

    @Override
    public ComponentFactory<Repository<I, E>> repository() {
        return config -> new EventSourcingRepository<>(
                idType,
                entityType,
                config.getComponent(EventStore.class),
                entityFactory.build(config),
                criteriaResolver.build(config),
                constructEntityEvolver(config)
        );
    }

    private EntityEvolver<E> constructEntityEvolver(Configuration config) {
        if (entityEvolver != null) {
            if (entityEvolverPerName.size() > 1) {
                logger.warn("Ignoring separate Event Sourcing Handlers since an EntityEvolver has been given!");
            }
            return entityEvolver.build(config);
        }
        return new SimpleEntityEvolvingComponent<>(buildAndCollectEntityEvolvers(config));
    }

    private Map<QualifiedName, EntityEvolver<E>> buildAndCollectEntityEvolvers(Configuration config) {
        return entityEvolverPerName.entrySet()
                                   .stream()
                                   .collect(Collectors.toMap(
                                           Map.Entry::getKey,
                                           entry -> entry.getValue()
                                                         .build(config)
                                   ));
    }
}
