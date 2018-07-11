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

package org.axonframework.commandhandling.disruptor;

import org.axonframework.eventsourcing.EventSourcedAggregate;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works as an in-memory cache to keep a weak reference to the cached object,
 * which then allows the garbage collector to remove an object from memory once it isn't needed
 * anymore.
 * <p>
 * A {@link HashMap} doesn't help here since it will keep hard delegate for key and
 * value objects. A {@link WeakHashMap} doesn't either, because it keeps weak delegate to the
 * key objects, but we want to track the value objects.
 * <p>
 * This implementation which delegates to a {@link Map} uses a {@link WeakReference} for the values. Once the
 * garbage collector decides it wants to finalize a value, it will be removed from the
 * map automatically.
 * <p>
 * This implementation is heavily inspired by http://www.java2s.com/Code/Java/Collections-Data-Structure/WeakValueHashMap.htm
 *
 * @param <T> the type of the aggregate root
 * @author Premanand Chandrasekaran
 * @since 3.3.1
 */
class FirstLevelCache<T> {

    private Map<String, WeakValue> delegate;
    private ReferenceQueue<EventSourcedAggregate<T>> queue;

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
     * Associates the specified value with the specified key in this cache.
     *
     * @param key   the key for this entry
     * @param value the value for this entry
     * @return the previous value associated with this {@code key} or {@code null} if this {@code key} does not exist.
     */
    public EventSourcedAggregate<T> put(String key, EventSourcedAggregate<T> value) {
        processQueue();
        WeakValue valueRef = new WeakValue(key, value, queue);
        return getReferenceValue(delegate.put(key, valueRef));
    }

    /**
     * Get the value associated with a {@code key}.
     *
     * @param key the key for this entry
     * @return the value associated with this {@code key} or {@code null} if this {@code key} does not exist.
     */
    public EventSourcedAggregate<T> get(Object key) {
        processQueue();
        return getReferenceValue(delegate.get(key));
    }

    /**
     * Remove the value associated with a {@code key}.
     *
     * @param key the key for this entry
     * @return the value associated with this {@code key} or {@code null} if this {@code key} does not exist.
     */
    public EventSourcedAggregate<T> remove(Object key) {
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

    /**
     * The current size of this cache.
     *
     * @return the current size
     */
    protected int size() {
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
