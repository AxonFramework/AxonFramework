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

package org.axonframework.eventsourcing;

import net.sf.jsr107cache.Cache;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.EventStore;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Snapshotter trigger mechanism that counts the number of events to decide when to create a snapshot. This
 * implementation acts as a proxy towards the actual event store, and keeps track of the number of "unsnapshotted"
 * events for each aggregate. This means repositories should be configured to use an instance of this class instead of
 * the actual event store.
 * <p/>
 * The EventCountSnapshotterTrigger delegates all event loading and storage to the configures event store (see {@link
 * #setEventStore(org.axonframework.eventstore.EventStore)}).
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class EventCountSnapshotterTrigger implements EventStore {

    private EventStore delegate;
    private Snapshotter snapshotter;
    private final Map<String, Integer> triggers = new ConcurrentHashMap<String, Integer>();
    private final ConcurrentMap<UUID, AtomicInteger> counters = new ConcurrentHashMap<UUID, AtomicInteger>();
    private volatile boolean clearCountersAfterAppend = true;
    private int defaultTrigger = 50;

    @Override
    public void appendEvents(String type, DomainEventStream events) {
        if (events.hasNext()) {
            UUID aggregateIdentifier = events.peek().getAggregateIdentifier();
            counters.putIfAbsent(aggregateIdentifier, new AtomicInteger(0));
            AtomicInteger counter = counters.get(aggregateIdentifier);
            delegate.appendEvents(type, new CountingEventStream(events, counter));
            triggerSnapshotIfRequired(type, aggregateIdentifier, counter);
            if (clearCountersAfterAppend) {
                counters.remove(aggregateIdentifier, counter);
            }
        } else {
            // this might leave a counter in memory. Will be solved in 0.7 version
            delegate.appendEvents(type, events);
        }
    }

    @Override
    public DomainEventStream readEvents(String type, UUID identifier) {
        AtomicInteger counter = new AtomicInteger(0);
        counters.put(identifier, counter);
        return new CountingEventStream(delegate.readEvents(type, identifier), counter);
    }

    private void triggerSnapshotIfRequired(String type, UUID aggregateIdentifier, AtomicInteger eventCount) {
        Integer trigger = triggers.get(type);
        if (trigger == null) {
            trigger = defaultTrigger;
        }
        if (eventCount.get() > trigger) {
            snapshotter.scheduleSnapshot(type, aggregateIdentifier);
            eventCount.set(1);
        }
    }

    /**
     * Sets the event store providing past events and storing snapshots. This instance of the snapshotter trigger will
     * act as a proxy for the given event store, counting the number of events being loaded and stored.
     *
     * @param eventStore the event store
     */
    public void setEventStore(EventStore eventStore) {
        this.delegate = eventStore;
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
     * IOC container friendly setter to register multiple triggers. The keys of the properties are expected to be
     * strings representing aggregate's type identifiers, and their values an integer value containing the number of
     * events to trigger on.
     *
     * @param eventCounts properties defining the number of events for each aggregate type
     * @see #registerTrigger(String, int)
     * @see #setDefaultTrigger(int)
     */
    public void setTriggerConfiguration(Properties eventCounts) {
        for (Map.Entry<Object, Object> entry : eventCounts.entrySet()) {
            if (entry.getValue() != null) {
                registerTrigger(entry.getKey().toString(), Integer.parseInt(entry.getValue().toString()));
            }
        }
    }

    /**
     * Registers a trigger for the given <code>aggregateType</code> at <code>eventCount</code> events.
     *
     * @param aggregateType The type identifier of the aggregate
     * @param eventCount    The number of events that will trigger snapshot creation
     * @see #setDefaultTrigger(int)
     */
    public void registerTrigger(String aggregateType, int eventCount) {
        triggers.put(aggregateType, eventCount);
    }

    /**
     * Removes the trigger for aggregates of the given <code>aggregateType</code>, reverting to the default trigger.
     *
     * @param aggregateType The type identifier of the aggregate
     * @see #setDefaultTrigger(int)
     */
    public void removeTrigger(String aggregateType) {
        triggers.remove(aggregateType);
    }

    /**
     * Sets the default number of events that will trigger the creation of a snapshot events. Defaults to 50.
     * <p/>
     * This means that a snapshot is created as soon as loading an aggregate would require reading in more than 50
     * events.
     * <p/>
     * Use {@link #registerTrigger(String, int)} or {@link #setTriggerConfiguration(java.util.Properties)} to configure
     * exceptions to this default.
     *
     * @param defaultTrigger The default trigger value.
     */
    public void setDefaultTrigger(int defaultTrigger) {
        this.defaultTrigger = defaultTrigger;
    }

    /**
     * Inidicates whether to maintain counters for aggregates after appending events to the event store for these
     * aggregates. Defaults to <code>true</code>.
     * <p/>
     * By setting this value to false, event counters are kept in memory. This is particularly useful when repositories
     * use caches, preventing events from being loaded. Consider registering the Caches use using {@link
     * #setAggregateCache(net.sf.jsr107cache.Cache)} or {@link #setAggregateCaches(java.util.List)}
     *
     * @param clearCountersAfterAppend indicator whether to clear counters after appending events
     */
    public void setClearCountersAfterAppend(boolean clearCountersAfterAppend) {
        this.clearCountersAfterAppend = clearCountersAfterAppend;
    }

    /**
     * Sets the Cache instance used be Caching repositories. By registering them to the snapshotter trigger, it can
     * optimize memory usage by clearing counters held for aggregates that are contained in caches. When an aggregate is
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
        cache.addListener(new CacheListener());
    }

    /**
     * Sets the Cache instances used be Caching repositories. By registering them to the snapshotter trigger, it can
     * optimize memory usage by clearing counters held for aggregates that are contained in caches. When an aggregate is
     * evicted or deleted from the cache, its event counter is removed from the trigger.
     *
     * @param caches The caches used by caching repositories
     */
    public void setAggregateCaches(List<Cache> caches) {
        for (Cache cache : caches) {
            setAggregateCache(cache);
        }
    }

    private static final class CountingEventStream implements DomainEventStream {

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
        public DomainEvent next() {
            DomainEvent next = delegate.next();
            counter.incrementAndGet();
            return next;
        }

        @Override
        public DomainEvent peek() {
            return delegate.peek();
        }
    }

    private class CacheListener implements net.sf.jsr107cache.CacheListener {

        @Override
        public void onLoad(Object key) {
        }

        @Override
        public void onPut(Object key) {
        }

        @SuppressWarnings({"SuspiciousMethodCalls"})
        @Override
        public void onEvict(Object key) {
            counters.remove(key);
        }

        @SuppressWarnings({"SuspiciousMethodCalls"})
        @Override
        public void onRemove(Object key) {
            counters.remove(key);
        }

        @Override
        public void onClear() {
            counters.clear();
        }
    }
}
