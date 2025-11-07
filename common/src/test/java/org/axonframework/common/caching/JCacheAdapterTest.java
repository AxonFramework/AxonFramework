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

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.spi.CachingProvider;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Test class validating the {@link JCacheAdapter}.
 *
 * @author Gerard Klijs
 */
class JCacheAdapterTest extends AbstractCacheAdapterTest {

    @Override
    AbstractCacheAdapterTest.TestSubjectWrapper getTestSubjectWrapper() {
        CachingProvider provider = Caching.getCachingProvider();
        CacheManager cacheManager = provider.getCacheManager();
        MutableConfiguration<Object, Object> configuration =
                new MutableConfiguration<>()
                        .setTypes(Object.class, Object.class)
                        .setStoreByValue(false)
                        .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(SECONDS, 1)));
        javax.cache.Cache<Object, Object> cache = cacheManager.createCache("jCache", configuration);
        JCacheAdapter testSubject = new JCacheAdapter(cache);
        return new AbstractCacheAdapterTest.TestSubjectWrapper(testSubject, cacheManager::close);
    }
}