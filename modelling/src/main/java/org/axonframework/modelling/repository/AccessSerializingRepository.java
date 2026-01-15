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

package org.axonframework.modelling.repository;

import jakarta.annotation.Nonnull;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.core.Context.ResourceKey;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Repository implementation that ensures safe concurrent access to entities stored in it. It delegates the actual
 * loading of entities to another {@link Repository}, but attempts to pass loaded elements to waiting components
 * immediately, to avoid avoidable load operations on the underlying repository.
 *
 * @param <ID> The type of identifier used to identify entities stored by this repository.
 * @param <E>  The type of entity stored in this repository.
 * @author Allard Buijze
 * @since 5.0.0
 */
public class AccessSerializingRepository<ID, E>
        implements Repository.LifecycleManagement<ID, E>, DescribableComponent {

    private static final Logger logger = LoggerFactory.getLogger(AccessSerializingRepository.class);

    private final ResourceKey<ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, E>>>> workingEntitiesKey =
            ResourceKey.withLabel("workingEntities");

    private final Repository.LifecycleManagement<ID, E> delegate;
    private final ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, E>>> inProgress;

    /**
     * Initialize the repository to serialize access to given {@code delegate}.
     *
     * @param delegate The repository implementation to delegate loading of the entities to
     */
    public AccessSerializingRepository(Repository.LifecycleManagement<ID, E> delegate) {
        this.delegate = delegate;
        this.inProgress = new ConcurrentHashMap<>();
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
        return awaitTurn(identifier,
                         processingContext,
                         () -> delegate.load(identifier, processingContext));
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, E>> loadOrCreate(@Nonnull ID identifier,
                                                                @Nonnull ProcessingContext processingContext) {
        return awaitTurn(identifier,
                         processingContext,
                         () -> delegate.loadOrCreate(identifier, processingContext));
    }

    private CompletableFuture<ManagedEntity<ID, E>> awaitTurn(
            ID identifier,
            ProcessingContext processingContext,
            Supplier<CompletableFuture<ManagedEntity<ID, E>>> entitySupplier
    ) {
        logger.debug("Attempting to load [{}] in {}", identifier, processingContext);
        ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, E>>> workingEntities =
                processingContext.computeResourceIfAbsent(workingEntitiesKey, ConcurrentHashMap::new);

        if (workingEntities.containsKey(identifier)) {
            // We're working with it, so it must be safe
            logger.debug("Found a working entity for [{}] in {}. Returning it.", identifier, processingContext);
            return workingEntities.get(identifier);
        }

        CompletableFuture<ManagedEntity<ID, E>> doneMarker = new CompletableFuture<>();
        CompletableFuture<ManagedEntity<ID, E>> previousMarker = inProgress.put(identifier, doneMarker);
        CompletableFuture<ManagedEntity<ID, E>> previousTask;

        doneMarker.whenComplete((r, e) -> inProgress.remove(identifier, doneMarker));
        if (previousMarker == null) {
            logger.debug("No previous task found for loading [{}]. Performing actual load.", identifier);
            previousTask = FutureUtils.emptyCompletedFuture();
        } else {
            logger.debug("Previous task detected. Will wait for it to complete before loading [{}] in {}.",
                         identifier, processingContext);
            previousTask = previousMarker.whenComplete(
                    (r, e) -> logger.debug("Previous task completed. Processing {} in {}.", r, processingContext, e)
            );
        }

        return previousTask.exceptionally(e -> {
            logger.warn("Previous task finished with exception.", e);
            return null;
        }).thenCompose(previousEntity -> {
            if (previousEntity == null) {
                logger.debug(
                        "Previous task for [{}] did not exist or completed with a failure. Loading from delegate in {}.",
                        identifier, processingContext
                );
            } else {
                logger.debug("Previous task finished successfully and transferred entity [{}] to {}.",
                             identifier, processingContext);
            }

            CompletableFuture<ManagedEntity<ID, E>> workingEntity;
            if (previousEntity == null) {
                logger.debug("Calling entity supplier in {} to load or create [{}].", processingContext, identifier);
                workingEntity = entitySupplier.get();
            } else {
                logger.debug("Received [{}] in {}. Registering as managed instance.", identifier, processingContext);
                workingEntity = CompletableFuture.completedFuture(delegate.attach(previousEntity, processingContext));
            }
            workingEntities.put(identifier, workingEntity);

            return workingEntity.whenComplete((r, e) -> {
                logger.debug("Entity [{}] released in {}.", identifier, processingContext);
                processingContext.whenComplete(pc -> {
                    logger.debug("Processing in {} completed successfully. Passing [{}] to next task.",
                                 processingContext, identifier);
                    doneMarker.complete(workingEntities.get(identifier).getNow(null));
                });

                processingContext.onError((pc, phase, error) -> {
                    logger.warn("Processing in {} completed with error. Triggering next task to continue without [{}].",
                                processingContext, identifier);
                    doneMarker.complete(null);
                });
            });
            // We need to apply one more function to avoid cancellations to impact the above logic
        }).thenApply(Function.identity());
    }

    @Override
    public ManagedEntity<ID, E> persist(@Nonnull ID identifier,
                                        @Nonnull E entity,
                                        @Nonnull ProcessingContext processingContext) {
        ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, E>>> workingEntities =
                processingContext.computeResourceIfAbsent(workingEntitiesKey, ConcurrentHashMap::new);
        ManagedEntity<ID, E> persisted = delegate.persist(identifier, entity, processingContext);

        if (workingEntities.put(identifier, CompletableFuture.completedFuture(persisted)) == null) {
            CompletableFuture<ManagedEntity<ID, E>> doneMarker = new CompletableFuture<>();
            inProgress.put(identifier, doneMarker);
            processingContext.whenComplete(pc -> workingEntities.get(identifier).getNow(null));
            processingContext.onError((pc, phase, error) -> doneMarker.complete(null));
        }
        return persisted;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}
