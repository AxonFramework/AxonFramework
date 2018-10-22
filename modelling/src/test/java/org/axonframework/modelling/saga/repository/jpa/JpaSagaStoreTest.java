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

package org.axonframework.modelling.saga.repository.jpa;

import org.axonframework.common.Assert;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.Saga;
import org.axonframework.modelling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.modelling.saga.repository.StubSaga;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Set;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration(locations = "/META-INF/spring/saga-repository-test.xml")
@Transactional
public class JpaSagaStoreTest {

    private AnnotatedSagaRepository<StubSaga> repository;

    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager entityManager;
    private DefaultUnitOfWork<Message<?>> unitOfWork;

    @Before
    public void setUp() {
        JpaSagaStore sagaStore = JpaSagaStore.builder()
                                             .entityManagerProvider(new SimpleEntityManagerProvider(entityManager))
                                             .build();
        repository = AnnotatedSagaRepository.<StubSaga>builder().sagaType(StubSaga.class).sagaStore(sagaStore).build();

        entityManager.clear();
        entityManager.createQuery("DELETE FROM SagaEntry");
        entityManager.createQuery("DELETE FROM AssociationValueEntry");

        // the serialized form of the Saga exceeds the default length of a blob.
        // So we must alter the table to prevent data truncation
        entityManager.createNativeQuery("ALTER TABLE SagaEntry ALTER COLUMN serializedSaga VARBINARY(1024)")
                     .executeUpdate();

        startUnitOfWork();
    }

