/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.disruptor.commandhandling;

import org.axonframework.eventsourcing.EventSourcedAggregate;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works as an in-memory cache to keep a weak reference to the cached object, which then allows the garbage collector to
 * remove an object from memory once it isn't needed anymore.
 * <p>
 * A {@link HashMap} doesn't help here since it will keep hard delegate for key and value objects. A {@link WeakHashMap}
 * doesn't either, because it keeps weak delegate to the key objects, but we want to track the value objects.
 * <p>
 * This implementation which delegates to a {@link Map} uses a {@link WeakReference} for the values. Once the garbage
 * collector decides it wants to finalize a value, it will be removed from the map automatically.
 * <p>
 * This implementation is heavily inspired by http://www.java2s.com/Code/Java/Collections-Data-Structure/WeakValueHashMap.htm
 *
 * @param <T> the type of the aggregate root
 * @author Premanand Chandrasekaran
 * @since 3.3.1
 */
class FirstLevelCache<T> {

    private final Map<String, WeakValue> delegate;
    private final ReferenceQueue<EventSourcedAggregate<T>> queue;

    /**
     * Creates a FirstLevelCache with a desired initial capacity.
     *
     * @param capacity - the initial capacity
     */
    private FirstLevelCache(int capacity) {
        delegate = new ConcurrentHashMap<>(capacity);
        queue = new ReferenceQueue<>();
    }

    /**
     * Creates a FirstLevelCache with an initial capacity of 1.
     */
    FirstLevelCache() {
        this(1);
    }

    /**
     * Puts the given {@code value} in the cache under given {@code key}
     *
     * @param key   The key to store the entry under
     * @param value The value to store in the cache
     * @return the previous value associated with this key, or {@code null} if it didn't exist
     */
    public EventSourcedAggregate<T> put(String key, EventSourcedAggregate<T> value) {
        processQueue();
        WeakValue valueRef = new WeakValue(key, value, queue);
        return getReferenceValue(delegate.put(key, valueRef));
    }

    /**
     * Returns the entry stored under the given {@code key}, or {@code null} if it doesn't exist.
     *
     * @param key The key to find the entry for
     * @return the entry previously stored, or {@code null} if no entry exists or when it has been garbage collected
     */
    public EventSourcedAggregate<T> get(Object key) {
        processQueue();
        //noinspection SuspiciousMethodCalls
        return getReferenceValue(delegate.get(key));
    }

    /**
     * Remove an entry under given {@code key}, if it exists
     *
     * @param key The key of the entry to remove
     * @return the entry stored, or {@code null} if no entry was known for this key
     */
    public EventSourcedAggregate<T> remove(Object key) {
        //noinspection SuspiciousMethodCalls
        return getReferenceValue(delegate.remove(key));
    }

    private EventSourcedAggregate<T> getReferenceValue(WeakValue valueRef) {
        return valueRef == null ? null : valueRef.get();
    }

    @SuppressWarnings("unchecked")
    private void processQueue() {
        WeakValue valueRef;
        while ((valueRef = (WeakValue) queue.poll()) != null) {
            delegate.remove(valueRef.getKey());
        }
    }

    public int size() {
        processQueue();
        return delegate.size();
    }

    private class WeakValue extends WeakReference<EventSourcedAggregate<T>> {

        private final String key;

        private WeakValue(String key, EventSourcedAggregate<T> value, ReferenceQueue<EventSourcedAggregate<T>> queue) {
            super(value, queue);
            this.key = key;
        }

        private String getKey() {
            return key;
        }
    }
}
