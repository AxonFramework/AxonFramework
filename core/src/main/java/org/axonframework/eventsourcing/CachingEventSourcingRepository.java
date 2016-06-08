/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.commandhandling.model.LockAwareAggregate;
import org.axonframework.commandhandling.model.LockingRepository;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.EventSourcedAggregate;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.NoCache;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.io.Serializable;
import java.util.concurrent.Callable;


/**
 * Implementation of the event sourcing repository that uses a cache to improve loading performance. The cache removes
 * the need to read all events from disk, at the cost of memory usage.
 * <p>
 * Note that an entry of a cached aggregate is immediately invalidated when an error occurs while saving that
 * aggregate. This is done to prevent the cache from returning aggregates that may not have fully persisted to disk.
 *
 * @param <T> The type of aggregate this repository stores
 * @author Allard Buijze
 * @since 0.3
 */
public class CachingEventSourcingRepository<T> extends EventSourcingRepository<T> {

    private Cache cache = NoCache.INSTANCE;
    private final EventStore eventStore;

    /**
     * Initializes a repository with a the given <code>aggregateFactory</code> and a pessimistic locking strategy.
     *
     * @param aggregateFactory The factory for new aggregate instances
     * @param eventStore       The event store that holds the event streams for this repository
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore, Cache cache) {
        this(aggregateFactory, eventStore, new PessimisticLockFactory(), cache);
    }

    /**
     * Initializes a repository with a the given <code>aggregateFactory</code> and a pessimistic locking strategy.
     * <p>
     * Note that an optimistic locking strategy is not compatible with caching.
     *
     * @param aggregateFactory The factory for new aggregate instances
     * @param eventStore       The event store that holds the event streams for this repository
     * @param lockFactory      The lock factory restricting concurrent access to aggregate instances
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore,
                                          LockFactory lockFactory, Cache cache) {
        super(aggregateFactory, eventStore, lockFactory);
        this.cache = cache;
        this.eventStore = eventStore;
    }

    @Override
    protected void prepareForCommit(LockAwareAggregate<T, EventSourcedAggregate<T>> aggregate) {
        super.prepareForCommit(aggregate);
        CurrentUnitOfWork.get().onRollback(u -> cache.remove(aggregate.identifier()));
    }

    @Override
    protected EventSourcedAggregate<T> doCreateNewForLock(Callable<T> factoryMethod) throws Exception {
        EventSourcedAggregate<T> aggregate = super.doCreateNewForLock(factoryMethod);
        String aggregateIdentifier = aggregate.identifier();
        // TODO: Add an entry in the cache which is serializable
        cache.put(aggregateIdentifier, new CacheEntry<>(aggregate));
        return aggregate;
    }

    @Override
    protected void doSaveWithLock(EventSourcedAggregate<T> aggregate) {
        super.doSaveWithLock(aggregate);
        cache.put(aggregate.identifier(), new CacheEntry<>(aggregate));
    }

    @Override
    protected void doDeleteWithLock(EventSourcedAggregate<T> aggregate) {
        super.doDeleteWithLock(aggregate);
        cache.put(aggregate.identifier(), new CacheEntry<>(aggregate));
    }

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained. If the aggregate is
     * available in the cache, it is returned from there. Otherwise the underlying persistence logic is called to
     * retrieve the aggregate.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param expectedVersion     The expected version of the aggregate
     * @return the fully initialized aggregate
     */
    @Override
    protected EventSourcedAggregate<T> doLoadWithLock(String aggregateIdentifier, Long expectedVersion) {
        EventSourcedAggregate<T> aggregate = null;
        CacheEntry<T> cacheEntry = cache.get(aggregateIdentifier);
        if (cacheEntry != null) {
            aggregate = cacheEntry.recreateAggregate(aggregateModel(), eventStore);
        }
        if (aggregate == null) {
            aggregate = super.doLoadWithLock(aggregateIdentifier, expectedVersion);
        } else if (aggregate.isDeleted()) {
            throw new AggregateDeletedException(aggregateIdentifier);
        }
        CurrentUnitOfWork.get().onRollback(u -> cache.remove(aggregateIdentifier));
        return aggregate;
    }

    private static class CacheEntry<T>  implements Serializable {

        private final T aggregateRoot;
        private final Long version;
        private final boolean deleted;

        private final transient EventSourcedAggregate<T> aggregate;

        public CacheEntry(EventSourcedAggregate<T> aggregate) {
            this.aggregate = aggregate;
            this.aggregateRoot = aggregate.getAggregateRoot();
            this.version = aggregate.version();
            this.deleted = aggregate.isDeleted();
        }

        public EventSourcedAggregate<T> recreateAggregate(AggregateModel<T> model, EventStore eventStore) {
            if (aggregate != null) {
                return aggregate;
            }
            return EventSourcedAggregate.reconstruct(aggregateRoot, model, version, deleted, eventStore);
        }
    }
}
