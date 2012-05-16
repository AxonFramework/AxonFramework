/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common;

import net.sf.jsr107cache.CacheException;
import net.sf.jsr107cache.CacheListener;
import org.junit.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class NoCacheTest {

    @Test
    public void testCacheDoesNothing() throws CacheException {
        // this is pretty stupid, but we're testing that it does absolutely nothing
        NoCache cache = NoCache.INSTANCE;
        cache.addListener(mock(CacheListener.class));
        cache.clear();
        cache.evict();
        cache.load(new Object());
        assertFalse(cache.containsKey(new Object()));
        assertFalse(cache.containsValue(new Object()));
        assertEquals(Collections.<Object>emptySet(), cache.entrySet());
        assertNull(cache.get(new Object()));
        assertEquals(Collections.<Object, Object>emptyMap(), cache.getAll(Arrays.asList(new Object(), new Object())));
        assertNull(cache.getCacheEntry(new Object()));
        assertNull(cache.getCacheStatistics());
        assertTrue(cache.isEmpty());
        assertEquals(Collections.<Object>emptySet(), cache.keySet());
        cache.loadAll(Arrays.asList(new Object(), new Object()));
        assertNull(cache.peek(new Object()));
        assertNull(cache.put(new Object(), new Object()));
        Map<Object, Object> map = new HashMap<Object, Object>();
        map.put(new Object(), new Object());
        cache.putAll(map);
        assertNull(cache.remove(new Object()));
        cache.removeListener(mock(CacheListener.class));
        assertEquals(0, cache.size());
        assertEquals(0, cache.values().size());
    }
}
