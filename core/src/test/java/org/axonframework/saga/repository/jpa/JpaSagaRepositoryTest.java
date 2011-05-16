/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.saga.repository.jpa;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.NoSuchSagaException;
import org.axonframework.saga.SagaStorageException;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.repository.XStreamSagaSerializer;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.axonframework.util.TestUtils.setOf;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/META-INF/spring/saga-repository-test.xml")
@Transactional
public class JpaSagaRepositoryTest {

    @Autowired
    private JpaSagaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Before
    public void setUp() {
        entityManager.clear();
        entityManager.createQuery("DELETE FROM SagaEntry");
        entityManager.createQuery("DELETE FROM AssociationValueEntry");
        repository.initialize();
        repository.setSerializer(new XStreamSagaSerializer());
    }

    @Rollback
    @Test
    public void testAddAndLoadSaga_ByIdentifier() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        repository.add(saga);
        MyTestSaga loaded = repository.load(MyTestSaga.class, identifier);
        assertEquals(identifier, loaded.getSagaIdentifier());
        assertSame(loaded, saga);
        assertNotNull(entityManager.find(SagaEntry.class, identifier));
    }

    @Rollback
    @Test
    public void testAddAndLoadSaga_ByAssociationValue() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        repository.add(saga);
        Set<MyTestSaga> loaded = repository.find(MyTestSaga.class, setOf(new AssociationValue("key", "value")));
        assertEquals(1, loaded.size());
        MyTestSaga loadedSaga = loaded.iterator().next();
        assertEquals(identifier, loadedSaga.getSagaIdentifier());
        assertSame(loadedSaga, saga);
        assertNotNull(entityManager.find(SagaEntry.class, identifier));
    }

    @Rollback
    @Test
    public void testAddAndLoadSaga_MultipleHitsByAssociationValue() {
        String identifier1 = UUID.randomUUID().toString();
        String identifier2 = UUID.randomUUID().toString();
        MyTestSaga saga1 = new MyTestSaga(identifier1);
        MyOtherTestSaga saga2 = new MyOtherTestSaga(identifier2);
        saga1.registerAssociationValue(new AssociationValue("key", "value"));
        saga2.registerAssociationValue(new AssociationValue("key", "value"));
        repository.add(saga1);
        repository.add(saga2);

        // we attempt to force a cache cleanup to reproduce a problem found on production
        saga1 = null;
        saga2 = null;
        System.gc();
        repository.purgeCache();

        // load saga1
        Set<MyTestSaga> loaded1 = repository.find(MyTestSaga.class, setOf(new AssociationValue("key", "value")));
        assertEquals(1, loaded1.size());
        MyTestSaga loadedSaga1 = loaded1.iterator().next();
        assertEquals(identifier1, loadedSaga1.getSagaIdentifier());
        assertNotNull(entityManager.find(SagaEntry.class, identifier1));

        // load saga2
        Set<MyOtherTestSaga> loaded2 = repository.find(MyOtherTestSaga.class, setOf(new AssociationValue("key",
                                                                                                         "value")));
        assertEquals(1, loaded2.size());
        MyOtherTestSaga loadedSaga2 = loaded2.iterator().next();
        assertEquals(identifier2, loadedSaga2.getSagaIdentifier());
        assertNotNull(entityManager.find(SagaEntry.class, identifier2));
    }

    @Test
    public void testAddAndLoadSaga_AssociateValueAfterStorage() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        repository.add(saga);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        Set<MyTestSaga> loaded = repository.find(MyTestSaga.class, setOf(new AssociationValue("key", "value")));
        assertEquals(1, loaded.size());
        MyTestSaga loadedSaga = loaded.iterator().next();
        assertEquals(identifier, loadedSaga.getSagaIdentifier());
        assertSame(loadedSaga, saga);
        assertNotNull(entityManager.find(SagaEntry.class, identifier));
    }

    @Test
    public void testLoadUncachedSaga_ByIdentifier() {
        repository.setSerializer(new XStreamSagaSerializer());
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        entityManager.persist(new SagaEntry(saga, new XStreamSagaSerializer()));
        MyTestSaga loaded = repository.load(MyTestSaga.class, identifier);
        assertNotSame(saga, loaded);
        assertEquals(identifier, loaded.getSagaIdentifier());
    }

    @Test
    public void testLoadUncachedSaga_ByAssociationValue() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        entityManager.persist(new SagaEntry(saga, new XStreamSagaSerializer()));
        entityManager.persist(new AssociationValueEntry(identifier, new AssociationValue("key", "value")));
        entityManager.flush();
        entityManager.clear();
        repository.initialize();
        Set<MyTestSaga> loaded = repository.find(MyTestSaga.class, setOf(new AssociationValue("key", "value")));
        assertEquals(1, loaded.size());
        MyTestSaga loadedSaga = loaded.iterator().next();
        assertEquals(identifier, loadedSaga.getSagaIdentifier());
        assertNotSame(loadedSaga, saga);
        assertNotNull(entityManager.find(SagaEntry.class, identifier));
    }

    @Test(expected = NoSuchSagaException.class)
    public void testLoadSaga_NotFound() {
        repository.load(MyTestSaga.class, "123456");
    }

    @Test
    public void testLoadSaga_AssociationValueRemoved() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        entityManager.persist(new SagaEntry(saga, new XStreamSagaSerializer()));
        entityManager.persist(new AssociationValueEntry(identifier, new AssociationValue("key", "value")));
        entityManager.flush();
        entityManager.clear();
        repository.initialize();
        MyTestSaga loaded = repository.load(MyTestSaga.class, identifier);
        loaded.removeAssociationValue("key", "value");
        Set<MyTestSaga> found = repository.find(MyTestSaga.class, setOf(new AssociationValue("key", "value")));
        assertEquals(0, found.size());
    }

    @Test
    public void testSaveSaga() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        entityManager.persist(new SagaEntry(saga, new XStreamSagaSerializer()));
        MyTestSaga loaded = repository.load(MyTestSaga.class, identifier);
        loaded.counter = 1;
        repository.commit(loaded);

        SagaEntry entry = entityManager.find(SagaEntry.class, identifier);
        MyTestSaga actualSaga = (MyTestSaga) entry.getSaga(new XStreamSagaSerializer());
        assertNotSame(loaded, actualSaga);
        assertEquals(1, actualSaga.counter);
    }

    @Test(expected = SagaStorageException.class)
    public void testStoreAssociationValue_NotSerializable() {
        String identifier = UUID.randomUUID().toString();
        MyTestSaga saga = new MyTestSaga(identifier);
        saga.registerAssociationValue(new AssociationValue("key", new Object()));
        repository.add(saga);
    }

    public static class MyTestSaga extends AbstractAnnotatedSaga {

        private static final long serialVersionUID = -1562911263884220240L;
        private int counter = 0;

        public MyTestSaga(String identifier) {
            super(identifier);
        }

        public void registerAssociationValue(AssociationValue associationValue) {
            associateWith(associationValue);
        }

        public void removeAssociationValue(String key, String value) {
            removeAssociationWith(key, value);
        }
    }

    public static class MyOtherTestSaga extends AbstractAnnotatedSaga {
        private static final long serialVersionUID = -1562911263884220240L;
        private int counter = 0;

        public MyOtherTestSaga(String identifier) {
            super(identifier);
        }

        public void registerAssociationValue(AssociationValue associationValue) {
            associateWith(associationValue);
        }

        public void removeAssociationValue(String key, String value) {
            removeAssociationWith(key, value);
        }
    }
}
