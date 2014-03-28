/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.cache.Cache;
import org.axonframework.cache.NoCache;
import org.junit.*;

import java.util.HashMap;
import java.util.Map;
import javax.cache.CacheException;

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
        cache.registerCacheEntryListener(mock(Cache.EntryListener.class));
        assertFalse(cache.containsKey(new Object()));
        assertNull(cache.get(new Object()));
        cache.put(new Object(), new Object());
        Map<Object, Object> map = new HashMap<Object, Object>();
        map.put(new Object(), new Object());
        assertFalse(cache.remove(new Object()));
        cache.unregisterCacheEntryListener(mock(Cache.EntryListener.class));
    }
}
