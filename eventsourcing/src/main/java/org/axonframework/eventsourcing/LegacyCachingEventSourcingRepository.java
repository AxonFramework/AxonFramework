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

import org.axonframework.common.caching.Cache;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.eventsourcing.eventstore.LegacyEventStore;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.inspection.AggregateModel;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the event sourcing repository that uses a cache to improve loading performance. The cache removes
 * the need to read all events from disk, at the cost of memory usage.
 * <p>
 * Note that an entry of a cached aggregate is immediately invalidated when an error occurs while saving that aggregate.
 * This is done to prevent the cache from returning aggregates that may not have fully persisted to disk.
 *
 * @param <T> The type of aggregate this repository stores
 * @author Allard Buijze
 * @since 0.3
 * @deprecated In favor of an instance to be constructed still.
 */
@Deprecated(since = "5.0.0", forRemoval = true)
public class LegacyCachingEventSourcingRepository<T> extends LegacyEventSourcingRepository<T> {

    private final LegacyEventStore eventStore;
    private final RepositoryProvider repositoryProvider;
    private final Cache cache;
    private final SnapshotTriggerDefinition snapshotTriggerDefinition;

    /**
     * Instantiate a {@code LegacyCachingEventSourcingRepository} based on the fields contained in the
     * {@link LegacyEventSourcingRepository.Builder}.
     * <p>
     * A goal of the provided Builder is to create an {@link AggregateModel} specifying generic {@code T} as the
     * aggregate type to be stored. All aggregates in this repository must be {@code instanceOf} this aggregate type. To
     * instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an AggregateModel.
     * Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided. An
     * {@link org.axonframework.common.AxonConfigurationException} is thrown if this criteria is not met. The same
     * criteria holds for the {@link AggregateFactory}. Either the AggregateFactory can be set directly or it will be
     * instantiated internally based on the {@code aggregateType}. Hence, one of both is a hard requirement, and will
     * also result in an AxonConfigurationException if both are missing.
     * <p>
     * Additionally will assert that the {@link LockFactory}, {@link LegacyEventStore},
     * {@link SnapshotTriggerDefinition} and {@link Cache} are not {@code null}, resulting in an
     * AxonConfigurationException if for any of these this is the case.
     *
     * @param builder the {@link LegacyEventSourcingRepository.Builder} used to instantiate a
     *                {@code LegacyCachingEventSourcingRepository} instance
     */
    protected LegacyCachingEventSourcingRepository(Builder<T> builder) {
        super(builder);
        assertNonNull(builder.cache, "The Cache is a hard requirement and should be provided");
        this.cache = builder.cache;
        this.eventStore = builder.eventStore;
        this.snapshotTriggerDefinition = builder.snapshotTriggerDefinition;
        this.repositoryProvider = builder.repositoryProvider;
    }

    @Override
    protected void validateOnLoad(Aggregate<T> aggregate) {
        CurrentUnitOfWork.get().onRollback(u -> cache.remove(aggregate.identifierAsString()));
        super.validateOnLoad(aggregate);
    }

    @Override
    protected void doSaveWithLock(EventSourcedAggregate<T> aggregate) {
        super.doSaveWithLock(aggregate);
        String key = aggregate.identifierAsString();
        CurrentUnitOfWork.get().onRollback(u -> cache.remove(aggregate.identifierAsString()));
        cache.put(key, new AggregateCacheEntry<>(aggregate));
    }

    @Override
    protected void doDeleteWithLock(EventSourcedAggregate<T> aggregate) {
        super.doDeleteWithLock(aggregate);
        String key = aggregate.identifierAsString();
        CurrentUnitOfWork.get().onRollback(u -> cache.remove(aggregate.identifierAsString()));
        cache.put(key, new AggregateCacheEntry<>(aggregate));
    }

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained. If the aggregate is available
     * in the cache, it is returned from there. Otherwise, the underlying persistence logic is called to retrieve the
     * aggregate.
     *
     * @param aggregateIdentifier The identifier of the aggregate to load.
     * @return The fully initialized aggregate.
     */
    @Override
    protected EventSourcedAggregate<T> doLoadWithLock(String aggregateIdentifier) {
        EventSourcedAggregate<T> aggregate = null;
        AggregateCacheEntry<T> cacheEntry = cache.get(aggregateIdentifier);
        if (cacheEntry != null) {
            CurrentUnitOfWork.get().onRollback(u -> cache.remove(aggregateIdentifier));
            aggregate = cacheEntry.recreateAggregate(aggregateModel(),
                                                     eventStore,
                                                     repositoryProvider,
                                                     snapshotTriggerDefinition);
        }
        if (aggregate == null) {
            aggregate = super.doLoadWithLock(aggregateIdentifier);
        } else if (aggregate.isDeleted()) {
            throw new AggregateDeletedException(aggregateIdentifier);
        }
        return aggregate;
    }
}
