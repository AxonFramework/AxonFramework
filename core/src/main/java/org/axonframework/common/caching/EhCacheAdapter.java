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

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import org.axonframework.common.Registration;

/**
 * Cache implementation that delegates all calls to an EhCache instance.
 *
 * @author Allard Buijze
 * @since 2.1.2
 */
public class EhCacheAdapter extends AbstractCacheAdapter<CacheEventListener> {

    private final Ehcache ehCache;

    /**
     * Initialize the adapter to forward all call to the given <code>ehCache</code> instance
     *
     * @param ehCache The cache instance to forward calls to
     */
    public EhCacheAdapter(Ehcache ehCache) {
        this.ehCache = ehCache;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> V get(K key) {
        final Element element = ehCache.get(key);
        return element == null ? null : (V) element.getObjectValue();
    }

    @Override
    public <K, V> void put(K key, V value) {
        ehCache.put(new Element(key, value));
    }

    @Override
    public <K, V> boolean putIfAbsent(K key, V value) {
        return ehCache.putIfAbsent(new Element(key, value)) == null;
    }

    @Override
    public <K> boolean remove(K key) {
        return ehCache.remove(key);
    }

    @Override
    public <K> boolean containsKey(K key) {
        return ehCache.isKeyInCache(key);
    }

    @SuppressWarnings("ClassEscapesDefinedScope")
    @Override
    protected EhCacheAdapter.CacheEventListenerAdapter createListenerAdapter(EntryListener cacheEntryListener) {
        return new EhCacheAdapter.CacheEventListenerAdapter(ehCache, cacheEntryListener);
    }

    @Override
    protected Registration doRegisterListener(CacheEventListener listenerAdapter) {
        ehCache.getCacheEventNotificationService().registerListener(listenerAdapter);
        return () -> ehCache.getCacheEventNotificationService().unregisterListener(listenerAdapter);
    }

    @SuppressWarnings("unchecked")
    private static class CacheEventListenerAdapter implements CacheEventListener, Cloneable {

        private Ehcache ehCache;
        private EntryListener delegate;

        public CacheEventListenerAdapter(Ehcache ehCache, EntryListener delegate) {
            this.ehCache = ehCache;
            this.delegate = delegate;
        }

        @Override
        public void notifyElementRemoved(Ehcache cache, Element element) throws CacheException {
            if (cache.equals(ehCache)) {
                delegate.onEntryRemoved(element.getObjectKey());
            }
        }

        @Override
        public void notifyElementPut(Ehcache cache, Element element) throws CacheException {
            if (cache.equals(ehCache)) {
                delegate.onEntryCreated(element.getObjectKey(), element.getObjectValue());
            }
        }

        @Override
        public void notifyElementUpdated(Ehcache cache, Element element) throws CacheException {
            if (cache.equals(ehCache)) {
                delegate.onEntryUpdated(element.getObjectKey(), element.getObjectValue());
            }
        }

        @Override
        public void notifyElementExpired(Ehcache cache, Element element) {
            if (cache.equals(ehCache)) {
                delegate.onEntryExpired(element.getObjectKey());
            }
        }

        @Override
        public void notifyElementEvicted(Ehcache cache, Element element) {
            if (cache.equals(ehCache)) {
                delegate.onEntryExpired(element.getObjectKey());
            }
        }

        @Override
        public void notifyRemoveAll(Ehcache cache) {
        }

        @Override
        public void dispose() {
        }

        @Override
        public CacheEventListenerAdapter clone() throws CloneNotSupportedException {
            CacheEventListenerAdapter clone = (CacheEventListenerAdapter) super.clone();
            clone.ehCache = (Ehcache) ehCache.clone();
            clone.delegate = (EntryListener) delegate.clone();
            return clone;
        }
    }
}
