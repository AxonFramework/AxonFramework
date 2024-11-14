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

package org.axonframework.modelling.repository;

import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Repository implementation that ensures safe concurrent access to entities stored in it. It delegates the actual
 * loading of entities to another {@link AsyncRepository}, but attempts to pass loaded elements to waiting components
 * immediately, to avoid avoidable load operations on the underlying repository.
 *
 * @param <ID> The type of identifier used to identify entities stored by this repository.
 * @param <T>  The type of entity stored in this repository.
 * @author Allard Buijze
 * @since 5.0.0
 */
public class AccessSerializingRepository<ID, T>
        implements AsyncRepository.LifecycleManagement<ID, T>, DescribableComponent {

    private static final Logger logger = LoggerFactory.getLogger(AccessSerializingRepository.class);

    private final ResourceKey<ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, T>>>> workingEntitiesKey =
            ResourceKey.sharedKey("workingEntities");

    private final AsyncRepository.LifecycleManagement<ID, T> delegate;
    private final ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, T>>> inProgress;

    /**
     * Initialize the repository to serialize access to given {@code delegate}.
     *
     * @param delegate The repository implementation to delegate loading of the entities to
     */
    public AccessSerializingRepository(AsyncRepository.LifecycleManagement<ID, T> delegate) {
        this.delegate = delegate;
        this.inProgress = new ConcurrentHashMap<>();
    }

    @Override
    public ManagedEntity<ID, T> attach(@Nonnull ManagedEntity<ID, T> entity,
                                       @Nonnull ProcessingContext processingContext) {
        return delegate.attach(entity, processingContext);
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, T>> load(@Nonnull ID identifier,
                                                        @Nonnull ProcessingContext processingContext,
                                                        long start,
                                                        long end) {
        return awaitTurn(identifier,
                         processingContext,
                         () -> delegate.load(identifier, processingContext, start, end));
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, T>> loadOrCreate(@Nonnull ID identifier,
                                                                @Nonnull ProcessingContext processingContext,
                                                                @Nonnull Supplier<T> factoryMethod) {
        return awaitTurn(identifier,
                         processingContext,
                         () -> delegate.loadOrCreate(identifier, processingContext, factoryMethod));
    }

    private CompletableFuture<ManagedEntity<ID, T>> awaitTurn(
            ID identifier,
            ProcessingContext processingContext,
            Supplier<CompletableFuture<ManagedEntity<ID, T>>> entitySupplier
    ) {
        logger.info("Attempting to load [{}] in {}", identifier, processingContext);
        ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, T>>> workingEntities =
                processingContext.computeResourceIfAbsent(workingEntitiesKey, ConcurrentHashMap::new);

        if (workingEntities.containsKey(identifier)) {
            // We're working with it, so it must be safe
            logger.info("Found a working entity for [{}] in {}. Returning it.", identifier, processingContext);
            return workingEntities.get(identifier);
        }

        CompletableFuture<ManagedEntity<ID, T>> doneMarker = new CompletableFuture<>();
        CompletableFuture<ManagedEntity<ID, T>> previousMarker = inProgress.put(identifier, doneMarker);
        CompletableFuture<ManagedEntity<ID, T>> previousTask;

        doneMarker.whenComplete((r, e) -> inProgress.remove(identifier, doneMarker));
        if (previousMarker == null) {
            logger.info("No previous task found for loading [{}]. Performing actual load.", identifier);
            previousTask = FutureUtils.emptyCompletedFuture();
        } else {
            logger.info("Previous task detected. Will wait for it to complete before loading [{}] in {}",
                        identifier, processingContext);
            previousTask = previousMarker.whenComplete(
                    (r, e) -> logger.info("Previous task completed. Processing {} in {}", r, processingContext, e)
            );
        }

        return previousTask.exceptionally(e -> {
            logger.info("Previous task finished with exception", e);
            return null;
        }).thenCompose(previousEntity -> {
            if (previousEntity == null) {
                logger.info(
                        "Previous task for [{}] did not exist or completed with a failure. Loading from delegate in {}.",
                        identifier, processingContext
                );
            } else {
                logger.info("Previous task finished successfully and transferred entity [{}] to {}.",
                            identifier, processingContext);
            }

            CompletableFuture<ManagedEntity<ID, T>> workingEntity;
            if (previousEntity == null) {
                logger.info("Calling entity supplier in {} to load or create [{}].", processingContext, identifier);
                workingEntity = entitySupplier.get();
            } else {
                logger.info("Received [{}] in {}. Registering as managed instance.", identifier, processingContext);
                workingEntity = CompletableFuture.completedFuture(delegate.attach(previousEntity, processingContext));
            }
            workingEntities.put(identifier, workingEntity);

            return workingEntity.whenComplete((r, e) -> {
                logger.info("Entity [{}] released in {}", identifier, processingContext);
                processingContext.whenComplete(pc -> {
                    logger.info("Processing in {} completed successfully. Passing [{}] to next task.",
                                processingContext, identifier);
                    doneMarker.complete(workingEntities.get(identifier).getNow(null));
                });

                processingContext.onError((pc, phase, error) -> {
                    logger.info("Processing in {} completed with error. Triggering next task to continue without [{}].",
                                processingContext, identifier);
                    doneMarker.complete(null);
                });
            });
            // We need to apply one more function to avoid cancellations to impact the above logic
        }).thenApply(Function.identity());
    }

    @Override
    public ManagedEntity<ID, T> persist(@Nonnull ID identifier,
                                        @Nonnull T entity,
                                        @Nonnull ProcessingContext processingContext) {
        ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, T>>> workingEntities =
                processingContext.computeResourceIfAbsent(workingEntitiesKey, ConcurrentHashMap::new);
        ManagedEntity<ID, T> persisted = delegate.persist(identifier, entity, processingContext);

        if (workingEntities.put(identifier, CompletableFuture.completedFuture(persisted)) == null) {
            CompletableFuture<ManagedEntity<ID, T>> doneMarker = new CompletableFuture<>();
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
