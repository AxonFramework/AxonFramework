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

/**
 * {@link AsyncRepository} implementation that loads entities based on their historic event streams, provided by an
 * {@link AsyncEventStore}.
 *
 * @param <ID> The type of identifier used to identify the entity.
 * @param <M>  The type of the model to load.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 0.1
 */
public class AsyncEventSourcingRepository<ID, M> implements AsyncRepository.LifecycleManagement<ID, M> {

    private final ResourceKey<Map<ID, CompletableFuture<EventSourcedEntity<ID, M>>>> managedEntitiesKey =
            ResourceKey.withLabel("managedEntities");

    private final Class<ID> idType;
    private final Class<M> entityType;
    private final AsyncEventStore eventStore;
    private final CriteriaResolver<ID> criteriaResolver;
    private final EventStateApplier<M> eventStateApplier;
    private final String context;
    private final Function<ID, M> newInstanceFactory;

    /**
     * Initialize the repository to load events from the given {@code eventStore} using the given {@code applier} to
     * apply state transitions to the entity based on the events received, and given {@code criteriaResolver} to resolve
     * the {@link org.axonframework.eventsourcing.eventstore.EventCriteria} of the given identifier type used to source
     * a model.
     *
     * @param eventStore        The event store to load events from.
     * @param criteriaResolver  Converts the given identifier to an
     *                          {@link org.axonframework.eventsourcing.eventstore.EventCriteria} used to load a matching
     *                          event stream.
     * @param eventStateApplier The function to apply event state changes to the loaded entities.
     * @param newInstanceFactory A factory method to create new instances of the model based on a provided identifier.
     * @param context            The (bounded) context this {@code AsyncEventSourcingRepository} provides access to
     *                           models for.
     */
    public AsyncEventSourcingRepository(
            Class<ID> idType,
            Class<M> entityType,
            AsyncEventStore eventStore,
            CriteriaResolver<ID> criteriaResolver,
            EventStateApplier<M> eventStateApplier,
            Function<ID, M> newInstanceFactory,
            String context) {
        this.idType = idType;
        this.entityType = entityType;
        this.eventStore = eventStore;
        this.criteriaResolver = criteriaResolver;
        this.eventStateApplier = eventStateApplier;
        this.newInstanceFactory = newInstanceFactory;
        this.context = context;
    }

    @Override
    public ManagedEntity<ID, M> attach(@Nonnull ManagedEntity<ID, M> entity,
                                       @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                entity.identifier(),
                id -> {
                    EventSourcedEntity<ID, M> sourcedEntity = EventSourcedEntity.mapToEventSourcedEntity(entity);
                    updateActiveModel(sourcedEntity, processingContext);
                    return CompletableFuture.completedFuture(sourcedEntity);
                }
        ).resultNow();
    }

    @Nonnull
    @Override
    public Class<M> entityType() {
        return entityType;
    }

    @Nonnull
    @Override
    public Class<ID> idType() {
        return idType;
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, M>> load(@Nonnull ID identifier,
                                                        @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                identifier,
                id -> eventStore.transaction(processingContext, context)
                                .source(SourcingCondition.conditionFor(criteriaResolver.resolve(id)))
                                .reduce(new EventSourcedEntity<>(identifier, newInstanceFactory.apply(identifier)),
                                        (entity, entry) -> {
                                    entity.applyStateChange(entry.message(), eventStateApplier, processingContext);
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
    public CompletableFuture<ManagedEntity<ID, M>> loadOrCreate(@Nonnull ID identifier,
                                                                @Nonnull ProcessingContext processingContext) {
        return load(identifier, processingContext).thenApply(
                managedEntity -> {
                    managedEntity.applyStateChange(
                            entity -> entity != null ? entity : newInstanceFactory.apply(identifier)
                    );
                    return managedEntity;
                }
        );
    }

    @Override
    public ManagedEntity<ID, M> persist(@Nonnull ID identifier,
                                        @Nonnull M entity,
                                        @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(identifier, id -> {
            EventSourcedEntity<ID, M> sourcedEntity = new EventSourcedEntity<>(identifier, entity);
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
    private void updateActiveModel(EventSourcedEntity<ID, M> entity, ProcessingContext processingContext) {
        eventStore.transaction(processingContext, context)
                  .onAppend(event -> entity.applyStateChange(event, eventStateApplier, processingContext));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("idType", idType);
        descriptor.describeProperty("entityType", entityType);
        descriptor.describeProperty("eventStore", eventStore);
        descriptor.describeProperty("criteriaResolver", criteriaResolver);
        descriptor.describeProperty("eventStateApplier", eventStateApplier);
        descriptor.describeProperty("context", context);
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
