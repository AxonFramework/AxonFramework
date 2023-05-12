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
import org.ehcache.core.Ehcache;
import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;
import org.ehcache.event.EventFiring;
import org.ehcache.event.EventOrdering;
import org.ehcache.event.EventType;
import org.ehcache.impl.events.CacheEventDispatcherImpl;

import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

/**
 * Cache implementation that delegates all calls to an EhCache instance.
 *
 * @author Allard Buijze
 * @since 2.1.2
 */
@SuppressWarnings("rawtypes")
public class EhCacheAdapter extends AbstractCacheAdapter<CacheEventListener> {

    @SuppressWarnings("rawtypes")
    private final Ehcache ehCache;

    /**
     * Initialize the adapter to forward all call to the given {@code ehCache} instance
     *
     * @param ehCache The cache instance to forward calls to
     */
    @SuppressWarnings("rawtypes")
    public EhCacheAdapter(Ehcache ehCache) {
        this.ehCache = ehCache;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> V get(K key) {
        final Object value = ehCache.get(key);
        //noinspection unchecked
        return value != null ? (V) value : null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void put(Object key, Object value) {
        ehCache.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean putIfAbsent(Object key, Object value) {
        return ehCache.putIfAbsent(key, value) == null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object key) {
        Object value = ehCache.get(key);
        if (value == null) {
            return false;
        }
        return ehCache.remove(key, value);
    }

    @Override
    public void removeAll() {
        ehCache.clear();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean containsKey(Object key) {
        return ehCache.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> void computeIfPresent(Object key, UnaryOperator<V> update) {
        Object oldValue;
        V newValue;
        do {
            oldValue = ehCache.get(key);
            if (oldValue == null) {
                break;
            }
            //noinspection unchecked
            newValue = update.apply((V) oldValue);
        } while (!replaceOrRemove(key, oldValue, newValue));
    }

    /**
     * Replace or remove the element under {@code key}. If the {@code newValue} is not {@code null}, we invoke replace.
     * If the {@code newValue} is {@code null}, the compute task decided to remove the entry instead. Since an
     * invocation of {@link Ehcache#replace(Object, Object, Object)} does not remove an {@link Object} if it's value is
     * {@code null}, we need to do this ourselves.
     *
     * @param key      The reference to the value to replace or remove, depending on whether the {@code newValue} is
     *                 {@code null}.
     * @param oldValue The old entry to replace with the {@code newValue}, if {@code newValue} is not {@code null}.
     * @param newValue The new value to replace with the {@code oldValue}, if it is not {@code null}.
     * @param <V>      The generic type of the value stored under the given {@code key}.
     * @return A boolean stating whether the {@link Ehcache#replace(Object, Object, Object)} or {@link #remove(Object)}
     * task succeeded.
     */
    @SuppressWarnings("unchecked")
    private <V> boolean replaceOrRemove(Object key, V oldValue, V newValue) {
        return newValue != null ? ehCache.replace(key, oldValue, newValue) : remove(key);
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected CacheEventListener createListenerAdapter(EntryListener cacheEntryListener) {
        return new EhCacheAdapter.CacheEventListenerAdapter(cacheEntryListener);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected Registration doRegisterListener(CacheEventListener listenerAdapter) {
        CacheEventDispatcherImpl<Number, String> eventService = new CacheEventDispatcherImpl<>(
                Executors.newCachedThreadPool(),
                Executors.newSingleThreadScheduledExecutor()
        );
        eventService.setListenerSource(ehCache);
        eventService.registerCacheEventListener(
                listenerAdapter,
                EventOrdering.ORDERED,
                EventFiring.ASYNCHRONOUS,
                EnumSet.allOf(EventType.class)
        );
        return () -> {
            try {
                eventService.deregisterCacheEventListener(listenerAdapter);
            } catch (IllegalStateException e) {
                return false;
            }
            return true;
        };
    }

    @SuppressWarnings("rawtypes")
    private static class CacheEventListenerAdapter implements CacheEventListener, Cloneable {

        private EntryListener delegate;

        public CacheEventListenerAdapter(EntryListener delegate) {
            this.delegate = delegate;
        }

        private void setDelegate(EntryListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public CacheEventListenerAdapter clone() throws CloneNotSupportedException {
            CacheEventListenerAdapter clone = (CacheEventListenerAdapter) super.clone();
            clone.setDelegate((EntryListener) delegate.clone());
            return clone;
        }

        @Override
        public void onEvent(CacheEvent event) {
            switch (event.getType()) {
                case CREATED:
                    delegate.onEntryCreated(event.getKey(), event.getNewValue());
                    break;
                case UPDATED:
                    delegate.onEntryUpdated(event.getKey(), event.getNewValue());
                    break;
                case REMOVED:
                    delegate.onEntryRemoved(event.getKey());
                    break;
                case EXPIRED:
                    delegate.onEntryExpired(event.getKey());
                    break;
                case EVICTED:
                    //nothing needs to be done
                    break;
                default:
                    throw new AssertionError("Unsupported event type " + event.getType());
            }
        }
    }
}
