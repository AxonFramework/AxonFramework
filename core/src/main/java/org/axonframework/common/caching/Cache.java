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

package org.axonframework.common.caching;

import org.axonframework.common.Registration;

/**
 * Abstraction for a Caching mechanism. All Axon component rely on this abstraction, so that different
 * providers can be plugged in. In future versions, this abstraction may be replaced with the <code>javax.cache</code>
 * api, as soon as that api is final.
 *
 * @author Allard Buijze
 * @since 2.1.2
 */
public interface Cache {

    /**
     * Returns an item from the cache, or <code>null</code> if no item was stored under that key
     *
     * @param key The key under which the item was cached
     * @param <K> The type of key used
     * @param <V> The type of value stored
     * @return the item stored under the given key
     */
    <K, V> V get(K key);

    /**
     * Stores the given <code>value</code> in the cache, under given <code>key</code>. If an item already exists,
     * it is updated with the new value.
     *
     * @param key   The key under which to store the item
     * @param value The item to cache
     * @param <K>   The type of key used
     * @param <V>   The type of value stored
     */
    <K, V> void put(K key, V value);

    /**
     * Stores the given <code>value</code> in the cache, under given <code>key</code>, if no element is yet available
     * under that key. This operation is performed atomically.
     *
     * @param key   The key under which to store the item
     * @param value The item to cache
     * @param <K>   The type of key used
     * @param <V>   The type of value stored
     * @return <code>true</code> if no value was previously assigned to the key, <code>false</code> otherwise.
     */
    <K, V> boolean putIfAbsent(K key, V value);

    /**
     * Removes the entry stored under given <code>key</code>. If no such entry exists, nothing happens.
     *
     * @param key The key under which the item was stored
     * @param <K> The type of key used
     * @return <code>true</code> if a value was previously assigned to the key and has been removed, <code>false</code>
     * otherwise.
     */
    <K> boolean remove(K key);

    /**
     * Indicates whether there is an item stored under given <code>key</code>.
     *
     * @param key The key to check
     * @param <K> The type of key
     * @return <code>true</code> if an item is available under that key, <code>false</code> otherwise.
     */
    <K> boolean containsKey(K key);

    /**
     * Registers the given <code>cacheEntryListener</code> to listen for Cache changes.
     *
     * @param cacheEntryListener The listener to register
     * @return a handle to unregister the listener
     */
    Registration registerCacheEntryListener(EntryListener cacheEntryListener);

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
         *
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
