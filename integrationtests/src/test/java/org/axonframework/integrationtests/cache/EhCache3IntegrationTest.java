/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.integrationtests.cache;

import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.EhCache3Adapter;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.core.Ehcache;
import org.ehcache.core.EhcacheManager;
import org.ehcache.core.config.DefaultConfiguration;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link Ehcache} specific implementation of the {@link CachingIntegrationTestSuite}.
 *
 * @author Steven van Beelen
 */
class EhCache3IntegrationTest extends CachingIntegrationTestSuite {

    private CacheManager cacheManager;

    @Override
    @BeforeEach
    void setUp() {
        Map<String, CacheConfiguration<?, ?>> caches = new HashMap<>();
        DefaultConfiguration config = new DefaultConfiguration(caches, null);
        cacheManager = new EhcacheManager(config);
        cacheManager.init();
        super.setUp();
    }

    @AfterEach
    void tearDown() {
        cacheManager.close();
    }

    @Override
    public Cache buildCache(String name) {
        return new EhCache3Adapter(createCache(name));
    }

    private Ehcache createCache(String name) {
        return (Ehcache) cacheManager.createCache(
                name,
                CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(
                                Object.class,
                                Object.class,
                                ResourcePoolsBuilder.heap(100L).build())
                        .build());
    }
}
