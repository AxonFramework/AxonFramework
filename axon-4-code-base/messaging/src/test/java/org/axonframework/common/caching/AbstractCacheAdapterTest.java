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

import org.axonframework.common.Registration;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating {@link AbstractCacheAdapter} implementations.
 *
 * @author Gerard Klijs
 */
public abstract class AbstractCacheAdapterTest {

    private TestSubjectWrapper testSubjectWrapper;
    @SuppressWarnings("rawtypes")
    private AbstractCacheAdapter testSubject;
    private org.axonframework.common.caching.Cache.EntryListener mockListener;
    private Registration registration;

    abstract TestSubjectWrapper getTestSubjectWrapper();

    @BeforeEach
    void setUp() {
        testSubjectWrapper = getTestSubjectWrapper();
        testSubject = testSubjectWrapper.testSubject;
        mockListener = mock(org.axonframework.common.caching.Cache.EntryListener.class);
        registration = testSubject.registerCacheEntryListener(mockListener);
    }

    @AfterEach
    void tearDown() {
        reset(mockListener);
        testSubjectWrapper.closeFunction.run();
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

    static class TestSubjectWrapper {

        @SuppressWarnings("rawtypes")
        private final AbstractCacheAdapter testSubject;
        private final Runnable closeFunction;

        @SuppressWarnings("rawtypes")
        TestSubjectWrapper(AbstractCacheAdapter testSubject, Runnable closeFunction) {
            this.testSubject = testSubject;
            this.closeFunction = closeFunction;
        }
    }
}
