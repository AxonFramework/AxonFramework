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

package org.axonframework.modelling.repository;

import jakarta.annotation.Nonnull;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

/**
 * In-memory implementation of the {@link Repository} interface that stores entities in a {@link ConcurrentHashMap}.
 * This implementation uses a {@link SimpleRepository} as a delegate to handle entity lifecycle management while
 * providing persistent storage in memory.
 * <p>
 * This repository is suitable for testing purposes, prototyping, or scenarios where entities need to be stored in
 * memory without external persistence. The storage is thread-safe through the use of {@code ConcurrentHashMap}.
 * <p>
 * The repository automatically manages entity lifecycle through the {@link SimpleRepository} delegate, ensuring proper
 * integration with the {@link ProcessingContext} and automatic persistence on commit.
 * <p>
 * Example usage:
 * <pre>{@code
 * InMemoryRepository<String, MyEntity> repository = new InMemoryRepository<>(
 *     String.class,
 *     MyEntity.class
 * );
 * }</pre>
 *
 * @param <ID> The type of identifier for entities in this repository.
 * @param <E>  The type of entity stored in this repository.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class InMemoryRepository<ID, E> implements Repository.LifecycleManagement<ID, E> {

    private final SimpleRepository<ID, E> delegate;
    private final ConcurrentMap<ID, E> storage;

    /**
     * Constructs a new {@link InMemoryRepository} for entities of type {@code entityType} with identifiers of type
     * {@code idType}.
     * <p>
     * The repository uses a {@link ConcurrentHashMap} as the underlying storage mechanism and delegates entity
     * lifecycle management to a {@link SimpleRepository}.
     *
     * @param idType     The type of the identifier for entities in this repository.
     * @param entityType The type of entity stored in this repository.
     */
    public InMemoryRepository(@Nonnull Class<ID> idType, @Nonnull Class<E> entityType) {
        requireNonNull(idType, "The idType may not be null");
        requireNonNull(entityType, "The entityType may not be null");

        this.storage = new ConcurrentHashMap<>();
        this.delegate = new SimpleRepository<>(
                idType,
                entityType,
                (identifier, processingContext) -> CompletableFuture.completedFuture(storage.get(identifier)),
                (identifier, entity, processingContext) -> {
                    if (entity != null) { // it's sometimes null!
                        storage.put(identifier, entity);
                    }
                    return FutureUtils.emptyCompletedFuture();
                }
        );
    }

    @Override
    public ManagedEntity<ID, E> attach(@Nonnull ManagedEntity<ID, E> entity,
                                       @Nonnull ProcessingContext processingContext) {
        return delegate.attach(entity, processingContext);
    }

    @Nonnull
    @Override
    public Class<E> entityType() {
        return delegate.entityType();
    }

    @Nonnull
    @Override
    public Class<ID> idType() {
        return delegate.idType();
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, E>> load(@Nonnull ID identifier,
                                                        @Nonnull ProcessingContext processingContext) {
        return delegate.load(identifier, processingContext);
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, E>> loadOrCreate(@Nonnull ID identifier,
                                                                @Nonnull ProcessingContext processingContext) {
        return delegate.loadOrCreate(identifier, processingContext);
    }

    @Override
    public ManagedEntity<ID, E> persist(@Nonnull ID identifier, @Nonnull E entity,
                                        @Nonnull ProcessingContext processingContext) {
        return delegate.persist(identifier, entity, processingContext);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("storageType", "ConcurrentHashMap");
        descriptor.describeProperty("storageSize", storage.size());
        descriptor.describeWrapperOf(delegate);
    }
}
