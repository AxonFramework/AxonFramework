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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

/**
 * Repository implementation that loads entities based on their historic event streams, provided by an Event Store.
 *
 * @param <ID> The type of identifier used to identify the entity
 * @param <T>  The type of the entity to load
 */
public class AsyncEventSourcingRepository<ID, T> implements AsyncRepository.LifecycleManagement<ID, T> {

    private final ResourceKey<Map<ID, CompletableFuture<EventSourcedEntity<ID, T>>>> managedEntitiesKey = ResourceKey.create(
            "managedEntities");

    private final EventStore eventStore;
    private final BiFunction<EventMessage<?>, T, T> applier;
    private final Function<ID, String> identifierResolver;

    /**
     * Initialize the repository to load events from the given {@code eventStore} using the given {@code applier} to
     * apply state transitions to the entity based on the events received, and given {@code identifierResolver} to
     * resolve the aggregate identifier from the given identifier type.
     *
     * @param eventStore         The event store to load events from
     * @param applier            THe function to apply state changes to the loaded entities
     * @param identifierResolver Converts the given identifier to an aggregate identifier to load the event stream
     */
    public AsyncEventSourcingRepository(EventStore eventStore, BiFunction<EventMessage<?>, T, T> applier,
                                        Function<ID, String> identifierResolver) {
        this.eventStore = eventStore;
        this.applier = applier;
        this.identifierResolver = identifierResolver;
    }

    @Override
    public ManagedEntity<ID, T> attach(@Nonnull ManagedEntity<ID, T> entity,
                                       @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        CompletableFuture<EventSourcedEntity<ID, T>> result = managedEntities.computeIfAbsent(
                entity.identifier(),
                id -> {
                    EventSourcedEntity<ID, T> managedEntity = EventSourcedEntity.fromCached(entity);
                    eventStore.currentTransaction(processingContext)
                              .onEvent(event -> managedEntity.applyStateChange(event, applier));
                    return CompletableFuture.completedFuture(managedEntity);
                });
        return result.resultNow();
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, T>> load(@Nonnull ID identifier,
                                                        @Nonnull ProcessingContext processingContext) {
        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(identifier, id -> {
            DomainEventStream eventStream = eventStore.readEvents(identifierResolver.apply(identifier));
            T currentState = null;

            while (eventStream.hasNext()) {
                DomainEventMessage<?> nextEvent = eventStream.next();
                currentState = applier.apply(nextEvent, currentState);
            }
            EventSourcedEntity<ID, T> managedEntity = new EventSourcedEntity<>(identifier, currentState);
            eventStore.currentTransaction(processingContext).onEvent(event -> managedEntity.applyStateChange(event,
                                                                                                             applier));

            return CompletableFuture.completedFuture(managedEntity);
        }).thenApply(Function.identity());
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, T>> loadOrCreate(@Nonnull ID identifier,
                                                                @Nonnull ProcessingContext processingContext,
                                                                @Nonnull Supplier<T> factoryMethod) {
        return load(identifier, processingContext)
                .thenApply(e -> {
                    e.applyStateChange(c -> c != null ? c : factoryMethod.get());
                    return e;
                });
    }

    @Override
    public ManagedEntity<ID, T> persist(@Nonnull ID identifier, @Nonnull T entity,
                                        @Nonnull ProcessingContext processingContext) {

        var managedEntities = processingContext.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);
        return managedEntities.computeIfAbsent(identifier, id -> {
            EventSourcedEntity<ID, T> managedEntity = new EventSourcedEntity<>(identifier, entity);
            eventStore.currentTransaction(processingContext).onEvent(event -> managedEntity.applyStateChange(event,
                                                                                                             applier));
            return CompletableFuture.completedFuture(managedEntity);
        }).resultNow();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventStore", eventStore);
        descriptor.describeProperty("identifierResolver", identifierResolver);
        descriptor.describeProperty("applier", applier);
    }

    private static class EventSourcedEntity<ID, T> implements ManagedEntity<ID, T> {

        private final ID identifier;
        private final AtomicReference<T> currentState;

        public EventSourcedEntity(ID identifier, T currentState) {
            this.identifier = identifier;
            this.currentState = new AtomicReference<>(currentState);
        }

        public static <ID, T> EventSourcedEntity<ID, T> fromCached(ManagedEntity<ID, T> entity) {
            if (entity instanceof AsyncEventSourcingRepository.EventSourcedEntity<ID, T> ese) {
                return ese;
            }
            return new EventSourcedEntity<>(entity.identifier(), entity.entity());
        }

        @Override
        public ID identifier() {
            return identifier;
        }

        @Override
        public T entity() {
            return currentState.get();
        }

        @Override
        public T applyStateChange(UnaryOperator<T> change) {
            return currentState.updateAndGet(change);
        }

        public T applyStateChange(EventMessage<?> event, BiFunction<EventMessage<?>, T, T> change) {
            return currentState.updateAndGet(current -> change.apply(event, current));
        }
    }
}
