/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventhandling.saga.repository;

import net.sf.ehcache.CacheManager;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.EhCacheAdapter;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CachingSagaStoreTest {

    private Cache associationsCache;
    private org.axonframework.common.caching.Cache sagaCache;
    private CachingSagaStore<StubSaga> testSubject;
    private CacheManager cacheManager;
    private net.sf.ehcache.Cache ehCache;
    private SagaStore<Object> mockSagaStore;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        ehCache = new net.sf.ehcache.Cache("test", 100, false, false, 10, 10);
        cacheManager = CacheManager.create();
        cacheManager.addCache(ehCache);
        associationsCache = spy(new EhCacheAdapter(ehCache));
        sagaCache = spy(new EhCacheAdapter(ehCache));

        mockSagaStore = spy(new InMemorySagaStore());

        testSubject = new CachingSagaStore(mockSagaStore, associationsCache, sagaCache);
    }

    @After
    public void tearDown() throws Exception {
        cacheManager.shutdown();
    }

    @Test
    public void testSagaAddedToCacheOnAdd() throws Exception {

        testSubject.insertSaga(StubSaga.class, "123", new StubSaga(), null, singleton(new AssociationValue("key", "value")));

        verify(sagaCache).put(eq("123"), any());
        verify(associationsCache, never()).put(any(), any());
    }

    @Test
    public void testAssociationsAddedToCacheOnLoad() throws Exception {
        testSubject.insertSaga(StubSaga.class, "id", new StubSaga(), null, singleton(new AssociationValue("key", "value")));

        verify(associationsCache, never()).put(any(), any());

        ehCache.removeAll();
        reset(sagaCache, associationsCache);

        final AssociationValue associationValue = new AssociationValue("key", "value");

        Set<String> actual = testSubject.findSagas(StubSaga.class, associationValue);
        assertEquals(actual, singleton("id"));
        verify(associationsCache, atLeast(1)).get("org.axonframework.eventhandling.saga.repository.StubSaga/key=value");
        verify(associationsCache).put("org.axonframework.eventhandling.saga.repository.StubSaga/key=value",
                                      Collections.singleton("id"));
    }

    @Test
    public void testSagaAddedToCacheOnLoad() throws Exception {
        StubSaga saga = new StubSaga();
        testSubject.insertSaga(StubSaga.class, "id", saga, null, singleton(new AssociationValue("key", "value")));

        ehCache.removeAll();
        reset(sagaCache, associationsCache);

        SagaStore.Entry<StubSaga> actual = testSubject.loadSaga(StubSaga.class, "id");
        assertSame(saga, actual.saga());

        verify(sagaCache).get("id");
        verify(sagaCache).put(eq("id"), any());
        verify(associationsCache, never()).put(any(), any());
    }

    @Test
    public void testCommitDelegatedAfterAddingToCache() throws Exception {
        StubSaga saga = new StubSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        testSubject.insertSaga(StubSaga.class, "123", saga, null, singleton(associationValue));

        verify(associationsCache, never()).put(any(), any());
        verify(mockSagaStore).insertSaga(StubSaga.class, "123", saga, null, singleton(associationValue));
    }
}
