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

import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.common.caching.Cache;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Snapshotter trigger mechanism that counts the number of events to decide when to create a snapshot. This
 * implementation acts as a proxy towards the actual event store, and keeps track of the number of "unsnapshotted"
 * events for each aggregate. This means repositories should be configured to use an instance of this class instead of
 * the actual event store.
 * <p/>
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class EventCountSnapshotterTrigger implements SnapshotterTrigger {

    private static final int DEFAULT_TRIGGER_VALUE = 50;
    private final ConcurrentMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private Snapshotter snapshotter;
    private volatile boolean clearCountersAfterAppend = true;
    private int trigger = DEFAULT_TRIGGER_VALUE;

    @Override
    public DomainEventStream decorateForRead(String aggregateIdentifier,
                                             DomainEventStream eventStream) {
        AtomicInteger counter = new AtomicInteger(0);
        counters.put(aggregateIdentifier, counter);
        return new CountingEventStream(eventStream, counter);
    }

    @Override
    public List<DomainEventMessage<?>> decorateForAppend(Aggregate<?> aggregate,
                                                         List<DomainEventMessage<?>> eventStream) {
        String aggregateIdentifier = aggregate.identifier();
        counters.putIfAbsent(aggregateIdentifier, new AtomicInteger(0));
        AtomicInteger counter = counters.get(aggregateIdentifier);
        counter.addAndGet(eventStream.size());
        if (counter.get() > trigger) {
            CurrentUnitOfWork.get().onCleanup(u -> triggerSnapshotIfRequired(aggregate.rootType(),
                                                                             aggregateIdentifier, counter));
        }
        return eventStream;
    }

    private void triggerSnapshotIfRequired(Class<?> aggregateType,
                                           String aggregateIdentifier, final AtomicInteger eventCount) {
        if (eventCount.get() > trigger) {
            snapshotter.scheduleSnapshot(aggregateType, aggregateIdentifier);
            eventCount.set(1);
        }
    }

    /**
     * Sets the snapshotter to notify when a snapshot needs to be taken.
     *
     * @param snapshotter the snapshotter to notify
     */
    public void setSnapshotter(Snapshotter snapshotter) {
        this.snapshotter = snapshotter;
    }

    /**
     * Sets the number of events that will trigger the creation of a snapshot events. Defaults to 50.
     * <p/>
     * This means that a snapshot is created as soon as loading an aggregate would require reading in more than 50
     * events.
     *
     * @param trigger The default trigger value.
     */
    public void setTrigger(int trigger) {
        this.trigger = trigger;
    }

    /**
     * Indicates whether to maintain counters for aggregates after appending events to the event store for these
     * aggregates. Defaults to <code>true</code>.
     * <p/>
     * By setting this value to false, event counters are kept in memory. This is particularly useful when repositories
     * use caches, preventing events from being loaded. Consider registering the Caches use using {@link
     * #setAggregateCache(org.axonframework.common.caching.Cache)} or {@link #setAggregateCaches(java.util.List)}
     *
     * @param clearCountersAfterAppend indicator whether to clear counters after appending events
     */
    public void setClearCountersAfterAppend(boolean clearCountersAfterAppend) {
        this.clearCountersAfterAppend = clearCountersAfterAppend;
    }

    /**
     * Sets the Cache instance used be Caching repositories. By registering them to the snapshotter trigger, it can
     * optimize memory usage by clearing counters held for aggregates that are contained in caches. When an aggregate
     * is
     * evicted or deleted from the cache, its event counter is removed from the trigger.
     * <p/>
     * Use the {@link #setAggregateCaches(java.util.List)} method if you have configured different caches for different
     * repositories.
     * <p/>
     * Using this method will automatically set {@link #setClearCountersAfterAppend(boolean)} to <code>false</code>.
     *
     * @param cache The cache used by caching repositories
     * @see #setAggregateCaches(java.util.List)
     */
    public void setAggregateCache(Cache cache) {
        this.clearCountersAfterAppend = false;
        cache.registerCacheEntryListener(new CacheListener());
    }

    /**
     * Sets the Cache instances used be Caching repositories. By registering them to the snapshotter trigger, it can
     * optimize memory usage by clearing counters held for aggregates that are contained in caches. When an aggregate
     * is
     * evicted or deleted from the cache, its event counter is removed from the trigger.
     *
     * @param caches The caches used by caching repositories
     */
    public void setAggregateCaches(List<Cache> caches) {
        caches.forEach(this::setAggregateCache);
    }

    private class CountingEventStream implements DomainEventStream {

        private final DomainEventStream delegate;
        private final AtomicInteger counter;

        public CountingEventStream(DomainEventStream delegate, AtomicInteger counter) {
            this.delegate = delegate;
            this.counter = counter;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public DomainEventMessage next() {
            DomainEventMessage next = delegate.next();
            counter.incrementAndGet();
            return next;
        }

        @Override
        public DomainEventMessage peek() {
            return delegate.peek();
        }

        /**
         * Returns the counter containing the number of bytes read.
         *
         * @return the counter containing the number of bytes read
         */
        protected AtomicInteger getCounter() {
            return counter;
        }
    }

    private final class CacheListener extends Cache.EntryListenerAdapter {

        @Override
        public void onEntryExpired(Object key) {
            counters.remove(key.toString());
        }

        @Override
        public void onEntryRemoved(Object key) {
            counters.remove(key.toString());
        }
    }
}
