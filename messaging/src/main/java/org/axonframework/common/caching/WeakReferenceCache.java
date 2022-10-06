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

import org.axonframework.common.Assert;
import org.axonframework.common.Registration;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.UnaryOperator;

/**
 * Cache implementation that keeps values in the cache until the garbage collector has removed them. Unlike the
 * WeakHashMap, which uses weak references on the keys, this Cache uses weak references on the values.
 * <p/>
 * Values are Weakly referenced, which means they are not eligible for removal as long as any other references to the
 * value exist.
 * <p/>
 * Items expire once the garbage collector has removed them. Some time after they have been removed, the entry listeners
 * are being notified thereof. Note that notification are emitted when the cache is being accessed (either for reading
 * or writing). If the cache is not being accessed for a longer period of time, it may occur that listeners are not
 * notified.
 *
 * @author Allard Buijze
 * @author Henrique Sena
 * @since 2.2.1
 */
public class WeakReferenceCache implements Cache {

    private final ConcurrentMap<Object, Entry> cache = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private final Set<EntryListener> adapters = new CopyOnWriteArraySet<>();

    @Override
    public Registration registerCacheEntryListener(EntryListener entryListener) {
        adapters.add(entryListener);
        return () -> adapters.remove(entryListener);
    }

    @Override
    public <K, V> V get(K key) {
        Assert.nonNull(key, () -> "Key may not be null");
        purgeItems();
        final Reference<Object> entry = cache.get(key);

        //noinspection unchecked
        final V returnValue = entry == null ? null : (V) entry.get();
        if (returnValue != null) {
            for (EntryListener adapter : adapters) {
                adapter.onEntryRead(key, returnValue);
            }
        }
        return returnValue;
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Null values not supported");
        }

        purgeItems();
        if (cache.put(key, new Entry(key, value)) != null) {
            for (EntryListener adapter : adapters) {
                adapter.onEntryUpdated(key, value);
            }
        } else {
            for (EntryListener adapter : adapters) {
                adapter.onEntryCreated(key, value);
            }
        }
    }

    @Override
    public boolean putIfAbsent(Object key, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Null values not supported");
        }
        purgeItems();
        if (cache.putIfAbsent(key, new Entry(key, value)) == null) {
            for (EntryListener adapter : adapters) {
                adapter.onEntryCreated(key, value);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object key) {
        if (cache.remove(key) != null) {
            for (EntryListener adapter : adapters) {
                adapter.onEntryRemoved(key);
            }
            return true;
        }
        return false;
    }

    @Override
    public void removeAll() {
        Set<Object> keys = new HashSet<>(cache.keySet());
        keys.forEach(key -> {
            cache.remove(key);
            for (EntryListener adapter : adapters) {
                adapter.onEntryRemoved(key);
            }
        });
    }

    @Override
    public boolean containsKey(Object key) {
        Assert.nonNull(key, () -> "Key may not be null");
        purgeItems();
        final Reference<Object> entry = cache.get(key);

        return entry != null && entry.get() != null;
    }

    private void purgeItems() {
        Entry purgedEntry;
        while ((purgedEntry = (Entry) referenceQueue.poll()) != null) {
            if (cache.remove(purgedEntry.getKey()) != null) {
                for (EntryListener adapter : adapters) {
                    adapter.onEntryExpired(purgedEntry.getKey());
                }
            }
        }
    }

    @Override
    public <V> void computeIfPresent(Object key, UnaryOperator<V> update) {
        //noinspection unchecked
        cache.computeIfPresent(key, (k, v) -> new Entry(k, update.apply((V) v.get())));
    }

    private class Entry extends WeakReference<Object> {

        private final Object key;

        public Entry(Object key, Object value) {
            super(value, referenceQueue);
            this.key = key;
        }

        public Object getKey() {
            return key;
        }
    }
}
