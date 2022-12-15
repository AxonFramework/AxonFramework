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

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import org.axonframework.common.Registration;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Cache implementation that delegates all calls to an EhCache instance.
 *
 * @author Allard Buijze
 * @since 2.1.2
 */
public class EhCacheAdapter extends AbstractCacheAdapter<CacheEventListener> {

    private final Ehcache ehCache;

    /**
     * Initialize the adapter to forward all call to the given {@code ehCache} instance
     *
     * @param ehCache The cache instance to forward calls to
     */
    public EhCacheAdapter(Ehcache ehCache) {
        this.ehCache = ehCache;
    }

    @Override
    public <K, V> V get(K key) {
        final Element element = ehCache.get(key);
        //noinspection unchecked
        return element == null ? null : (V) element.getObjectValue();
    }

    @Override
    public void put(Object key, Object value) {
        ehCache.put(new Element(key, value));
    }

    @Override
    public boolean putIfAbsent(Object key, Object value) {
        return ehCache.putIfAbsent(new Element(key, value)) == null;
    }

    @Override
    public <T> T getOrCompute(Object key, Supplier<T> valueSupplier) {
        Element current = ehCache.get(key);
        if(current != null) {
            return (T) current.getObjectValue();
        }
        T newValue = valueSupplier.get();
        ehCache.put(new Element(key, newValue));
        return newValue;
    }

    @Override
    public boolean remove(Object key) {
        return ehCache.remove(key);
    }

    @Override
    public void removeAll() {
        ehCache.removeAll();
    }

    @Override
    public boolean containsKey(Object key) {
        return ehCache.isKeyInCache(key);
    }

    @Override
    public <V> void computeIfPresent(Object key, UnaryOperator<V> update) {
        Element oldValue;
        V newValue;
        do {
            oldValue = ehCache.get(key);
            if (oldValue == null) {
                break;
            }
            //noinspection unchecked
            newValue = update.apply((V) oldValue.getObjectValue());
        } while (!replaceOrRemove(key, oldValue, newValue));
    }

    /**
     * Replace or remove the element under {@code key}. If the {@code newValue} is not {@code null}, we invoke replace.
     * If the {@code newValue} is {@code null}, the compute task decided to remove the entry instead. Since an
     * invocation of {@link Ehcache#replace(Element, Element)} does not remove an {@link Element} if it's value is
     * {@code null}, we need to do this ourselves.
     *
     * @param key      The reference to the value to replace or remove, depending on whether the {@code newValue} is
     *                 {@code null}.
     * @param oldValue The old entry to replace with the {@code newValue}, if {@code newValue} is not {@code null}.
     * @param newValue The new value to replace with the {@code oldValue}, if it is not {@code null}.
     * @param <V>      The generic type of the value stored under the given {@code key}.
     * @return A boolean stating whether the {@link Ehcache#replace(Element, Element)} or {@link Ehcache#remove(Object)}
     * task succeeded.
     */
    private <V> boolean replaceOrRemove(Object key, Element oldValue, V newValue) {
        return newValue != null ? ehCache.replace(oldValue, new Element(key, newValue)) : ehCache.remove(key);
    }

    @Override
    protected EhCacheAdapter.CacheEventListenerAdapter createListenerAdapter(EntryListener cacheEntryListener) {
        return new EhCacheAdapter.CacheEventListenerAdapter(ehCache, cacheEntryListener);
    }

    @Override
    protected Registration doRegisterListener(CacheEventListener listenerAdapter) {
        ehCache.getCacheEventNotificationService().registerListener(listenerAdapter);
        return () -> ehCache.getCacheEventNotificationService().unregisterListener(listenerAdapter);
    }

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
