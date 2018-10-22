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
import org.junit.Test;

import javax.cache.CacheException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
public class NoCacheTest {

    @Test
    public void testCacheDoesNothing() throws CacheException {
        // this is pretty stupid, but we're testing that it does absolutely nothing
        NoCache cache = NoCache.INSTANCE;
        Registration registration = cache.registerCacheEntryListener(mock(Cache.EntryListener.class));
        assertFalse(cache.containsKey(new Object()));
        assertNull(cache.get(new Object()));
        cache.put(new Object(), new Object());
        Map<Object, Object> map = new HashMap<>();
        map.put(new Object(), new Object());
        assertFalse(cache.remove(new Object()));
        registration.cancel();
    }
}