    protected void startUnitOfWork() {
        Assert.isTrue(unitOfWork == null || !unitOfWork.isActive(),
                      () -> "Cannot start unit of work. There is one still active.");
        unitOfWork = DefaultUnitOfWork.startAndGet(null);
        TransactionStatus tx = txManager.getTransaction(new DefaultTransactionDefinition());
        unitOfWork.onRollback(u -> txManager.rollback(tx));
        unitOfWork.onCommit(u -> txManager.commit(tx));
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @DirtiesContext
    @Test
    public void testAddingAnInactiveSagaDoesntStoreIt() throws Exception {
        unitOfWork.executeWithResult(() -> {
            Saga<StubSaga> saga = repository.createInstance(IdentifierFactory.getInstance().generateIdentifier(),
                                                            StubSaga::new);
            saga.execute(testSaga -> {
                testSaga.registerAssociationValue(new AssociationValue("key", "value"));
                testSaga.end();
            });
            return null;
        });

        entityManager.flush();
        entityManager.clear();

        long result = entityManager.createQuery("select count(*) from SagaEntry", Long.class).getSingleResult();
        assertEquals(0L, result);
    }


    @DirtiesContext
    @Test
    public void testAddAndLoadSaga_ByIdentifier() throws Exception {
        String identifier = unitOfWork.executeWithResult(() -> repository.createInstance(
                IdentifierFactory.getInstance().generateIdentifier(), StubSaga::new).getSagaIdentifier())
                .getPayload();
        entityManager.clear();
        startUnitOfWork();
        unitOfWork.execute(() -> {
            Saga loaded = repository.load(identifier);
            assertEquals(identifier, loaded.getSagaIdentifier());
            assertNotNull(entityManager.find(SagaEntry.class, identifier));
        });
    }

    @DirtiesContext
    @Test
    public void testAddAndLoadSaga_ByAssociationValue() throws Exception {
        String identifier = unitOfWork.executeWithResult(() -> {
            Saga<StubSaga> saga = repository.createInstance(IdentifierFactory.getInstance().generateIdentifier(),
                                                            StubSaga::new);
            saga.execute(s -> s.associate("key", "value"));
            return saga.getSagaIdentifier();
        }).getPayload();
        entityManager.clear();
        startUnitOfWork();
        unitOfWork.execute(() -> {
            Set<String> loaded = repository.find(new AssociationValue("key", "value"));
            assertEquals(1, loaded.size());
            Saga loadedSaga = repository.load(loaded.iterator().next());
            assertEquals(identifier, loadedSaga.getSagaIdentifier());
            assertNotNull(entityManager.find(SagaEntry.class, identifier));
        });
    }


    public void testLoadSaga_NotFound() {
        unitOfWork.execute(() -> assertNull(repository.load("123456")));
    }

    @DirtiesContext
    @Test
    public void testLoadSaga_AssociationValueRemoved() throws Exception {
        String identifier = unitOfWork.executeWithResult(() -> {
            Saga<StubSaga> saga = repository.createInstance(IdentifierFactory.getInstance().generateIdentifier(),
                                                            StubSaga::new);
            saga.execute(s -> s.associate("key", "value"));
            return saga.getSagaIdentifier();
        }).getPayload();
        entityManager.clear();
        startUnitOfWork();
        unitOfWork.execute(() -> {
            Saga<StubSaga> loaded = repository.load(identifier);
            loaded.execute(s -> s.removeAssociationValue("key", "value"));
        });
        entityManager.clear();
        startUnitOfWork();
        Set<String> found = unitOfWork.executeWithResult(() -> repository.find(new AssociationValue("key", "value")))
                .getPayload();
        assertEquals(0, found.size());
    }

    @DirtiesContext
    @Test
    public void testEndSaga() throws Exception {
        String identifier = unitOfWork.executeWithResult(() -> {
            Saga<StubSaga> saga = repository.createInstance(IdentifierFactory.getInstance().generateIdentifier(),
                                                            StubSaga::new);
            saga.execute(s -> s.associate("key", "value"));
            return saga.getSagaIdentifier();
        }).getPayload();
        entityManager.clear();
        assertFalse(entityManager.createQuery("SELECT ae FROM AssociationValueEntry ae WHERE ae.sagaId = :id")
                                 .setParameter("id", identifier).getResultList().isEmpty());
        startUnitOfWork();
        unitOfWork.execute(() -> {
            Saga<StubSaga> loaded = repository.load(identifier);
            loaded.execute(StubSaga::end);
        });
        entityManager.clear();

        assertNull(entityManager.find(SagaEntry.class, identifier));
        assertTrue(entityManager.createQuery("SELECT ae FROM AssociationValueEntry ae WHERE ae.sagaId = :id")
                                .setParameter("id", identifier).getResultList().isEmpty());
    }

    @DirtiesContext
    @Test
    public void testStoreSagaWithCustomEntity() throws Exception {
        JpaSagaStore sagaStore = new JpaSagaStore(
                JpaSagaStore.builder().entityManagerProvider(new SimpleEntityManagerProvider(entityManager))
        ) {
            @Override
            protected AbstractSagaEntry<?> createSagaEntry(Object saga, String sagaIdentifier, Serializer serializer) {
                return new CustomSagaEntry(saga, sagaIdentifier, serializer);
            }

            @Override
            protected String sagaEntryEntityName() {
                return CustomSagaEntry.class.getSimpleName();
            }

            @Override
            protected Class<? extends SimpleSerializedObject<?>> serializedObjectType() {
                return CustomSerializedSaga.class;
            }
        };

        repository = AnnotatedSagaRepository.<StubSaga>builder().sagaType(StubSaga.class).sagaStore(sagaStore).build();

        String identifier = unitOfWork.executeWithResult(() -> repository.createInstance(
                IdentifierFactory.getInstance().generateIdentifier(), StubSaga::new).getSagaIdentifier())
                .getPayload();

        assertFalse(entityManager.createQuery("SELECT e FROM CustomSagaEntry e").getResultList().isEmpty());

        entityManager.clear();

        startUnitOfWork();
        unitOfWork.execute(() -> {
            Saga<StubSaga> loaded = repository.load(identifier);
            loaded.execute(StubSaga::end);
            assertNotNull(entityManager.find(CustomSagaEntry.class, identifier));
        });
    }
}
