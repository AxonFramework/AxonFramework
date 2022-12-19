package org.axonframework.integrationtests.cache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.SizeOfPolicyConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.EhCacheAdapter;
import org.junit.jupiter.api.*;

/**
 * {@link Ehcache} specific implementation of the {@link CachingIntegrationTestSuite}.
 *
 * @author Steven van Beelen
 */
class EhCacheIntegrationTest extends CachingIntegrationTestSuite {

    private CacheManager cacheManager;

    @Override
    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.create(getEhCacheConfiguration());
        super.setUp();
    }

    @AfterEach
    void tearDown() {
        cacheManager.shutdown();
    }

    @Override
    public Cache buildCache(String name) {
        Ehcache cache = createCache(name);
        cacheManager.addCache(cache);
        return new EhCacheAdapter(cache);
    }

    private Ehcache createCache(String name) {
        CacheConfiguration cacheConfig = new CacheConfiguration(name, 10_000)
                .name(name)
                .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LRU)
                .eternal(false)
                .timeToLiveSeconds(600)
                .timeToIdleSeconds(600);
        return new net.sf.ehcache.Cache(cacheConfig);
    }

    private net.sf.ehcache.config.Configuration getEhCacheConfiguration() {
        net.sf.ehcache.config.Configuration configuration = new net.sf.ehcache.config.Configuration();
        SizeOfPolicyConfiguration sizeOfPolicyConfiguration = new SizeOfPolicyConfiguration();
        sizeOfPolicyConfiguration.maxDepth(13000);
        sizeOfPolicyConfiguration.maxDepthExceededBehavior(SizeOfPolicyConfiguration.MaxDepthExceededBehavior.ABORT);
        configuration.addSizeOfPolicy(sizeOfPolicyConfiguration);
        return configuration;
    }
}
