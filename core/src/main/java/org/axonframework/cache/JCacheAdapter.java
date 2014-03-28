package org.axonframework.cache;

import java.io.Serializable;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

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
    protected void doUnregisterListener(CacheEntryListenerConfiguration listenerAdapter) {
        jCache.deregisterCacheEntryListener(listenerAdapter);
    }

    @Override
    protected void doRegisterListener(CacheEntryListenerConfiguration listenerAdapter) {
        jCache.registerCacheEntryListener(listenerAdapter);
    }

    private static final class JCacheListenerAdapter<K, V> implements CacheEntryListenerConfiguration<K, V>,
            CacheEntryUpdatedListener<K, V>, CacheEntryCreatedListener<K, V>, CacheEntryExpiredListener<K, V>,
            CacheEntryRemovedListener<K, V>, Factory<CacheEntryListener<? super K, ? super V>>, Serializable {

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
