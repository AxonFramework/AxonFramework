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

import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Repository implementation that ensures safe concurrent access to entities stored in it. It delegates that actual
 * loading of entities to another repository, but attempts to pass loaded elements to waiting components immediately, to
 * avoid avoidable load operations on the underlying repository.
 *
 * @param <ID> The type of identifier used to identify entities stored by this repository
 * @param <T>  The type of entity stored in this repository
 */
public class AccessSerializingRepository<ID, T>
        implements AsyncRepository.LifecycleManagement<ID, T>, DescribableComponent {

    private static final Logger logger = LoggerFactory.getLogger(AccessSerializingRepository.class);

    private final ProcessingContext.ResourceKey<ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, T>>>> workingAggregatesKey =
            ProcessingContext.ResourceKey.create("workingAggregates");
    private final AsyncRepository.LifecycleManagement<ID, T> delegate;
    private final ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, T>>> inProgress;

    /**
     * Initialize the repository to serialize access to given {@code delegate}.
     *
     * @param delegate The repository implementation to delegate loading of the entities to
     */
    public AccessSerializingRepository(LifecycleManagement<ID, T> delegate) {
        this.delegate = delegate;
        inProgress = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, T>> load(@Nonnull ID identifier,
                                                        @Nonnull ProcessingContext processingContext) {
        return awaitTurn(identifier,
                         processingContext,
                         () -> delegate.load(identifier, processingContext));
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, T>> loadOrCreate(@Nonnull ID identifier,
                                                                @Nonnull ProcessingContext processingContext,
                                                                @Nonnull Supplier<T> factoryMethod) {
        return awaitTurn(identifier,
                         processingContext,
                         () -> delegate.loadOrCreate(identifier, processingContext, factoryMethod));
    }

    @Override
    public ManagedEntity<ID, T> persist(@Nonnull ID identifier, @Nonnull T entity,
                                        @Nonnull ProcessingContext processingContext) {
        ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, T>>> workingAggregates =
                processingContext.computeResourceIfAbsent(workingAggregatesKey, ConcurrentHashMap::new);
        ManagedEntity<ID, T> persisted = delegate.persist(identifier, entity, processingContext);
        if (workingAggregates.put(identifier, CompletableFuture.completedFuture(persisted)) == null) {
            CompletableFuture<ManagedEntity<ID, T>> doneMarker = new CompletableFuture<>();
            inProgress.put(identifier, doneMarker);
            processingContext.whenComplete(pc -> workingAggregates.get(identifier).getNow(null));
            processingContext.onError((pc, phase, error) -> doneMarker.complete(null));
        }
        return persisted;
    }


    @Override
    public ManagedEntity<ID, T> attach(@Nonnull ManagedEntity<ID, T> entity,
                                       @Nonnull ProcessingContext processingContext) {
        return delegate.attach(entity, processingContext);
    }

    private CompletableFuture<ManagedEntity<ID, T>> awaitTurn(ID aggregateIdentifier,
                                                              ProcessingContext processingContext,
                                                              Supplier<CompletableFuture<ManagedEntity<ID, T>>> action) {
        logger.info("Attempting to load {} in {}", aggregateIdentifier, processingContext);
        ConcurrentMap<ID, CompletableFuture<ManagedEntity<ID, T>>> workingAggregates = processingContext.computeResourceIfAbsent(
                workingAggregatesKey,
                ConcurrentHashMap::new);

        if (workingAggregates.containsKey(aggregateIdentifier)) {
            // we're working with it, so it must be safe
            logger.info("Found a working aggregate for {} in {}. Returning it.",
                        aggregateIdentifier,
                        processingContext);
            return workingAggregates.get(aggregateIdentifier);
        }

        CompletableFuture<ManagedEntity<ID, T>> doneMarker = new CompletableFuture<>();
        CompletableFuture<ManagedEntity<ID, T>> replaced = inProgress.put(aggregateIdentifier, doneMarker);

        doneMarker.whenComplete((r, e) -> inProgress.remove(aggregateIdentifier, doneMarker));
        CompletableFuture<ManagedEntity<ID, T>> previousTask;
        if (replaced == null) {
            logger.info("No previous task found for loading {}. Performing actual load.", aggregateIdentifier);
            previousTask = FutureUtils.emptyCompletedFuture();
        } else {
            logger.info("Previous task detected. Will wait for it to complete before loading {} in {}",
                        aggregateIdentifier,
                        processingContext);
            previousTask = replaced.whenComplete((r, e) -> logger.info("Previous task completed. Processing {} in {}",
                                                                       r,
                                                                       processingContext,
                                                                       e));
        }
        return previousTask.exceptionally(e -> {
            logger.info("Previous task finished with error", e);
            return null;
        }).thenCompose(previousAggregate -> {
            if (previousAggregate == null) {
                logger.info("Previous task for {} did not exist or completed with failure. Loading from delegate in {}.",
                            aggregateIdentifier,
                            processingContext);
            } else {
                logger.info("Previous task finished successfully and transferred entity {} to {}",
                            aggregateIdentifier,
                            processingContext);
            }
            CompletableFuture<ManagedEntity<ID, T>> workingAggregate;
            if (previousAggregate == null) {
                logger.info("Calling action in {} to load {}", processingContext, aggregateIdentifier);
                workingAggregate = action.get();
            } else {
                logger.info("Received {} in {}. Registering as managed instance",
                            processingContext,
                            aggregateIdentifier);
                workingAggregate = CompletableFuture.completedFuture(delegate.attach(previousAggregate,
                                                                                     processingContext));
            }
            workingAggregates.put(aggregateIdentifier, workingAggregate);
            return workingAggregate.whenComplete((r, e) -> {
                logger.info("Aggregate {} released in {}", aggregateIdentifier, processingContext);
                processingContext.whenComplete(pc -> {
                    logger.info("Processing in {} completed successfully. Passing {} to next task",
                                processingContext,
                                aggregateIdentifier);
                    doneMarker.complete(workingAggregates.get(aggregateIdentifier).getNow(null));
                });
                processingContext.onError((pc, phase, error) -> {
                    logger.info("Processing in {} completed with error. Triggering next task to continue without {}",
                                processingContext,
                                aggregateIdentifier);
                    doneMarker.complete(null);
                });
            });
            // we need to apply one more function to avoid cancellations to impact the above logic
        }).thenApply(Function.identity());
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}
