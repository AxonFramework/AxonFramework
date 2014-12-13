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

package org.axonframework.saga.repository;

import net.sf.ehcache.CacheManager;
import org.axonframework.cache.Cache;
import org.axonframework.cache.EhCacheAdapter;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;
import org.junit.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CachingSagaRepositoryTest {

    private Cache associationsCache;
    private org.axonframework.cache.Cache sagaCache;
    private SagaRepository repository;
    private CachingSagaRepository testSubject;
    private CacheManager cacheManager;
    private net.sf.ehcache.Cache ehCache;

    @Before
    public void setUp() throws Exception {
        ehCache = new net.sf.ehcache.Cache("test", 100, false, false, 10, 10);
        cacheManager = CacheManager.create();
        cacheManager.addCache(ehCache);
        associationsCache = spy(new EhCacheAdapter(ehCache));
        sagaCache = spy(new EhCacheAdapter(ehCache));
        repository = mock(SagaRepository.class);
        testSubject = new CachingSagaRepository(repository, associationsCache, sagaCache);
    }

    @After
    public void tearDown() throws Exception {
        cacheManager.shutdown();
    }

    @Test
    public void testSagaAddedToCacheOnAdd() throws Exception {
        final StubSaga saga = new StubSaga("id");
        saga.associate("key", "value");
        testSubject.add(saga);

        verify(sagaCache).put("id", saga);
        verify(associationsCache, never()).put(any(), any());
        verify(repository).add(saga);
    }

    @Test
    public void testConcurrentAccessToSagaRepository() {
        final StubSaga saga = new StubSaga("id");
        saga.associate("key", "value");
        testSubject.add(saga);
        testSubject.commit(saga);

        // to make sure this saga is found
        when(repository.find(any(Class.class), any(AssociationValue.class)))
                .thenReturn(new HashSet<>(Arrays.asList(saga.getSagaIdentifier())));

        Set<String> found = testSubject.find(StubSaga.class, new AssociationValue("key", "value"));
        Iterator<String> iterator = found.iterator();

        final StubSaga saga2 = new StubSaga("id");
        saga2.associate("key", "value");
        testSubject.add(saga2);
        testSubject.commit(saga2);

        assertEquals(saga.getSagaIdentifier(), iterator.next());
    }

    @Test
    public void testAssociationsAddedToCacheOnLoad() {
        final StubSaga saga = new StubSaga("id");
        saga.associate("key", "value");
        testSubject.add(saga);
        ehCache.removeAll();
        reset(sagaCache, associationsCache);

        final AssociationValue associationValue = new AssociationValue("key", "value");
        when(repository.find(StubSaga.class, associationValue)).thenReturn(Collections.singleton("id"));

        Set<String> actual = testSubject.find(StubSaga.class, associationValue);
        assertEquals(actual, singleton("id"));
        verify(associationsCache, atLeast(1)).get("org.axonframework.saga.repository.StubSaga/key=value");
        verify(associationsCache).put("org.axonframework.saga.repository.StubSaga/key=value",
                                                  Collections.singleton("id"));
    }

    @Test
    public void testSagaAddedToCacheOnLoad() {
        final StubSaga saga = new StubSaga("id");
        saga.associate("key", "value");
        testSubject.add(saga);
        ehCache.removeAll();

        reset(sagaCache, associationsCache);

        when(repository.load("id")).thenReturn(saga);

        Saga actual = testSubject.load("id");
        assertSame(saga, actual);

        verify(sagaCache).get("id");
        verify(sagaCache).put("id", saga);
        verify(associationsCache, never()).put(any(), any());
    }

    @Test
    public void testCommitDelegatedAfterAddingToCache() {
        final StubSaga saga = new StubSaga("id");
        saga.associate("key", "value");
        testSubject.add(saga);
        ehCache.removeAll();

        saga.associate("new", "id");
        saga.removeAssociationValue("key", "value");
        testSubject.commit(saga);

        verify(repository).commit(saga);
        verify(associationsCache, never()).put(any(), any());
    }
}
