/*
 * Copyright (c) 2010-2026. Axon Framework
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
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.core.Context.ResourceKey;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.repository.ManagedEntity;
import org.axonframework.modelling.repository.Repository;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * {@link Repository} implementation that loads entities based on their historic event streams, provided by an
 * {@link EventStore}.
 *
 * @param <ID> The type of identifier used to identify the event sourced entity.
 * @param <E>  The type of the event sourced entity to load.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 0.1
 */
public class EventSourcingRepository<ID, E> implements Repository.LifecycleManagement<ID, E> {

    private final ResourceKey<Map<ID, CompletableFuture<EventSourcedEntity<ID, E>>>> managedEntitiesKey =
            ResourceKey.withLabel("managedEntities");

    private final Class<ID> idType;
    private final Class<E> entityType;
    private final EventStore eventStore;
    private final CriteriaResolver<ID> sourceCriteriaResolver;
    private final CriteriaResolver<ID> appendCriteriaResolver;
    private final EntityEvolver<E> entityEvolver;
    private final EventSourcedEntityFactory<ID, E> entityFactory;

    /**
     * Initialize the repository to load events from the given {@code eventStore} using the given {@code applier} to
     * apply state transitions to the entity based on the events received, and given {@code criteriaResolver} to resolve
     * the {@link EventCriteria} of the given identifier type used to source an entity.
     * <p>
     * This constructor uses the same criteria resolver for both sourcing and appending (default backward-compatible behavior).
     *
     * @param idType           The type of the identifier for the event sourced entity this repository serves.
     * @param entityType       The type of the event sourced entity this repository serves.
     * @param eventStore       The event store to load events from.
     * @param entityFactory    A factory method to create new instances of the entity based on the entity's type and a
     *                         provided identifier.
     * @param criteriaResolver Converts the given identifier to an {@link EventCriteria} used to load a matching event
     *                         stream. Used for both sourcing and appending criteria.
     * @param entityEvolver    The function used to evolve the state of loaded entities based on events.
     */
    public EventSourcingRepository(@Nonnull Class<ID> idType,
                                   @Nonnull Class<E> entityType,
                                   @Nonnull EventStore eventStore,
                                   @Nonnull EventSourcedEntityFactory<ID, E> entityFactory,
                                   @Nonnull CriteriaResolver<ID> criteriaResolver,
                                   @Nonnull EntityEvolver<E> entityEvolver) {
        this(idType, entityType, eventStore, entityFactory, criteriaResolver, criteriaResolver, entityEvolver);
    }

