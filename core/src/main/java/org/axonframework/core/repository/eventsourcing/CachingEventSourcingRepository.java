/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.repository.eventsourcing;

import net.sf.jsr107cache.Cache;
import org.axonframework.core.AggregateDeletedEvent;
import org.axonframework.core.EventSourcedAggregateRoot;
import org.axonframework.core.repository.LockingStrategy;

import java.util.UUID;

/**
 * Implementation of the event sourcing repository that uses a cache to improve loading performance. The cache removes
 * the need to read all events from disk, at the cost of memory usage. Since caching is not compatible with the
 * optimistic locking strategy, only pessimistic locking is available for this type of repository.
 * <p/>
 * Note that an entry of a cached aggregate is immediately invalidated when an error occurs while saving that aggregate.
 * This is done to prevent the cache from returning aggregates that may not have fully persisted to disk.
 *
 * @author Allard Buijze
 * @param <T> The type of aggregate this repository stores
 * @since 0.3
 */
public abstract class CachingEventSourcingRepository<T extends EventSourcedAggregateRoot>
        extends EventSourcingRepository<T> {

    private static final NoCache DEFAULT_CACHE = new NoCache();

    private Cache cache = DEFAULT_CACHE;

    /**
     * Initializes a repository with a pessimistic locking strategy. Optimistic locking is not compatible with caching.
     */
    protected CachingEventSourcingRepository() {
        super(LockingStrategy.PESSIMISTIC);
    }

    /**
     * Saves the aggregate and stores is in the cache for fast retrieval. If an exception occurs while saving the
     * aggregate, the related cache entry is invalidated immediately.
     * <p/>
     * Note that an entry of a cached aggregate is immediately invalidated when an error occurs while saving that
     * aggregate. This is done to prevent the cache from returning aggregates that may not have fully persisted to
     * disk.
     *
     * @param aggregate the aggregate to save
     */
    @Override
    public void doSave(T aggregate) {
        cache.put(aggregate.getIdentifier(), aggregate);
        try {
            super.doSave(aggregate);
        }
        catch (RuntimeException ex) {
            // when an exception occurs, the lock is release and cached state is compromised.
            cache.remove(aggregate.getIdentifier());
            throw ex;
        }
    }

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained. If the aggregate is available
     * in the cache, it is returned from there. Otherwise the underlying persistence logic is called to retrieve the
     * aggregate.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @return the fully initialized aggregate
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public T doLoad(UUID aggregateIdentifier) {
        T existingAggregate = (T) cache.get(aggregateIdentifier);
        if (existingAggregate == null) {
            return super.doLoad(aggregateIdentifier);
        }
        return existingAggregate;
    }

    /**
     * Perform the actual deleting of an aggregate. If present, the aggregate will be removed from the cache.
     *
     * @param aggregateIdentifier the identifier of the aggregate to delete
     * @return the event to publish, if any.
     */
    @Override
    protected AggregateDeletedEvent doDelete(UUID aggregateIdentifier) {
        AggregateDeletedEvent event = super.doDelete(aggregateIdentifier);
        cache.remove(aggregateIdentifier);
        return event;
    }

    /**
     * Set the cache to use for this repository. If a cache is not set, caching is disabled for this implementation.
     *
     * @param cache the cache to use
     */
    public void setCache(Cache cache) {
        this.cache = cache;
    }

}
