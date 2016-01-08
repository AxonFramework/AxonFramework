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

package org.axonframework.saga.repository.jpa;

import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;
import org.axonframework.saga.Saga;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.repository.StubSaga;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/META-INF/spring/saga-repository-test.xml")
@Transactional
public class JpaSagaRepositoryTest {

    private JpaSagaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;
    private XStreamSerializer serializer;

    @Before
    public void setUp() {
        repository = new JpaSagaRepository(new SimpleEntityManagerProvider(entityManager));

        entityManager.clear();
        entityManager.createQuery("DELETE FROM SagaEntry");
        entityManager.createQuery("DELETE FROM AssociationValueEntry");

        // the serialized form of the Saga exceeds the default length of a blob.
        // So we must alter the table to prevent data truncation
        entityManager.createNativeQuery("ALTER TABLE SagaEntry ALTER COLUMN serializedSaga VARBINARY(1024)")
                     .executeUpdate();
        serializer = new XStreamSerializer();
        repository.setSerializer(serializer);
    }

    @DirtiesContext
    @Test
    public void testAddingAnInactiveSagaDoesntStoreIt() {
        StubSaga testSaga = new StubSaga("test1");
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        testSaga.end();

        repository.add(testSaga);
        entityManager.flush();
        entityManager.clear();
        Set<String> actual = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(0, actual.size());
        Object actualSaga = repository.load("test1");
        assertNull(actualSaga);
    }


    @DirtiesContext
    @Test
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_SagaFound() {
        StubSaga testSaga = new StubSaga("test1");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga("test2");
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        otherTestSaga.registerAssociationValue(new AssociationValue("key", "value"));
        repository.add(testSaga);
        repository.add(otherTestSaga);
        entityManager.flush();
        entityManager.clear();
        Set<String> actual = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(1, actual.size());
        assertEquals("test1", actual.iterator().next());
    }

    @DirtiesContext
    @Test
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_NoSagaFound() {
        StubSaga testSaga = new StubSaga("test1");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga("test2");
        repository.add(testSaga);
        repository.add(otherTestSaga);
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        otherTestSaga.registerAssociationValue(new AssociationValue("key", "value"));
        entityManager.flush();
        entityManager.clear();
        Set<String> actual = repository.find(InexistentSaga.class, new AssociationValue("key", "value"));
        assertTrue("Didn't expect any sagas", actual.isEmpty());
    }

    @Test
    @DirtiesContext
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_SagaDeleted() {
        StubSaga testSaga = new StubSaga("test1");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga("test2");
        repository.add(testSaga);
        repository.add(otherTestSaga);
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        otherTestSaga.registerAssociationValue(new AssociationValue("key", "value"));
        testSaga.end();
        repository.commit(testSaga);
        entityManager.flush();
        entityManager.clear();
        Set<String> actual = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertTrue("Didn't expect any sagas", actual.isEmpty());
    }

    @DirtiesContext
    @Test
    public void testAddAndLoadSaga_ByIdentifier() {
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        repository.add(saga);
        Saga loaded = repository.load(identifier);
        assertEquals(identifier, loaded.getSagaIdentifier());
        assertNotNull(entityManager.find(SagaEntry.class, identifier));
    }

    @DirtiesContext
    @Test
    public void testAddAndLoadSaga_ByAssociationValue() {
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        repository.add(saga);
        Set<String> loaded = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(1, loaded.size());
        Saga loadedSaga = repository.load(loaded.iterator().next());
        assertEquals(identifier, loadedSaga.getSagaIdentifier());
        assertNotNull(entityManager.find(SagaEntry.class, identifier));
    }

    @Test
    @DirtiesContext
    public void testAddAndLoadSaga_AssociateValueAfterStorage() {
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        repository.add(saga);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        repository.commit(saga);
        Set<String> loaded = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(1, loaded.size());
        Saga loadedSaga = repository.load(loaded.iterator().next());
        assertEquals(identifier, loadedSaga.getSagaIdentifier());
        assertNotNull(entityManager.find(SagaEntry.class, identifier));
    }

    @DirtiesContext
    @Test
    public void testLoadUncachedSaga_ByIdentifier() {
        repository.setSerializer(new XStreamSerializer());
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        entityManager.persist(new SagaEntry(saga, new XStreamSerializer()));
        entityManager.flush();
        entityManager.clear();
        Saga loaded = repository.load(identifier);
        assertNotSame(saga, loaded);
        assertEquals(identifier, loaded.getSagaIdentifier());
    }

    @DirtiesContext
    @Test
    public void testLoadUncachedSaga_ByAssociationValue() {
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        entityManager.persist(new SagaEntry(saga, serializer));
        entityManager.persist(new AssociationValueEntry(serializer.typeForClass(saga.getClass()).getName(),
                                                        identifier, new AssociationValue("key", "value")));
        entityManager.flush();
        entityManager.clear();
        Set<String> loaded = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(1, loaded.size());
        Saga loadedSaga = repository.load(loaded.iterator().next());
        assertEquals(identifier, loadedSaga.getSagaIdentifier());
        assertNotSame(loadedSaga, saga);
        assertNotNull(entityManager.find(SagaEntry.class, identifier));
    }

    public void testLoadSaga_NotFound() {
        assertNull(repository.load("123456"));
    }

    @DirtiesContext
    @Test
    public void testLoadSaga_AssociationValueRemoved() {
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        entityManager.persist(new SagaEntry(saga, serializer));
        entityManager.persist(new AssociationValueEntry(serializer.typeForClass(saga.getClass()).getName(),
                                                        identifier, new AssociationValue("key", "value")));
        entityManager.flush();
        entityManager.clear();
        StubSaga loaded = (StubSaga) repository.load(identifier);
        loaded.removeAssociationValue("key", "value");
        repository.commit(loaded);
        Set<String> found = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(0, found.size());
    }

    @DirtiesContext
    @Test
    public void testSaveSaga() {
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        entityManager.persist(new SagaEntry(saga, new XStreamSerializer()));
        StubSaga loaded = (StubSaga) repository.load(identifier);
        repository.commit(loaded);

        entityManager.clear();

        SagaEntry entry = entityManager.find(SagaEntry.class, identifier);
        StubSaga actualSaga = (StubSaga) entry.getSaga(new XStreamSerializer());
        assertNotSame(loaded, actualSaga);
    }

    @DirtiesContext
    @Test
    public void testEndSaga() {
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        saga.associate("key", "value");
        repository.add(saga);
        entityManager.flush();
        assertFalse(entityManager.createQuery("SELECT ae FROM AssociationValueEntry ae WHERE ae.sagaId = :id")
                                 .setParameter("id", identifier)
                                 .getResultList().isEmpty());
        StubSaga loaded = (StubSaga) repository.load(identifier);
        loaded.end();
        repository.commit(loaded);

        entityManager.clear();

        assertNull(entityManager.find(SagaEntry.class, identifier));
        assertTrue(entityManager.createQuery("SELECT ae FROM AssociationValueEntry ae WHERE ae.sagaId = :id")
                                .setParameter("id", identifier)
                                .getResultList().isEmpty());
    }

    public static class MyOtherTestSaga extends AbstractAnnotatedSaga {

        private static final long serialVersionUID = -1562911263884220240L;

        public MyOtherTestSaga(String identifier) {
            super(identifier);
        }

        public void registerAssociationValue(AssociationValue associationValue) {
            associateWith(associationValue);
        }
    }

    private class InexistentSaga implements Saga {

        @Override
        public String getSagaIdentifier() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public AssociationValues getAssociationValues() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public void handle(EventMessage event) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public boolean isActive() {
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }
}
