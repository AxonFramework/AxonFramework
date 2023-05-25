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

import net.sf.ehcache.CacheManager;
import org.axonframework.common.caching.Cache;
import org.junit.jupiter.api.*;

/**
 * Concrete implementation of the {@link CachingSagaStoreTest} using the {@link EhCacheAdapter}.
 *
 * @author Steven van Beelen
 */
class EhCachingSagaStoreTest extends CachingSagaStoreTest {

    private CacheManager cacheManager;
    private net.sf.ehcache.Cache ehCache;

    @AfterEach
    void tearDown() {
        cacheManager.shutdown();
    }

    @Override
    Cache sagaCache() {
        if (ehCache == null) {
            buildEhCache();
        }
        return new EhCacheAdapter(ehCache);
    }

    @Override
    Cache associationCache() {
        if (ehCache == null) {
            buildEhCache();
        }
        return new EhCacheAdapter(ehCache);
    }

    private void buildEhCache() {
        ehCache = new net.sf.ehcache.Cache("test", 100, false, false, 10, 10);
        cacheManager = CacheManager.create();
        cacheManager.addCache(ehCache);
    }
}
