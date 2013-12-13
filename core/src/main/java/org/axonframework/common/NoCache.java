/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.common;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheLoader;
import javax.cache.CacheManager;
import javax.cache.CacheStatistics;
import javax.cache.CacheWriter;
import javax.cache.Status;
import javax.cache.event.CacheEntryListener;
import javax.cache.mbeans.CacheMXBean;
import javax.cache.transaction.IsolationLevel;
import javax.cache.transaction.Mode;

/**
 * Cache implementation that does absolutely nothing. Objects aren't cached, making it a special case implementation
 * for
 * the case when caching is disabled.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public final class NoCache implements Cache<Object, Object> {

    /**
     * Creates a singleton reference the the NoCache implementation.
     */
    public static final NoCache INSTANCE = new NoCache();
    private static final CacheConfiguration.Duration IMMEDIATE = new CacheConfiguration.Duration(TimeUnit.SECONDS, 0);

    private NoCache() {
    }


    @Override
    public Object get(Object key) {
        return null;
    }

    @Override
    public Map<Object, Object> getAll(Set keys) {
        return Collections.emptyMap();
    }

    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    @Override
    public Future<Object> load(Object key) {
        return new Now<Object>(null);
    }

    @Override
    public Future<Map<Object, ?>> loadAll(Set<?> keys) {
        return new Now<Map<Object, ?>>(Collections.<Object, Object>emptyMap());
    }

    @Override
    public CacheStatistics getStatistics() {
        return null;
    }

    @Override
    public void put(Object key, Object value) {
    }

    @Override
    public Object getAndPut(Object key, Object value) {
        return null;
    }

    @Override
    public void putAll(Map map) {
    }

    @Override
    public boolean putIfAbsent(Object key, Object value) {
        return true;
    }

    @Override
    public boolean remove(Object key) {
        return false;
    }

    @Override
    public boolean remove(Object key, Object oldValue) {
        return false;
    }

    @Override
    public Object getAndRemove(Object key) {
        return null;
    }

    @Override
    public boolean replace(Object key, Object oldValue, Object newValue) {
        return true;
    }

    @Override
    public boolean replace(Object key, Object value) {
        return true;
    }

    @Override
    public Object getAndReplace(Object key, Object value) {
        return null;
    }

    @Override
    public void removeAll(Set keys) {
    }

    @Override
    public void removeAll() {
    }

    @Override
    public CacheConfiguration<Object, Object> getConfiguration() {
        return new CacheConfiguration<Object, Object>() {
            @Override
            public boolean isReadThrough() {
                return false;
            }

            @Override
            public boolean isWriteThrough() {
                return false;
            }

            @Override
            public boolean isStoreByValue() {
                return false;
            }

            @Override
            public boolean isStatisticsEnabled() {
                return false;
            }

            @Override
            public void setStatisticsEnabled(boolean enableStatistics) {
            }

            @Override
            public boolean isTransactionEnabled() {
                return false;
            }

            @Override
            public IsolationLevel getTransactionIsolationLevel() {
                return IsolationLevel.NONE;
            }

            @Override
            public Mode getTransactionMode() {
                return Mode.NONE;
            }

            @Override
            public CacheLoader<Object, ?> getCacheLoader() {
                return null;
            }

            @Override
            public CacheWriter<? super Object, ? super Object> getCacheWriter() {
                return null;
            }

            @Override
            public Duration getExpiry(ExpiryType type) {
                return IMMEDIATE;
            }
        };
    }

    @Override
    public boolean registerCacheEntryListener(CacheEntryListener<? super Object, ? super Object> cacheEntryListener) {
        return false;
    }

    @Override
    public boolean unregisterCacheEntryListener(CacheEntryListener<?, ?> cacheEntryListener) {
        return false;
    }

    @Override
    public Object invokeEntryProcessor(Object key, EntryProcessor<Object, Object> entryProcessor) {
        return entryProcessor.process(new NoCacheEntry(key));
    }

    @Override
    public String getName() {
        return "NoCache";
    }

    @Override
    public CacheManager getCacheManager() {
        return null;
    }

    @Override
    public Iterator<Entry<Object, Object>> iterator() {
        return Collections.<Entry<Object, Object>>emptyList().iterator();
    }

    @Override
    public CacheMXBean getMBean() {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isInstance(this)) {
            return (T) this;
        }
        throw new IllegalArgumentException("NoCache implementation cannot be unwrapped to a " + clazz.getName());
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public Status getStatus() {
        return Status.STARTED;
    }


    private static class NoCacheEntry implements MutableEntry<Object, Object> {

        private final Object key;
        private Object value;

        public NoCacheEntry(Object key) {
            this.key = key;
            value = null;
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public void remove() {
        }

        @Override
        public void setValue(Object value) {
            this.value = value;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return value;
        }

    }

    private class Now<T> implements Future<T> {

        private final T value;

        public Now(T value) {
            this.value = value;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return value;
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return value;
        }
    }
}
