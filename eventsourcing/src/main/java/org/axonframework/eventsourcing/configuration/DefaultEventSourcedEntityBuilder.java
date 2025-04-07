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
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventStateApplier;
import org.axonframework.eventsourcing.MultiEventStateApplier;
import org.axonframework.eventsourcing.SingleEventEventStateApplier;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.repository.AsyncRepository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

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

    private final Class<I> idType;
    private final Class<E> entityType;
    private ComponentFactory<EventSourcedEntityFactory<I, E>> entityFactory;
    private ComponentFactory<CriteriaResolver<I>> criteriaResolver;
    private final List<ComponentFactory<EventStateApplier<E>>> eventStateApplierFactories = new CopyOnWriteArrayList<>();

    DefaultEventSourcedEntityBuilder(@Nonnull Class<I> idType, @Nonnull Class<E> entityType) {
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
    public EventSourcedEntityBuilder.EventSourcingHandlerPhase<I, E> eventStateApplier(
            @Nonnull ComponentFactory<EventStateApplier<E>> eventStateApplier
    ) {
        this.eventStateApplierFactories.add(requireNonNull(eventStateApplier,
                                                           "The event state applier cannot be null."));
        return this;
    }

    @Override
    public <P> EventSourcingHandlerPhase<I, E> eventSourcingHandler(@Nonnull QualifiedName eventName,
                                                                    @Nonnull Class<P> payloadType,
                                                                    @Nonnull BiConsumer<E, P> eventSourcingHandler) {
        return eventStateApplier(c -> new SingleEventEventStateApplier<>(eventName, payloadType, (model, payload) -> {
            eventSourcingHandler.accept(model, payload);
            return model;
        }));
    }

    @Override
    public <P> EventSourcingHandlerPhase<I, E> eventSourcingHandler(@Nonnull QualifiedName eventName,
                                                                    @Nonnull Class<P> payloadType,
                                                                    @Nonnull BiFunction<E, P, E> eventSourcingHandler) {
        return eventStateApplier(c -> new SingleEventEventStateApplier<>(eventName, payloadType, eventSourcingHandler));
    }

    @Override
    public <P> EventSourcingHandlerPhase<I, E> eventSourcingHandler(@Nonnull Class<P> payloadType,
                                                                    @Nonnull BiConsumer<E, P> eventSourcingHandler) {
        return eventStateApplier(c -> {
            QualifiedName eventName = resolveQualifiedName(payloadType, c);
            return new SingleEventEventStateApplier<>(eventName, payloadType, (model, payload) -> {
                eventSourcingHandler.accept(model, payload);
                return model;
            });
        });
    }

    @Override
    public <P> EventSourcingHandlerPhase<I, E> eventSourcingHandler(@Nonnull Class<P> payloadType,
                                                                    @Nonnull BiFunction<E, P, E> eventSourcingHandler) {
        return eventStateApplier(c -> {
            QualifiedName eventName = resolveQualifiedName(payloadType, c);
            return new SingleEventEventStateApplier<>(eventName, payloadType, eventSourcingHandler);
        });
    }

    private QualifiedName resolveQualifiedName(Class<?> payloadType, NewConfiguration configuration) {
        MessageTypeResolver messageTypeResolver = configuration.getComponent(MessageTypeResolver.class);
        return messageTypeResolver.resolve(payloadType).qualifiedName();
    }

    @Override
    public String entityName() {
        return entityType.getSimpleName() + "#" + idType.getSimpleName();
    }

    @Override
    public ComponentFactory<AsyncRepository<I, E>> repository() {
        return c -> new AsyncEventSourcingRepository<>(
                idType,
                entityType,
                c.getComponent(AsyncEventStore.class),
                entityFactory.build(c),
                criteriaResolver.build(c),
                constructEventStateApplier(c)
        );
    }

    private EventStateApplier<E> constructEventStateApplier(NewConfiguration c) {
        if (eventStateApplierFactories.size() == 1) {
            return eventStateApplierFactories.getFirst().build(c);
        }
        List<EventStateApplier<E>> eventStateAppliers = eventStateApplierFactories
                .stream()
                .map(eventStateApplier -> eventStateApplier.build(c))
                .toList();
        return new MultiEventStateApplier<>(eventStateAppliers);
    }
}
