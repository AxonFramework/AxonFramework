package org.axonframework.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import javax.cache.event.CacheEntryListener;

/**
 * Utility class to work around an issue in the EhCache adapter for JCache.
 *
 * @author Allard Buijze
 * @since 2.1.2
 */
public class CacheUtils {

    private static final Logger logger = LoggerFactory.getLogger(CacheUtils.class);
    private static final CacheStrategy CACHE_STRATEGY = defineCacheStrategy();

    private static CacheStrategy defineCacheStrategy() {
        try {
            CacheUtils.class.getClassLoader().loadClass("net.sf.ehcache.Ehcache");
            logger.debug("EhCache/JCache detected on classpath. Activating workaround...");
            return new EhCacheStrategy(new DefaultStrategy());
        } catch (Exception e) {
            logger.debug("EhCache/JCache not found. No workaround needed.");
            return new DefaultStrategy();
        }
    }

    /**
     * Registers the given <code>cacheListener</code> to the given <code>cache</code>. To work around a known issue in
     * the EhCache/JCache adapter, the listener will be registered in the underlying cache directly if it is an EhCache
     * implementation. In other cases, the listener is registered using the javax cache API.
     *
     * @param cache         The cache to listen to
     * @param cacheListener The listener to notify of changes in the cache
     */

    public static <K, V> void registerCacheEntryListener(final Cache<K, V> cache,
                                                         final CacheEntryListener<K, V> cacheListener) {
        CACHE_STRATEGY.registerCacheEntryListener(cache, cacheListener);
    }

}
