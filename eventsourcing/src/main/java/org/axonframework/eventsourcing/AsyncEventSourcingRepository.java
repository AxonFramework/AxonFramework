/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext.ResourceKey;
import org.axonframework.modelling.repository.AsyncRepository;
import org.axonframework.modelling.repository.ManagedEntity;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

/**
 * {@link AsyncRepository} implementation that loads entities based on their historic event streams, provided by an
 * {@link EventStore}.
 *
 * @param <ID> The type of identifier used to identify the entity.
 * @param <M>  The type of the model to load.
 * @author Allard Buijze
 * @since 0.1
 */
public class AsyncEventSourcingRepository<ID, M> implements AsyncRepository.LifecycleManagement<ID, M> {

    private final ResourceKey<Map<ID, CompletableFuture<EventSourcedEntity<ID, M>>>> managedEntitiesKey =
            ResourceKey.create("managedEntities");

    private final EventStore eventStore;
    private final IdentifierResolver<ID> identifierResolver;
    private final EventStateApplier<M> eventStateApplier;
    // TODO #3093 - This should be a revamp of the AggregateFactory. IF this is the way to go.
    private final Supplier<M> modelFactory;

    /**
     * Initialize the repository to load events from the given {@code eventStore} using the given {@code applier} to
     * apply state transitions to the entity based on the events received, and given {@code identifierResolver} to
     * resolve the aggregate identifier from the given identifier type.
     *
     * @param eventStore         The event store to load events from.
     * @param identifierResolver Converts the given identifier to an aggregate identifier to load the event stream.
     * @param eventStateApplier  The function to apply event state changes to the loaded entities.
     * @param modelFactory       Supplier of the model this repository constructs. Used to define the default instance
     *                           given to the {@code eventStateApplier}.
     */
    public AsyncEventSourcingRepository(EventStore eventStore,
                                        IdentifierResolver<ID> identifierResolver,
                                        EventStateApplier<M> eventStateApplier,
                                        Supplier<M> modelFactory) {
        this.eventStore = eventStore;
        this.identifierResolver = identifierResolver;
        this.eventStateApplier = eventStateApplier;
        this.modelFactory = modelFactory;
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

    @Override
    public CompletableFuture<ManagedEntity<ID, M>> load(@Nonnull ID identifier,
                                                        @Nonnull ProcessingContext processingContext,
                                                        long start,
                                                        long end) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                identifier,
                id -> {
                    DomainEventStream eventStream = eventStore.readEvents(identifierResolver.resolve(identifier));
                    M currentState = null;
                    while (eventStream.hasNext()) {
                        DomainEventMessage<?> nextEvent = eventStream.next();
                        currentState = eventStateApplier.apply(currentState, nextEvent);
                    }

                    EventSourcedEntity<ID, M> managedEntity = new EventSourcedEntity<>(identifier, currentState);
                    updateActiveModel(managedEntity, processingContext);
                    return CompletableFuture.completedFuture(managedEntity);
                }
        ).thenApply(Function.identity());
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, M>> loadOrCreate(@Nonnull ID identifier,
                                                                @Nonnull ProcessingContext processingContext,
                                                                @Nonnull Supplier<M> factoryMethod) {
        return load(identifier, processingContext).thenApply(
                managedEntity -> {
                    managedEntity.applyStateChange(
                            entity -> entity == null || entity.equals(modelFactory.get()) ? factoryMethod.get() : entity
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
     * {@link EventStateApplier} in the
     * {@link org.axonframework.eventsourcing.eventstore.AppendEventTransaction#onAppend(Consumer)}. onAppend hook is
     * used to immediately source events that are being published by the model
     *
     * @param entity            An {@link ManagedEntity} to make the state change for.
     * @param processingContext The context for which to retrieve the active
     *                          {@link org.axonframework.eventsourcing.eventstore.AppendEventTransaction}.
     */
    private void updateActiveModel(EventSourcedEntity<ID, M> entity, ProcessingContext processingContext) {
        eventStore.currentTransaction(processingContext)
                  .onAppend(event -> entity.applyStateChange(event, eventStateApplier));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventStore", eventStore);
        descriptor.describeProperty("identifierResolver", identifierResolver);
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

        private M applyStateChange(EventMessage<?> event, EventStateApplier<M> applier) {
            return currentState.updateAndGet(current -> applier.changeState(current, event));
        }
    }
}
