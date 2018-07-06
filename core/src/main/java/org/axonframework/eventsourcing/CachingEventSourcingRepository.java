/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.LockingRepository;
import org.axonframework.commandhandling.model.RepositoryProvider;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

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

    private final EventStore eventStore;
    private final RepositoryProvider repositoryProvider;
    private final Cache cache;
    private final SnapshotTriggerDefinition snapshotTriggerDefinition;

    /**
     * Initializes a repository with a the given {@code aggregateFactory} and a pessimistic locking strategy. It stores
     * Aggregates in the given {@code cache}.
     * <p>
     * A repository initialized using this constructor does not create snapshots for aggregates.
     *
     * @param aggregateFactory   The factory for new aggregate instances
     * @param eventStore         The event store that holds the event streams for this repository
     * @param cache              The cache in which entries will be stored
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore, Cache cache) {
        this(aggregateFactory,
             eventStore,
             new PessimisticLockFactory(),
             cache,
             NoSnapshotTriggerDefinition.INSTANCE);
    }

    /**
     * Initializes a repository with a the given {@code aggregateFactory} and a pessimistic locking strategy. It stores
     * Aggregates in the given {@code cache}.
     * <p>
     * A repository initialized using this constructor does not create snapshots for aggregates.
     *
     * @param aggregateFactory   The factory for new aggregate instances
     * @param eventStore         The event store that holds the event streams for this repository
     * @param cache              The cache in which entries will be stored
     * @param repositoryProvider Provides repositories for specific aggregate types
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore, Cache cache,
                                          RepositoryProvider repositoryProvider) {
        this(aggregateFactory,
             eventStore,
             new PessimisticLockFactory(),
             cache,
             NoSnapshotTriggerDefinition.INSTANCE,
             repositoryProvider);
    }

    /**
     * Initializes a repository with a the given {@code aggregateFactory} and a pessimistic locking strategy.
     *
     * @param aggregateFactory          The factory for new aggregate instances
     * @param eventStore                The event store that holds the event streams for this repository
     * @param cache                     The cache in which entries will be stored
     * @param snapshotTriggerDefinition The definition describing when to trigger a snapshot
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore, Cache cache,
                                          SnapshotTriggerDefinition snapshotTriggerDefinition) {
        this(aggregateFactory,
             eventStore,
             new PessimisticLockFactory(),
             cache,
             snapshotTriggerDefinition);
    }

    /**
     * Initializes a repository with a the given {@code aggregateFactory} and a pessimistic locking strategy.
     *
     * @param aggregateFactory          The factory for new aggregate instances
     * @param eventStore                The event store that holds the event streams for this repository
     * @param cache                     The cache in which entries will be stored
     * @param snapshotTriggerDefinition The definition describing when to trigger a snapshot
     * @param repositoryProvider        Provides repositories for specific aggregate types
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore, Cache cache,
                                          SnapshotTriggerDefinition snapshotTriggerDefinition,
                                          RepositoryProvider repositoryProvider) {
        this(aggregateFactory,
             eventStore,
             new PessimisticLockFactory(),
             cache,
             snapshotTriggerDefinition,
             repositoryProvider);
    }

    /**
     * Initializes a repository with a the given {@code aggregateFactory} and a pessimistic locking strategy.
     * <p>
     * Note that an optimistic locking strategy is not compatible with caching.
     *
     * @param aggregateFactory          The factory for new aggregate instances
     * @param eventStore                The event store that holds the event streams for this repository
     * @param lockFactory               The lock factory restricting concurrent access to aggregate instances
     * @param cache                     The cache in which entries will be stored
     * @param snapshotTriggerDefinition The definition describing when to trigger a snapshot
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore,
                                          LockFactory lockFactory, Cache cache,
                                          SnapshotTriggerDefinition snapshotTriggerDefinition) {
        this(aggregateFactory, eventStore, lockFactory, cache, snapshotTriggerDefinition, null);
    }

    /**
     * Initializes a repository with a the given {@code aggregateFactory} and a pessimistic locking strategy.
     * <p>
     * Note that an optimistic locking strategy is not compatible with caching.
     *
     * @param aggregateFactory          The factory for new aggregate instances
     * @param eventStore                The event store that holds the event streams for this repository
     * @param lockFactory               The lock factory restricting concurrent access to aggregate instances
     * @param cache                     The cache in which entries will be stored
     * @param snapshotTriggerDefinition The definition describing when to trigger a snapshot
     * @param repositoryProvider        Provides repositories for specific aggregate types
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore,
                                          LockFactory lockFactory, Cache cache,
                                          SnapshotTriggerDefinition snapshotTriggerDefinition,
                                          RepositoryProvider repositoryProvider) {
        super(aggregateFactory, eventStore, lockFactory, snapshotTriggerDefinition, repositoryProvider);
        this.cache = cache;
        this.eventStore = eventStore;
        this.snapshotTriggerDefinition = snapshotTriggerDefinition;
        this.repositoryProvider = repositoryProvider;
    }

    /**
     * Initializes a repository with a the given {@code aggregateFactory} and a pessimistic locking strategy.
     * <p>
     * Note that an optimistic locking strategy is not compatible with caching.
     *
     * @param aggregateFactory          The factory for new aggregate instances
     * @param eventStore                The event store that holds the event streams for this repository
     * @param lockFactory               The lock factory restricting concurrent access to aggregate instances
     * @param cache                     The cache in which entries will be stored
     * @param parameterResolverFactory  The parameter resolver factory used to resolve parameters of annotated handlers
     * @param snapshotTriggerDefinition The definition describing when to trigger a snapshot
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore,
                                          LockFactory lockFactory, Cache cache,
                                          ParameterResolverFactory parameterResolverFactory,
                                          SnapshotTriggerDefinition snapshotTriggerDefinition) {
        this(aggregateFactory,
             eventStore,
             lockFactory,
             cache,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(aggregateFactory.getAggregateType()),
             snapshotTriggerDefinition,
             null);
    }

    /**
     * Initializes a repository with a the given {@code aggregateFactory} and a pessimistic locking strategy.
     * <p>
     * Note that an optimistic locking strategy is not compatible with caching.
     *
     * @param aggregateFactory          The factory for new aggregate instances
     * @param eventStore                The event store that holds the event streams for this repository
     * @param lockFactory               The lock factory restricting concurrent access to aggregate instances
     * @param cache                     The cache in which entries will be stored
     * @param parameterResolverFactory  The parameter resolver factory used to resolve parameters of annotated handlers
     * @param handlerDefinition         The handler definition used to create concrete handlers
     * @param snapshotTriggerDefinition The definition describing when to trigger a snapshot
     * @param repositoryProvider        Provides repositories for specific aggregate types
     * @see LockingRepository#LockingRepository(Class)
     */
    public CachingEventSourcingRepository(AggregateFactory<T> aggregateFactory, EventStore eventStore,
                                          LockFactory lockFactory, Cache cache,
                                          ParameterResolverFactory parameterResolverFactory,
                                          HandlerDefinition handlerDefinition,
                                          SnapshotTriggerDefinition snapshotTriggerDefinition,
                                          RepositoryProvider repositoryProvider) {
        super(aggregateFactory,
              eventStore,
              lockFactory,
              parameterResolverFactory,
              handlerDefinition,
              snapshotTriggerDefinition,
              repositoryProvider);
        this.cache = cache;
        this.eventStore = eventStore;
        this.snapshotTriggerDefinition = snapshotTriggerDefinition;
        this.repositoryProvider = repositoryProvider;
    }

    @Override
    protected void validateOnLoad(Aggregate<T> aggregate, Long expectedVersion) {
        CurrentUnitOfWork.get().onRollback(u -> cache.remove(aggregate.identifierAsString()));
        super.validateOnLoad(aggregate, expectedVersion);
    }

    @Override
    protected EventSourcedAggregate<T> doCreateNewForLock(Callable<T> factoryMethod) throws Exception {
        EventSourcedAggregate<T> aggregate = super.doCreateNewForLock(factoryMethod);
        CurrentUnitOfWork.get().onRollback(u -> cache.remove(aggregate.identifierAsString()));
        cache.put(aggregate.identifierAsString(), new AggregateCacheEntry<>(aggregate));
        return aggregate;
    }

    @Override
    protected void doSaveWithLock(EventSourcedAggregate<T> aggregate) {
        super.doSaveWithLock(aggregate);
        cache.put(aggregate.identifierAsString(), new AggregateCacheEntry<>(aggregate));
    }

    @Override
    protected void doDeleteWithLock(EventSourcedAggregate<T> aggregate) {
        super.doDeleteWithLock(aggregate);
        cache.put(aggregate.identifierAsString(), new AggregateCacheEntry<>(aggregate));
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
        AggregateCacheEntry<T> cacheEntry = cache.get(aggregateIdentifier);
        if (cacheEntry != null) {
            aggregate = cacheEntry.recreateAggregate(aggregateModel(),
                                                     eventStore,
                                                     repositoryProvider,
                                                     snapshotTriggerDefinition);
        }
        if (aggregate == null) {
            aggregate = super.doLoadWithLock(aggregateIdentifier, expectedVersion);
        } else if (aggregate.isDeleted()) {
            throw new AggregateDeletedException(aggregateIdentifier);
        }
        return aggregate;
    }

}
