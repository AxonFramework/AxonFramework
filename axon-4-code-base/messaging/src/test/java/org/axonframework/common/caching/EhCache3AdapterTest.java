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

package org.axonframework.common.caching;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.core.Ehcache;
import org.ehcache.core.EhcacheManager;
import org.ehcache.core.config.DefaultConfiguration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Test class validating the {@link EhCache3Adapter}.
 *
 * @author Gerard Klijs
 */
class EhCache3AdapterTest extends AbstractCacheAdapterTest {

    @Override
    @SuppressWarnings("rawtypes")
    AbstractCacheAdapterTest.TestSubjectWrapper getTestSubjectWrapper() {
        Map<String, CacheConfiguration<?, ?>> caches = new HashMap<>();
        DefaultConfiguration config = new DefaultConfiguration(caches, null);
        CacheManager cacheManager = new EhcacheManager(config);
        cacheManager.init();
        Cache cache = cacheManager
                .createCache(
                        "test",
                        CacheConfigurationBuilder
                                .newCacheConfigurationBuilder(
                                        Object.class,
                                        Object.class,
                                        ResourcePoolsBuilder.heap(10L).build())
                                .withExpiry(ExpiryPolicyBuilder.timeToIdleExpiration(Duration.ofMillis(200L)))
                                .build());
        EhCache3Adapter testSubject = new EhCache3Adapter((Ehcache) cache);
        return new TestSubjectWrapper(testSubject, cacheManager::close);
    }
}