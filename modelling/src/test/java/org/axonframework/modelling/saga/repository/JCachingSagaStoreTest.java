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

package org.axonframework.modelling.saga.repository;

import com.hazelcast.cache.HazelcastCachingProvider;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.JCacheAdapter;
import org.junit.jupiter.api.*;

import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

/**
 * Concrete implementation of the {@link CachingSagaStoreTest} using the {@link JCacheAdapter}.
 *
 * @author Steven van Beelen
 */
class JCachingSagaStoreTest extends CachingSagaStoreTest {

    private CachingProvider cachingProvider;
    private CacheManager cacheManager;
    private javax.cache.Cache<Object, Object> jCache;

    @AfterEach
    void tearDown() {
        cachingProvider.close();
        cacheManager.close();
    }

    @Override
    Cache sagaCache() {
        if (jCache == null) {
            buildJCache();
        }
        return new JCacheAdapter(jCache);
    }

    @Override
    Cache associationCache() {
        if (jCache == null) {
            buildJCache();
        }
        return new JCacheAdapter(jCache);
    }

    private void buildJCache() {
        cachingProvider = new HazelcastCachingProvider();
        cacheManager = cachingProvider.getCacheManager();
        jCache = cacheManager.createCache("sagaStoreCache", new MutableConfiguration<>());
    }
}
