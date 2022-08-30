/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


/**
 * Tests for cache implementation that keeps values in the cache until the garbage collector has removed them {@link WeakReferenceCache}.
 *
 * @author Steven van Beelen
 * @author Henrique Sena
 */
class WeakReferenceCacheTest {

    private WeakReferenceCache testSubject;
    private Cache.EntryListener mockListener;
    private Registration registration;

    @BeforeEach
    void setUp() {
        mockListener = mock(Cache.EntryListener.class);
        testSubject = new WeakReferenceCache();
        registration = testSubject.registerCacheEntryListener(mockListener);
    }

    @Test
    void itemPurgedWhenNoLongerReferenced() throws Exception {
        // Mockito holds a reference to all parameters, preventing GC
        registration.cancel();
        final Set<String> expiredEntries = new CopyOnWriteArraySet<>();
        testSubject.registerCacheEntryListener(new Cache.EntryListenerAdapter() {
            @Override
            public void onEntryExpired(Object key) {
                expiredEntries.add(key.toString());
            }
        });

        Object value = new Object();
        testSubject.put("test1", value);
        assertSame(value, testSubject.get("test1"));

        // dereference
        value = null;
        // hope for a true GC
        System.gc();

        for (int i = 0; i < 5 && testSubject.containsKey("test1"); i++) {
            // try again
            System.gc();
            Thread.sleep(100);
        }

        assertNull(testSubject.get("test1"));

        // the reference is gone, but it may take a 'while' for the reference to be queued
        for (int i = 0; i < 5 && !expiredEntries.contains("test1"); i++) {
            testSubject.get("test1");
            Thread.sleep(100);
        }

        assertEquals(singleton("test1"), expiredEntries);
    }

    @Test
    void entryListenerNotifiedOfCreationUpdateAndDeletion() {

        Object value = new Object();
        Object value2 = new Object();
        testSubject.put("test1", value);
        verify(mockListener).onEntryCreated("test1", value);

        testSubject.put("test1", value2);
        verify(mockListener).onEntryUpdated("test1", value2);

        testSubject.get("test1");
        verify(mockListener).onEntryRead("test1", value2);

        testSubject.remove("test1");
        verify(mockListener).onEntryRemoved("test1");

        assertNull(testSubject.get("test1"));
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenKeyIsNullOnGet() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.get(null));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenKeyIsNullOnContainsKey() {
        assertThrows(IllegalArgumentException.class, () -> testSubject.containsKey(null));
    }
}
