package org.axonframework.common.cache;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import net.sf.ehcache.jcache.JCache;
import net.sf.ehcache.jcache.JCacheManager;

import javax.cache.Cache;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

/**
 * @author Allard Buijze
 */
public class EhCacheStrategy implements CacheStrategy {

    private final DefaultStrategy fallback;

    public EhCacheStrategy(DefaultStrategy fallback) {
        this.fallback = fallback;
    }

    @Override
    public <K, V> void registerCacheEntryListener(Cache<K, V> cache, CacheEntryListener<K, V> cacheListener) {

        try {
            final Ehcache ehCache = cache.unwrap(Ehcache.class);
            if (ehCache == null) {
                // we really need to be defensive in this...
                fallback.registerCacheEntryListener(cache, cacheListener);
            } else {
                ehCache.getCacheEventNotificationService().registerListener(
                        new CacheEventListenerAdapter(cacheListener, ehCache));
            }
        } catch (IllegalArgumentException e) {
            fallback.registerCacheEntryListener(cache, cacheListener);
        }
    }

    private static class CacheEventListenerAdapter implements CacheEventListener {

        private final CacheEntryListener cacheListener;
        private final Ehcache ehCache;

        public CacheEventListenerAdapter(CacheEntryListener cacheListener, Ehcache ehCache) {
            this.cacheListener = cacheListener;
            this.ehCache = ehCache;
        }

        @Override
        public void notifyElementRemoved(Ehcache ehcache, final Element element) throws CacheException {
            if (cacheListener instanceof CacheEntryRemovedListener) {
                ((CacheEntryRemovedListener) cacheListener).entryRemoved(new CacheEntryEventAdapter(ehcache,
                                                                                                    element));
            }
        }

        @Override
        public void notifyElementPut(Ehcache ehcache, Element element) throws CacheException {
            if (cacheListener instanceof CacheEntryCreatedListener) {
                ((CacheEntryCreatedListener) cacheListener).entryCreated(new CacheEntryEventAdapter(ehCache,
                                                                                                    element));
            }
        }

        @Override
        public void notifyElementUpdated(Ehcache ehcache, Element element) throws CacheException {
            if (cacheListener instanceof CacheEntryUpdatedListener) {
                ((CacheEntryUpdatedListener) cacheListener).entryUpdated(new CacheEntryEventAdapter(ehCache,
                                                                                                    element));
            }
        }

        @Override
        public void notifyElementExpired(Ehcache ehcache, Element element) {
            if (cacheListener instanceof CacheEntryExpiredListener) {
                ((CacheEntryExpiredListener) cacheListener).entryExpired(new CacheEntryEventAdapter(ehCache,
                                                                                                    element));
            }
        }

        @Override
        public void notifyElementEvicted(Ehcache ehcache, Element element) {
            if (cacheListener instanceof CacheEntryRemovedListener) {
                ((CacheEntryExpiredListener) cacheListener).entryExpired(new CacheEntryEventAdapter(ehCache,
                                                                                                    element));
            }
        }

        @Override
        public void notifyRemoveAll(Ehcache ehcache) {
        }

        @Override
        public void dispose() {
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return new CacheEventListenerAdapter(cacheListener, (Ehcache) ehCache.clone());
        }
    }

    private static class CacheEntryEventAdapter extends CacheEntryEvent {

        private final Element element;

        public CacheEntryEventAdapter(Ehcache cache, Element element) {
            super(new JCache(cache, new JCacheManager(cache.getCacheManager().getName(),
                                                      cache.getCacheManager(),
                                                      element.getClass().getClassLoader()),
                             element.getClass().getClassLoader()
            ));
            this.element = element;
        }

        @Override
        public Object getKey() {
            return element.getKey();
        }

        @Override
        public Object getValue() {
            return element.getValue();
        }
    }
}
