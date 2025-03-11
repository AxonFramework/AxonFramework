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

package org.axonframework.modelling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.AsyncRepository;
import org.axonframework.modelling.repository.ManagedEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * Simple implementation of an {@link AsyncRepository} that can load and persist entities of a given type. When an
 * entity is loaded, it is stored in the {@link ProcessingContext} to ensure that the same entity is returned when it is
 * loaded again in the same context.
 * <p>
 * When an entity is successfully loaded, it's scheduled to automatically be persisted when the
 * {@link ProcessingContext} is committed.
 * <p>
 * Both the {@link #load(Object, ProcessingContext)} and {@link #loadOrCreate(Object, ProcessingContext)} use the
 * {@link SimpleEntityLoader} to load the entity. If you wish to create an entity when it does not exist, you can return
 * a value when you have found none.
 *
 * @param <I> The type of the identifier of the entity.
 * @param <T> The type of the entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class SimpleRepository<I, T> implements AsyncRepository.LifecycleManagement<I, T> {

    private final ResourceKey<Map<I, CompletableFuture<ManagedEntity<I, T>>>> managedEntitiesKey =
            ResourceKey.withLabel("SimpleRepository.ManagedEntities");

    private final Class<I> idType;
    private final Class<T> entityType;
    private final SimpleEntityLoader<I, T> loader;
    private final SimpleEntityPersister<I, T> persister;

    /**
     * Constructs a new simple {@link AsyncRepository} for entities of type {@code entityType} with identifiers of type
     * {@code idType}. The given {@code loader} is used to load entities and the given {@code persister} is used
     * to persist entities.
     *
     * @param idType     The type of the identifier of the entity.
     * @param entityType The type of the entity.
     * @param loader     The component capable of loading entities.
     * @param persister  The component capable of persisting entities.
     */
    public SimpleRepository(
            @Nonnull Class<I> idType,
            @Nonnull Class<T> entityType,
            @Nonnull SimpleEntityLoader<I, T> loader,
            @Nonnull SimpleEntityPersister<I, T> persister
    ) {
        this.idType = requireNonNull(idType, "The entityIdClass may not be null");
        this.entityType = requireNonNull(entityType, "The entityClass may not be null");
        this.loader = requireNonNull(loader, "The loader may not be null");
        this.persister = requireNonNull(persister, "The persister may not be null");
    }

    @Override
    public ManagedEntity<I, T> attach(@Nonnull ManagedEntity<I, T> entity,
                                      @Nonnull ProcessingContext context) {
        var managedEntities = context.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                entity.identifier(),
                id -> {
                    SimpleEntity<I, T> simpleEntity = SimpleEntity.mapToSimpleEntity(entity);
                    attachEntitySaveToContext(id, simpleEntity, context);
                    return CompletableFuture.completedFuture(simpleEntity);
                }
        ).resultNow();
    }

    @Override
    public Class<T> entityType() {
        return entityType;
    }

    @Override
    public Class<I> idType() {
        return idType;
    }

    @Override
    public CompletableFuture<ManagedEntity<I, T>> load(@Nonnull I id, @Nonnull ProcessingContext context) {
        var managedEntities = context.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);

        return managedEntities.computeIfAbsent(
                id,
                identifier -> loader
                        .load(identifier, context)
                        .thenApply((entity) -> {
                            SimpleEntity<I, T> managedEntity = new SimpleEntity<>(id, entity);
                            attachEntitySaveToContext(id, managedEntity, context);
                            return managedEntity;
                        })
        );
    }

    private void attachEntitySaveToContext(I id, ManagedEntity<I, T> managedEntity, ProcessingContext context) {
        context.onPrepareCommit(uow -> persister.persist(id, managedEntity.entity(), uow));
    }

    @Override
    public CompletableFuture<ManagedEntity<I, T>> loadOrCreate(@Nonnull I identifier,
                                                               @Nonnull ProcessingContext processingContext) {
        return load(identifier, processingContext);
    }

    @Override
    public ManagedEntity<I, T> persist(@Nonnull I id, @Nonnull T entity, @Nonnull ProcessingContext context) {
        var managedEntities = context.computeResourceIfAbsent(managedEntitiesKey, ConcurrentHashMap::new);
        if (managedEntities.containsKey(id)) {
            ManagedEntity<I, T> managedEntity = managedEntities.get(id).join();
            managedEntity.applyStateChange(oldState -> entity);
            return managedEntity;
        } else {
            var managedEntity = new SimpleEntity<>(id, entity);
            attachEntitySaveToContext(id, managedEntity, context);
            managedEntities.put(id, CompletableFuture.completedFuture(managedEntity));
            return managedEntity;
        }
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("idClass", idType);
        descriptor.describeProperty("entityClass", entityType);
    }

    /**
     * Simple implementation of a {@link ManagedEntity} that uses an {@link AtomicReference} to store the state of the
     * entity.
     *
     * @param <I> The type of the identifier of the entity.
     * @param <T> The type of the entity.
     */
    private static class SimpleEntity<I, T> implements ManagedEntity<I, T> {

        private final I id;
        private final AtomicReference<T> state;

        private SimpleEntity(I id, T state) {
            this.id = id;
            this.state = new AtomicReference<>(state);
        }

        private static <ID, T> SimpleEntity<ID, T> mapToSimpleEntity(ManagedEntity<ID, T> entity) {
            return entity instanceof SimpleEntity<ID, T> eventSourcedEntity
                    ? eventSourcedEntity
                    : new SimpleEntity<>(entity.identifier(), entity.entity());
        }

        @Override
        public I identifier() {
            return id;
        }

        @Override
        public T entity() {
            return state.get();
        }

        @Override
        public T applyStateChange(UnaryOperator<T> change) {
            return state.updateAndGet(change);
        }
    }
}
