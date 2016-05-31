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

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.event.*;
import java.io.Serializable;

/**
 * Cache adapter implementation that allows providers implementing the JCache abstraction to be used.
 *
 * @author Allard Buijze
 * @since 2.1.2
 */
@SuppressWarnings("unchecked")
public class JCacheAdapter extends AbstractCacheAdapter<CacheEntryListenerConfiguration> {

    private final javax.cache.Cache jCache;

    /**
     * Initialize the adapter to forward call to the given <code>jCache</code> instance
     *
     * @param jCache The cache to forward all calls to
     */
    public JCacheAdapter(javax.cache.Cache jCache) {
        this.jCache = jCache;
    }

    @Override
    public <K, V> V get(K key) {
        return (V) jCache.get(key);
    }

    @Override
    public <K, V> void put(K key, V value) {
        jCache.put(key, value);
    }

    @Override
    public <K, V> boolean putIfAbsent(K key, V value) {
        return jCache.putIfAbsent(key, value);
    }

    @Override
    public <K> boolean remove(K key) {
        return jCache.remove(key);
    }

    @Override
    public <K> boolean containsKey(K key) {
        return jCache.containsKey(key);
    }

    @Override
    protected CacheEntryListenerConfiguration createListenerAdapter(EntryListener cacheEntryListener) {
        return new JCacheListenerAdapter(cacheEntryListener);
    }

    @Override
    protected Registration doRegisterListener(CacheEntryListenerConfiguration listenerAdapter) {
        jCache.registerCacheEntryListener(listenerAdapter);
        return () -> {
            jCache.deregisterCacheEntryListener(listenerAdapter);
            return true;
        };
    }

    private static final class JCacheListenerAdapter<K, V> implements CacheEntryListenerConfiguration<K, V>,
            CacheEntryUpdatedListener<K, V>, CacheEntryCreatedListener<K, V>, CacheEntryExpiredListener<K, V>,
            CacheEntryRemovedListener<K, V>, Factory<CacheEntryListener<? super K, ? super V>>, Serializable {

        private static final long serialVersionUID = 3260575514029378445L;
        private final EntryListener delegate;

        public JCacheListenerAdapter(EntryListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onCreated(Iterable<CacheEntryEvent<? extends K, ? extends V>> cacheEntryEvents)
                throws CacheEntryListenerException {
            for (CacheEntryEvent event : cacheEntryEvents) {
                delegate.onEntryCreated(event.getKey(), event.getValue());
            }
        }

        @Override
        public void onExpired(Iterable<CacheEntryEvent<? extends K, ? extends V>> iterable)
                throws CacheEntryListenerException {
            for (CacheEntryEvent event : iterable) {
                delegate.onEntryExpired(event.getKey());
            }
        }

        @Override
        public Factory<CacheEntryListener<? super K, ? super V>> getCacheEntryListenerFactory() {
            return this;
        }

        @Override
        public boolean isOldValueRequired() {
            return false;
        }

        @Override
        public Factory<CacheEntryEventFilter<? super K, ? super V>> getCacheEntryEventFilterFactory() {
            return null;
        }

        @Override
        public boolean isSynchronous() {
            return true;
        }

        @Override
        public void onRemoved(Iterable<CacheEntryEvent<? extends K, ? extends V>> iterable)
                throws CacheEntryListenerException {
            for (CacheEntryEvent event : iterable) {
                delegate.onEntryRemoved(event.getKey());
            }
        }

        @Override
        public void onUpdated(Iterable<CacheEntryEvent<? extends K, ? extends V>> iterable)
                throws CacheEntryListenerException {
            for (CacheEntryEvent event : iterable) {
                delegate.onEntryUpdated(event.getKey(), event.getValue());
            }
        }

        @Override
        public CacheEntryListener create() {
            return this;
        }
    }
}