    /**
     * Initialize the repository to load events from the given {@code eventStore} using the given {@code applier} to
     * apply state transitions to the entity based on the events received. This constructor enables Dynamic Consistency
     * Boundaries (DCB) where the criteria used for sourcing events (loading state) can differ from the criteria used
     * for consistency checking (appending events).
     * <p>
     * When {@code sourceCriteriaResolver} is {@code null}, no sourcing occurs — events are not loaded from the store.
     * This is used for standalone {@code @AppendCriteriaBuilder} scenarios where only consistency checking is needed
     * without loading state from events. The {@link ConsistencyMarker} is then resolved from the
     * {@link org.axonframework.eventsourcing.eventstore.EventStorageEngine#latestToken(ProcessingContext) latest token}.
     * <p>
     * <b>Example - Accounting Use Case:</b>
     * <ul>
     *   <li><b>sourceCriteriaResolver</b>: Resolves to criteria that loads both CreditsIncreased AND CreditsDecreased
     *       events to calculate the balance.</li>
     *   <li><b>appendCriteriaResolver</b>: Resolves to criteria that only checks for conflicts on CreditsDecreased
     *       events, allowing concurrent credit increases.</li>
     * </ul>
     *
     * @param idType                 The type of the identifier for the event sourced entity this repository serves.
     * @param entityType             The type of the event sourced entity this repository serves.
     * @param eventStore             The event store to load events from.
     * @param entityFactory          A factory method to create new instances of the entity based on the entity's type and a
     *                               provided identifier.
     * @param sourceCriteriaResolver Converts the given identifier to an {@link EventCriteria} used to load (source)
     *                               the entity's event stream. May be {@code null} to skip sourcing entirely.
     * @param appendCriteriaResolver Converts the given identifier to an {@link EventCriteria} used for consistency
     *                               checking when appending events.
     * @param entityEvolver          The function used to evolve the state of loaded entities based on events.
     */
    public EventSourcingRepository(@Nonnull Class<ID> idType,
                                   @Nonnull Class<E> entityType,
                                   @Nonnull EventStore eventStore,
                                   @Nonnull EventSourcedEntityFactory<ID, E> entityFactory,
                                   @Nullable CriteriaResolver<ID> sourceCriteriaResolver,
                                   @Nonnull CriteriaResolver<ID> appendCriteriaResolver,
                                   @Nonnull EntityEvolver<E> entityEvolver) {
        this.idType = requireNonNull(idType, "The id type must not be null.");
        this.entityType = requireNonNull(entityType, "The entity type must not be null.");
        this.eventStore = requireNonNull(eventStore, "The event store must not be null.");
        this.entityFactory = requireNonNull(entityFactory, "The entity factory must not be null.");
        this.sourceCriteriaResolver = sourceCriteriaResolver;
        this.appendCriteriaResolver = requireNonNull(appendCriteriaResolver, "The append criteria resolver must not be null.");
        this.entityEvolver = requireNonNull(entityEvolver, "The entity evolver must not be null.");
    }

