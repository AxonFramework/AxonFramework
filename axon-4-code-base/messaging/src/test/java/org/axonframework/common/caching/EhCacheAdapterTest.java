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

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EhCacheAdapter}.
 *
 * @author Steven van Beelen
 */
class EhCacheAdapterTest {

    private EhCacheAdapter testSubject;

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        Cache cache = new Cache("test", 100, false, false, 10, 10);
        cacheManager = CacheManager.create();
        cacheManager.addCache(cache);

        testSubject = new EhCacheAdapter(cache);
    }

    @AfterEach
    void tearDown() {
        cacheManager.shutdown();
    }

    @Test
    void removeAllRemovesAllEntries() {
        testSubject.put("one", new Object());
        testSubject.put("two", new Object());
        testSubject.put("three", new Object());
        testSubject.put("four", new Object());

        assertTrue(testSubject.containsKey("one"));
        assertTrue(testSubject.containsKey("two"));
        assertTrue(testSubject.containsKey("three"));
        assertTrue(testSubject.containsKey("four"));

        testSubject.removeAll();

        assertFalse(testSubject.containsKey("one"));
        assertFalse(testSubject.containsKey("two"));
        assertFalse(testSubject.containsKey("three"));
        assertFalse(testSubject.containsKey("four"));
    }

    @Test
    void computeIfPresentDoesNotUpdateNonExistingEntry() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        testSubject.computeIfPresent("some-key", v -> {
            invoked.set(true);
            return v;
        });

        assertFalse(invoked.get());
    }

    @Test
    void computeIfPresentUpdatesExistingEntry() {
        String testKey = "some-key";
        testSubject.put(testKey, new Object());

        AtomicBoolean invoked = new AtomicBoolean(false);

        testSubject.computeIfPresent(testKey, v -> {
            invoked.set(true);
            return v;
        });

        assertTrue(invoked.get());
    }
}