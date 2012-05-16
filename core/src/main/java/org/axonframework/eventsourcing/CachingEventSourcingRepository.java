/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import net.sf.jsr107cache.Cache;
import org.axonframework.common.NoCache;
import org.axonframework.repository.LockingStrategy;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;

/**
 * Implementation of the event sourcing repository that uses a cache to improve loading performance. The cache removes
 * the need to read all events from disk, at the cost of memory usage. Since caching is not compatible with the
 * optimistic locking strategy, only pessimistic locking is available for this type of repository.
 * <p/>
 * Note that an entry of a cached aggregate is immediately invalidated when an error occurs while saving that
 * aggregate. This is done to prevent the cache from returning aggregates that may not have fully persisted to disk.
 *
 * @param <T> The type of aggregate this repository stores
 * @author Allard Buijze
 * @since 0.3
 */
public class CachingEventSourcingRepository<T extends EventSourcedAggregateRoot> extends EventSourcingRepository<T> {

    private Cache cache = NoCache.INSTANCE;

    /**
     * Initializes a repository with a the given <code>aggregateFactory</code> and a pessimistic locking strategy.
     * Optimistic locking is not compatible with caching.
     *
     * @param aggregateFactory The factory for new aggregate instances
     * @see org.axonframework.repository.LockingRepository#LockingRepository()
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory) {
        super(aggregateFactory, LockingStrategy.PESSIMISTIC);
    }

    /**
     * Saves the aggregate and stores it in the cache (if configured) for fast retrieval. If an exception occurs while
     * saving the aggregate, the related cache entry is invalidated immediately.
     * <p/>
     * Note that an entry of a cached aggregate is immediately invalidated when an error occurs while saving that
     * aggregate. This is done to prevent the cache from returning aggregates that may not have fully persisted.
     *
     * @param aggregate the aggregate to save
     */
    @Override
    public void doSaveWithLock(final T aggregate) {
        cache.put(aggregate.getIdentifier(), aggregate);
        try {
            super.doSaveWithLock(aggregate);
        } catch (RuntimeException ex) {
            // when an exception occurs, the lock is release and cached state is compromised.
            cache.remove(aggregate.getIdentifier());
            throw ex;
        }
    }

    @Override
    protected void doDeleteWithLock(T aggregate) {
        cache.remove(aggregate.getIdentifier());
        super.doDeleteWithLock(aggregate);
    }

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained. If the aggregate is
     * available
     * in the cache, it is returned from there. Otherwise the underlying persistence logic is called to retrieve the
     * aggregate.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param expectedVersion     The expected version of the aggregate
     * @return the fully initialized aggregate
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public T doLoad(Object aggregateIdentifier, Long expectedVersion) {
        T aggregate = (T) cache.get(aggregateIdentifier);
        if (aggregate == null) {
            aggregate = super.doLoad(aggregateIdentifier, expectedVersion);
        } else if (aggregate.isDeleted()) {
            throw new AggregateDeletedException(aggregateIdentifier);
        }
        CurrentUnitOfWork.get().registerListener(new CacheClearingUnitOfWorkListener(aggregateIdentifier));
        return aggregate;
    }

    /**
     * Set the cache to use for this repository. If a cache is not set, caching is disabled for this implementation.
     *
     * @param cache the cache to use
     */
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    private class CacheClearingUnitOfWorkListener extends UnitOfWorkListenerAdapter {

        private final Object identifier;

        public CacheClearingUnitOfWorkListener(Object identifier) {
            this.identifier = identifier;
        }

        @Override
        public void onRollback(Throwable failureCause) {
            cache.remove(identifier);
        }
    }
}
