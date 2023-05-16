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

package org.axonframework.common.caching;

import org.axonframework.common.Registration;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.core.Ehcache;
import org.ehcache.core.EhcacheManager;
import org.ehcache.core.config.DefaultConfiguration;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EhCacheAdapter}.
 *
 * @author Steven van Beelen
 */
class EhCacheAdapterTest {

    private EhCacheAdapter testSubject;
    private CacheManager cacheManager;
    private org.axonframework.common.caching.Cache.EntryListener mockListener;
    private Registration registration;

    @BeforeEach
    void setUp() {
        Map<String, CacheConfiguration<?, ?>> caches = new HashMap<>();
        DefaultConfiguration config = new DefaultConfiguration(caches, null);
        cacheManager = new EhcacheManager(config);
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

        testSubject = new EhCacheAdapter((Ehcache) cache);
        mockListener = mock(org.axonframework.common.caching.Cache.EntryListener.class);
        registration = testSubject.registerCacheEntryListener(mockListener);
    }

    @AfterEach
    void tearDown() {
        reset(mockListener);
        cacheManager.close();
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

    @Test
    void putIfAbsentWorksCorrectly() {
        Object value = new Object();
        Object value2 = new Object();

        testSubject.putIfAbsent("test1", value);
        assertEquals(value, testSubject.get("test1"));

        testSubject.putIfAbsent("test1", value2);
        assertEquals(value, testSubject.get("test1"));
    }


    @Test
    void entryListenerNotifiedOfCreationUpdateAndDeletion() {
        Object value = new Object();
        Object value2 = new Object();
        testSubject.put("test1", value);
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryCreated("test1", value));

        testSubject.put("test1", value2);
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryUpdated("test1", value2));

        testSubject.remove("test1");
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryRemoved("test1"));

        assertNull(testSubject.get("test1"));
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    void entryListenerNotifiedOfExpired() {
        Object value = new Object();

        testSubject.put("test1", value);
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryCreated("test1", value));
        await().atMost(Duration.ofSeconds(1L)).untilAsserted(() -> {
            testSubject.get("test1");
            verify(mockListener).onEntryExpired("test1");
        });
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    void entryListenerCanBeDeregistered() {
        Object value = new Object();
        Object value2 = new Object();
        testSubject.put("test1", value);
        await().atMost(Duration.ofMillis(300L)).untilAsserted(
                () -> verify(mockListener).onEntryCreated("test1", value));
        registration.cancel();

        testSubject.put("test2", value2);
        verifyNoMoreInteractions(mockListener);
    }
}