    @Override
    public ManagedEntity<ID, E> attach(@Nonnull ManagedEntity<ID, E> entity,
                                       @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                entity.identifier(),
                id -> {
                    EventSourcedEntity<ID, E> sourcedEntity = EventSourcedEntity.mapToEventSourcedEntity(entity);
                    updateActiveEntity(sourcedEntity, processingContext);
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
    public Class<ID> idType() {
        return idType;
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, E>> load(@Nonnull ID identifier,
                                                        @Nonnull ProcessingContext context) {
        var managedEntities = context.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                identifier,
                id -> doLoad(identifier, context)
                        .whenComplete((entity, exception) -> updateActiveEntity(entity, context, exception))
        ).thenApply(Function.identity());
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, E>> loadOrCreate(@Nonnull ID identifier,
                                                                @Nonnull ProcessingContext context) {
        var managedEntities = context.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                identifier,
                id -> doLoad(identifier, context)
                        .thenApply((entity) -> createEntityIfNullFromLoad(identifier, entity, context))
                        .whenComplete((entity, exception) -> updateActiveEntity(entity, context, exception))
        ).thenApply(Function.identity());
    }

    private EventSourcedEntity<ID, E> createEntityIfNullFromLoad(ID identifier, EventSourcedEntity<ID, E> entity,
                                                                 ProcessingContext context) {
        if (entity.entity() == null) {
            E createdEntity = entityFactory.create(identifier, null, context);
            if (createdEntity == null) {
                throw new EntityMissingAfterLoadOrCreateException(identifier);
            }
            return new EventSourcedEntity<>(identifier, createdEntity);
        }
        return entity;
    }

    private CompletableFuture<EventSourcedEntity<ID, E>> doLoad(ID identifier, ProcessingContext context) {
        if (sourceCriteriaResolver == null) {
            // Standalone append criteria without sourcing (e.g., only @AppendCriteriaBuilder defined).
            // No events to load — the transaction will be created in updateActiveEntity when appending.
            return CompletableFuture.completedFuture(new EventSourcedEntity<>(identifier));
        }

        EventCriteria sourceCriteria = sourceCriteriaResolver.resolve(identifier, context);
        EventCriteria appendCriteria = appendCriteriaResolver.resolve(identifier, context);

        return eventStore
                .transaction(appendCriteria, context)
                .source(SourcingCondition.conditionFor(sourceCriteria))
                .reduce(new EventSourcedEntity<>(identifier),
                        (entity, entry) -> {
                            entity.ensureInitialState(() -> entityFactory.create(identifier, entry.message(), context));
                            entity.evolve(entry.message(), entityEvolver, context);
                            return entity;
                        });
    }

    @Override
    public ManagedEntity<ID, E> persist(@Nonnull ID identifier,
                                        @Nonnull E entity,
                                        @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(identifier, id -> {
            EventSourcedEntity<ID, E> sourcedEntity = new EventSourcedEntity<>(identifier, entity);
            updateActiveEntity(sourcedEntity, processingContext);
            return CompletableFuture.completedFuture(sourcedEntity);
        }).resultNow();
    }

    private void updateActiveEntity(EventSourcedEntity<ID, E> entity, ProcessingContext processingContext,
                                    Throwable exception) {
        if (exception == null) {
            updateActiveEntity(entity, processingContext);
        }
    }

    /**
     * Update the given {@code entity} for any event that is published within its lifecycle, by invoking the
     * {@link EntityEvolver} in the {@link EventStoreTransaction#onAppend(Consumer)}. The {@code onAppend} hook is used
     * to immediately source events that are being published by the entity.
     *
     * @param entity            An {@link ManagedEntity} to make the state change for.
     * @param processingContext The {@link ProcessingContext} for which to retrieve the active
     *                          {@link EventStoreTransaction}.
     */
    private void updateActiveEntity(EventSourcedEntity<ID, E> entity, ProcessingContext processingContext) {
        EventCriteria appendCriteria = appendCriteriaResolver.resolve(entity.identifier(), processingContext);
        eventStore.transaction(appendCriteria, processingContext)
                  .onAppend(event -> {
                      if (entity.entity() == null) {
                          entity.applyStateChange(e -> entityFactory.create(
                                  entity.identifier(), event, processingContext));
                      } else {
                          entity.evolve(event, entityEvolver, processingContext);
                      }
                  });
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("idType", idType);
        descriptor.describeProperty("entityType", entityType);
        descriptor.describeProperty("eventStore", eventStore);
        descriptor.describeProperty("entityFactory", entityFactory);
        descriptor.describeProperty("sourceCriteriaResolver", sourceCriteriaResolver);
        descriptor.describeProperty("appendCriteriaResolver", appendCriteriaResolver);
        descriptor.describeProperty("entityEvolver", entityEvolver);
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
        private boolean initialized;

        private EventSourcedEntity(ID identifier) {
            this(identifier, null);
        }

        private EventSourcedEntity(ID identifier, M currentState) {
            this.identifier = identifier;
            this.currentState = new AtomicReference<>(currentState);
            this.initialized = currentState != null;
        }

        private static <ID, T> EventSourcedEntity<ID, T> mapToEventSourcedEntity(ManagedEntity<ID, T> entity) {
            return entity instanceof EventSourcingRepository.EventSourcedEntity<ID, T> eventSourcedEntity
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

        /**
         * Initialize this entity with an initial state if it has not been initialized yet. This method will set the
         * current state to the value returned by the given {@code initialState} supplier, and mark the entity as
         * initialized. After the first invocation, this entity will be considered initialized, and further invocations
         * will have no effect.
         *
         * @param initialStateSupplier The supplier that provides the initial state of the entity.
         */
        public void ensureInitialState(Supplier<M> initialStateSupplier) {
            if (!initialized) {
                this.initialized = true;
                M entityInitialState = initialStateSupplier.get();
                if (entityInitialState == null) {
                    throw new EntityMissingAfterFirstEventException(identifier);
                }
                this.currentState.set(entityInitialState);
            }
        }

        private M evolve(EventMessage event,
                         EntityEvolver<M> evolver,
                         ProcessingContext processingContext) {
            this.initialized = true;
            return currentState.updateAndGet(current -> evolver.evolve(current, event, processingContext));
        }
    }
}
