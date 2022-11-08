/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.common.caching;

import org.axonframework.common.Registration;

import java.util.function.UnaryOperator;

/**
 * Abstraction for a Caching mechanism. All Axon component rely on this abstraction, so that different providers can be
 * plugged in. In future versions, this abstraction may be replaced with the {@code javax.cache} api, as soon as that
 * api is final.
 *
 * @author Allard Buijze
 * @since 2.1.2
 */
public interface Cache {

    /**
     * Returns an item from the cache, or {@code null} if no item was stored under that key
     *
     * @param key The key under which the item was cached
     * @param <K> The type of key used
     * @param <V> The type of value stored
     * @return the item stored under the given key
     */
    <K, V> V get(K key);

    /**
     * Stores the given {@code value} in the cache, under given {@code key}. If an item already exists, it is updated
     * with the new value.
     *
     * @param key   The key under which to store the item
     * @param value The item to cache
     */
    void put(Object key, Object value);

    /**
     * Stores the given {@code value} in the cache, under given {@code key}, if no element is yet available under that
     * key. This operation is performed atomically.
     *
     * @param key   The key under which to store the item
     * @param value The item to cache
     * @return {@code true} if no value was previously assigned to the key, {@code false} otherwise.
     */
    boolean putIfAbsent(Object key, Object value);

    /**
     * Removes the entry stored under given {@code key}. If no such entry exists, nothing happens.
     *
     * @param key The key under which the item was stored
     * @return {@code true} if a value was previously assigned to the key and has been removed, {@code false} otherwise.
     */
    boolean remove(Object key);

    /**
     * Remove all stored entries in this cache.
     */
    default void removeAll() {
        throw new UnsupportedOperationException("Cache#removeAll is currently unsupported by this version");
    }

    /**
     * Indicates whether there is an item stored under given {@code key}.
     *
     * @param key The key to check
     * @return {@code true} if an item is available under that key, {@code false} otherwise.
     */
    boolean containsKey(Object key);

    /**
     * Registers the given {@code cacheEntryListener} to listen for Cache changes.
     *
     * @param cacheEntryListener The listener to register
     * @return a handle to deregister the listener
     */
    Registration registerCacheEntryListener(EntryListener cacheEntryListener);

    /**
     * Perform the {@code update} in the value behind the given {@code key}. The {@code update} is only executed if
     * there's an entry referencing the {@code key}.
     *
     * @param key    The key to perform an update for, if not empty.
     * @param update The update to perform if the {@code key} is present.
     * @param <V>    The type of the value to execute the {@code update} for.
     */
    default <V> void computeIfPresent(Object key, UnaryOperator<V> update) {
        throw new UnsupportedOperationException("Cache#computeIfPresent is currently unsupported by this version");
    }

    /**
     * Interface describing callback methods, which are invoked when changes are made in the underlying cache.
     */
    interface EntryListener {

        /**
         * Invoked when an entry has expired.
         *
         * @param key The key of the entry that expired
         */
        void onEntryExpired(Object key);

        /**
         * Invoked when an item was removed from the cache, either following an expiry, or by explicitly calling {@link
         * org.axonframework.common.caching.Cache#remove(Object)}.
         *
         * @param key The key of the entry that was removed
         */
        void onEntryRemoved(Object key);

        /**
         * Invoked when an item has been updated.
         *
         * @param key   The key of the entry that was updated
         * @param value The new value of the entry
         */
        void onEntryUpdated(Object key, Object value);

        /**
         * Invoked when a new item has been added to the cache
         *
         * @param key   The key of the entry that was added
         * @param value The value of the entry
         */
        void onEntryCreated(Object key, Object value);

        /**
         * Invoked when an item was retrieved from the Cache
         *
         * @param key   The key of the entry that was read
         * @param value The value of the entry read
         */
        void onEntryRead(Object key, Object value);

        /**
         * Clone operation used by some Cache implementations. An implementation must implement {@link
         * java.lang.Cloneable} to indicate it supports cloning.
         *
         * @return a copy of this instance
         * @throws CloneNotSupportedException if cloning is not supported
         * @see java.lang.Cloneable
         */
        Object clone() throws CloneNotSupportedException;
    }

    /**
     * Adapter implementation for the EntryListener, allowing for overriding only specific callback methods.
     */
    class EntryListenerAdapter implements EntryListener {

        @Override
        public void onEntryExpired(Object key) {
        }

        @Override
        public void onEntryRemoved(Object key) {
        }

        @Override
        public void onEntryUpdated(Object key, Object value) {
        }

        @Override
        public void onEntryCreated(Object key, Object value) {
        }

        @Override
        public void onEntryRead(Object key, Object value) {
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }
}
