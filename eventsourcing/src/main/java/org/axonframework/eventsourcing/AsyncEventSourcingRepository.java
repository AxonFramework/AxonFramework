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

package org.axonframework.eventsourcing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.AsyncRepository;
import org.axonframework.modelling.repository.ManagedEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * {@link AsyncRepository} implementation that loads entities based on their historic event streams, provided by an
 * {@link AsyncEventStore}.
 *
 * @param <I> The type of identifier used to identify the entity.
 * @param <E> The type of the entity to load.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 0.1
 */
public class AsyncEventSourcingRepository<I, E> implements AsyncRepository.LifecycleManagement<I, E> {

    private final ResourceKey<Map<I, CompletableFuture<EventSourcedEntity<I, E>>>> managedEntitiesKey =
            ResourceKey.withLabel("managedEntities");

    private final Class<I> idType;
    private final Class<E> entityType;
    private final AsyncEventStore eventStore;
    private final CriteriaResolver<I> criteriaResolver;
    private final EventStateApplier<E> eventStateApplier;
    private final EventSourcedEntityFactory<I, E> entityFactory;

    /**
     * Initialize the repository to load events from the given {@code eventStore} using the given {@code applier} to
     * apply state transitions to the entity based on the events received, and given {@code criteriaResolver} to resolve
     * the {@link org.axonframework.eventsourcing.eventstore.EventCriteria} of the given identifier type used to source
     * a model.
     *
     * @param idType            The type of the identifier for the event sourced entity this repository serves.
     * @param entityType        The type of the event sourced entity this repository serves.
     * @param eventStore        The event store to load events from.
     * @param entityFactory     A factory method to create new instances of the entity based on the entity's type and a
     *                          provided identifier.
     * @param criteriaResolver  Converts the given identifier to an
     *                          {@link org.axonframework.eventsourcing.eventstore.EventCriteria} used to load a matching
     *                          event stream.
     * @param eventStateApplier The function to apply event state changes to the loaded entities.
     */
    public AsyncEventSourcingRepository(@Nonnull Class<I> idType,
                                        @Nonnull Class<E> entityType,
                                        @Nonnull AsyncEventStore eventStore,
                                        @Nonnull EventSourcedEntityFactory<I, E> entityFactory,
                                        @Nonnull CriteriaResolver<I> criteriaResolver,
                                        @Nonnull EventStateApplier<E> eventStateApplier) {
        this.idType = requireNonNull(idType, "The id type cannot be null.");
        this.entityType = requireNonNull(entityType, "The entity type cannot be null.");
        this.eventStore = requireNonNull(eventStore, "The event store cannot be null.");
        this.entityFactory = requireNonNull(entityFactory, "The entity factory cannot be null.");
        this.criteriaResolver = requireNonNull(criteriaResolver, "The criteria resolver cannot be null.");
        this.eventStateApplier = requireNonNull(eventStateApplier, "The event state applier cannot be null.");
    }

    @Override
    public ManagedEntity<I, E> attach(@Nonnull ManagedEntity<I, E> entity,
                                      @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                entity.identifier(),
                id -> {
                    EventSourcedEntity<I, E> sourcedEntity = EventSourcedEntity.mapToEventSourcedEntity(entity);
                    updateActiveModel(sourcedEntity, processingContext);
                    return CompletableFuture.completedFuture(sourcedEntity);
                }
        ).resultNow();
    }

    @Nonnull
    @Override
    public Class<E> entityType() {
        return entityType;
    }

    @Nonnull
    @Override
    public Class<I> idType() {
        return idType;
    }

    @Override
    public CompletableFuture<ManagedEntity<I, E>> load(@Nonnull I identifier,
                                                       @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                identifier,
                id -> eventStore.transaction(processingContext)
                                .source(SourcingCondition.conditionFor(criteriaResolver.resolve(id)))
                                .reduce(new EventSourcedEntity<>(
                                                identifier,
                                                entityFactory.createEntity(entityType(), identifier)
                                        ),
                                        (entity, entry) -> {
                                            entity.applyStateChange(entry.message(),
                                                                    eventStateApplier,
                                                                    processingContext);
                                            return entity;
                                        })
                                .whenComplete((entity, exception) -> {
                                    if (exception == null) {
                                        updateActiveModel(entity, processingContext);
                                    }
                                })
        ).thenApply(Function.identity());
    }

    @Override
    public CompletableFuture<ManagedEntity<I, E>> loadOrCreate(@Nonnull I identifier,
                                                               @Nonnull ProcessingContext processingContext) {
        return load(identifier, processingContext).thenApply(
                managedEntity -> {
                    managedEntity.applyStateChange(
                            entity -> entity != null
                                    ? entity
                                    : entityFactory.createEntity(entityType(), identifier)
                    );
                    return managedEntity;
                }
        );
    }

    @Override
    public ManagedEntity<I, E> persist(@Nonnull I identifier,
                                       @Nonnull E entity,
                                       @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(identifier, id -> {
            EventSourcedEntity<I, E> sourcedEntity = new EventSourcedEntity<>(identifier, entity);
            updateActiveModel(sourcedEntity, processingContext);
            return CompletableFuture.completedFuture(sourcedEntity);
        }).resultNow();
    }

    /**
     * Update the given {@code entity} for any event that is published within its lifecycle, by invoking the
     * {@link EventStateApplier} in the {@link EventStoreTransaction#onAppend(Consumer)}. onAppend hook is used to
     * immediately source events that are being published by the model
     *
     * @param entity            An {@link ManagedEntity} to make the state change for.
     * @param processingContext The {@link ProcessingContext} for which to retrieve the active
     *                          {@link EventStoreTransaction}.
     */
    private void updateActiveModel(EventSourcedEntity<I, E> entity, ProcessingContext processingContext) {
        eventStore.transaction(processingContext)
                  .onAppend(event -> entity.applyStateChange(event, eventStateApplier, processingContext));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("idType", idType);
        descriptor.describeProperty("entityType", entityType);
        descriptor.describeProperty("eventStore", eventStore);
        descriptor.describeProperty("entityFactory", entityFactory);
        descriptor.describeProperty("criteriaResolver", criteriaResolver);
        descriptor.describeProperty("eventStateApplier", eventStateApplier);
    }

    /**
     * Private implementation of the {@link ManagedEntity} supporting event sourcing.
     *
     * @param <ID> The type of identifier of the event sourced entity.
     * @param <M>  The type of entity managed by this event sourced entity.
     */
    private static class EventSourcedEntity<ID, M> implements ManagedEntity<ID, M> {

        private final ID identifier;
        private final AtomicReference<M> currentState;

        private EventSourcedEntity(ID identifier, M currentState) {
            this.identifier = identifier;
            this.currentState = new AtomicReference<>(currentState);
        }

        private static <ID, T> EventSourcedEntity<ID, T> mapToEventSourcedEntity(ManagedEntity<ID, T> entity) {
            return entity instanceof AsyncEventSourcingRepository.EventSourcedEntity<ID, T> eventSourcedEntity
                    ? eventSourcedEntity
                    : new EventSourcedEntity<>(entity.identifier(), entity.entity());
        }

        @Override
        public ID identifier() {
            return identifier;
        }

        @Override
        public M entity() {
            return currentState.get();
        }

        @Override
        public M applyStateChange(UnaryOperator<M> change) {
            return currentState.updateAndGet(change);
        }

        private M applyStateChange(EventMessage<?> event, EventStateApplier<M> applier,
                                   ProcessingContext processingContext) {
            return currentState.updateAndGet(current -> applier.apply(current, event, processingContext));
        }
    }
}
