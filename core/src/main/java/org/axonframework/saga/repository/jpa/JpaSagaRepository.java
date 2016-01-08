/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.ResourceInjector;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.AbstractSagaRepository;
import org.axonframework.serializer.JavaSerializer;
import org.axonframework.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityNotFoundException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * JPA implementation of the Saga Repository. It uses an {@link javax.persistence.EntityManager} to persist the actual
 * saga in a backing
 * store.
 * <p/>
 * After each operations that modified the backing store, {@link javax.persistence.EntityManager#flush()} is invoked to
 * ensure the store contains the last modifications. To override this behavior, see {@link }
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class JpaSagaRepository extends AbstractSagaRepository {

    private static final Logger logger = LoggerFactory.getLogger(JpaSagaRepository.class);

    private static final String LOAD_SAGA_QUERY = "SELECT new org.axonframework.saga.repository.jpa.SerializedSaga("
            + "se.serializedSaga, se.sagaType, se.revision) "
            + "FROM SagaEntry se "
            + "WHERE se.sagaId = :sagaId";
    private static final String DELETE_ASSOCIATION_QUERY = "DELETE FROM AssociationValueEntry ae "
            + "WHERE ae.associationKey = :associationKey "
            + "AND ae.associationValue = :associationValue "
            + "AND ae.sagaType = :sagaType "
            + "AND ae.sagaId = :sagaId";
    private static final String FIND_ASSOCIATION_IDS_QUERY = "SELECT ae.sagaId FROM AssociationValueEntry ae "
            + "WHERE ae.associationKey = :associationKey "
            + "AND ae.associationValue = :associationValue "
            + "AND ae.sagaType = :sagaType";
    private static final String DELETE_ASSOCIATIONS_QUERY = "DELETE FROM AssociationValueEntry ae WHERE ae.sagaId = :sagaId";
    private static final String DELETE_SAGA_QUERY = "DELETE FROM SagaEntry se WHERE se.sagaId = :id";
    private static final String UPDATE_SAGA_QUERY = "UPDATE SagaEntry s SET s.serializedSaga = :serializedSaga, s.revision = :revision "
            + "WHERE s.sagaId = :sagaId AND s.sagaType = :sagaType";

    private static final String LOAD_SAGA_NAMED_QUERY = "LOAD_SAGA_NAMED_QUERY";
    private static final String DELETE_ASSOCIATION_NAMED_QUERY = "DELETE_ASSOCIATION_NAMED_QUERY";
    private static final String FIND_ASSOCIATION_IDS_NAMED_QUERY = "FIND_ASSOCIATION_IDS_NAMED_QUERY";
    private static final String DELETE_ASSOCIATIONS_NAMED_QUERY = "DELETE_ASSOCIATIONS_NAMED_QUERY";
    private static final String DELETE_SAGA_NAMED_QUERY = "DELETE_SAGA_NAMED_QUERY";
    private static final String UPDATE_SAGA_NAMED_QUERY = "UPDATE_SAGA_NAMED_QUERY";

    private final EntityManagerProvider entityManagerProvider;
    private ResourceInjector injector;
    private Serializer serializer;
    private volatile boolean useExplicitFlush = true;

    /**
     * Initializes a Saga Repository with a <code>JavaSerializer</code>.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this repository
     */
    public JpaSagaRepository(EntityManagerProvider entityManagerProvider) {
        Assert.notNull(entityManagerProvider, "entityManagerProvider may not be null");
        this.entityManagerProvider = entityManagerProvider;
        serializer = new JavaSerializer();

        EntityManager entityManager = this.entityManagerProvider.getEntityManager();
        EntityManagerFactory entityManagerFactory = entityManager.getEntityManagerFactory();
        entityManagerFactory.addNamedQuery(LOAD_SAGA_NAMED_QUERY, entityManager.createQuery(LOAD_SAGA_QUERY));
        entityManagerFactory.addNamedQuery(DELETE_ASSOCIATION_NAMED_QUERY, entityManager.createQuery(DELETE_ASSOCIATION_QUERY));
        entityManagerFactory.addNamedQuery(FIND_ASSOCIATION_IDS_NAMED_QUERY, entityManager.createQuery(FIND_ASSOCIATION_IDS_QUERY));
        entityManagerFactory.addNamedQuery(DELETE_ASSOCIATIONS_NAMED_QUERY, entityManager.createQuery(DELETE_ASSOCIATIONS_QUERY));
        entityManagerFactory.addNamedQuery(DELETE_SAGA_NAMED_QUERY, entityManager.createQuery(DELETE_SAGA_QUERY));
        entityManagerFactory.addNamedQuery(UPDATE_SAGA_NAMED_QUERY, entityManager.createQuery(UPDATE_SAGA_QUERY));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Saga load(String sagaId) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        List<SerializedSaga> serializedSagaList = (List<SerializedSaga>) entityManager
                .createNamedQuery(LOAD_SAGA_NAMED_QUERY)
                .setParameter("sagaId", sagaId)
                .setMaxResults(1)
                .getResultList();
        if (serializedSagaList == null || serializedSagaList.isEmpty()) {
            return null;
        }
        SerializedSaga serializedSaga = serializedSagaList.get(0);
        Saga loadedSaga = serializer.deserialize(serializedSaga);
        if (injector != null) {
            injector.injectResources(loadedSaga);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded saga id [{}] of type [{}]", sagaId, loadedSaga.getClass().getName());
        }
        return loadedSaga;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    protected void removeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        int updateCount = entityManager.createNamedQuery(DELETE_ASSOCIATION_NAMED_QUERY)
                .setParameter("associationKey", associationValue.getKey())
                .setParameter("associationValue", associationValue.getValue())
                .setParameter("sagaType", sagaType)
                .setParameter("sagaId", sagaIdentifier)
                .executeUpdate();
        if (updateCount == 0 && logger.isWarnEnabled()) {
            logger.warn("Wanted to remove association value, but it was already gone: sagaId= {}, key={}, value={}",
                    sagaIdentifier, associationValue.getKey(), associationValue.getValue());
        }
    }

    @Override
    protected String typeOf(Class<? extends Saga> sagaClass) {
        return serializer.typeForClass(sagaClass).getName();
    }

    @Override
    protected void storeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        entityManager.persist(new AssociationValueEntry(sagaType, sagaIdentifier, associationValue));
        if (useExplicitFlush) {
            entityManager.flush();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Set<String> findAssociatedSagaIdentifiers(Class<? extends Saga> type, AssociationValue associationValue) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        List<String> entries =
                entityManager.createNamedQuery(FIND_ASSOCIATION_IDS_NAMED_QUERY)
                        .setParameter("associationKey", associationValue.getKey())
                        .setParameter("associationValue", associationValue.getValue())
                        .setParameter("sagaType", typeOf(type))
                        .getResultList();
        return new TreeSet<>(entries);
    }

    @Override
    protected void deleteSaga(Saga saga) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        try {
            entityManager.createNamedQuery(DELETE_ASSOCIATIONS_NAMED_QUERY)
                    .setParameter("sagaId", saga.getSagaIdentifier())
                    .executeUpdate();
            entityManager.createNamedQuery(DELETE_SAGA_NAMED_QUERY)
                    .setParameter("id", saga.getSagaIdentifier())
                    .executeUpdate();
        } catch (EntityNotFoundException e) {
            logger.info("Could not delete SagaEntry {}, it appears to have already been deleted.",
                    saga.getSagaIdentifier());
        }
        entityManager.flush();
    }

    @Override
    protected void updateSaga(Saga saga) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        SagaEntry entry = new SagaEntry(saga, serializer);
        if (logger.isDebugEnabled()) {
            logger.debug("Updating saga id {} as {}", saga.getSagaIdentifier(), new String(entry.getSerializedSaga(),
                    Charset.forName("UTF-8")));
        }
        if (useExplicitFlush) {
            entityManager.flush();
        }
        int updateCount = entityManager.createNamedQuery(UPDATE_SAGA_NAMED_QUERY)
                .setParameter("serializedSaga", entry.getSerializedSaga())

                .setParameter("revision", entry.getRevision())
                .setParameter("sagaId", entry.getSagaId())
                .setParameter("sagaType", entry.getSagaType())
                .executeUpdate();
        if (updateCount == 0) {
            logger.warn("Expected to be able to update a Saga instance, but no rows were found. Inserting instead.");
            entityManager.persist(entry);
            if (useExplicitFlush) {
                entityManager.flush();
            }
        }
    }

    @Override
    protected void storeSaga(Saga saga) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        SagaEntry entry = new SagaEntry(saga, serializer);
        entityManager.persist(entry);
        if (logger.isDebugEnabled()) {
            logger.debug("Storing saga id {} as {}", saga.getSagaIdentifier(), new String(entry.getSerializedSaga(),
                    Charset.forName("UTF-8")));
        }
        if (useExplicitFlush) {
            entityManager.flush();
        }
    }

    /**
     * Sets the ResourceInjector to use to inject Saga instances with any (temporary) resources they might need. These
     * are typically the resources that could not be persisted with the Saga.
     *
     * @param resourceInjector The resource injector
     */
    public void setResourceInjector(ResourceInjector resourceInjector) {
        this.injector = resourceInjector;
    }

    /**
     * Sets the Serializer instance to serialize Sagas with. Defaults to the XStream Serializer.
     *
     * @param serializer the Serializer instance to serialize Sagas with
     */
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Sets whether or not to do an explicit {@link javax.persistence.EntityManager#flush()} after each data modifying
     * operation on the backing storage. Default to <code>true</code>
     *
     * @param useExplicitFlush <code>true</code> to force flush, <code>false</code> otherwise.
     */
    public void setUseExplicitFlush(boolean useExplicitFlush) {
        this.useExplicitFlush = useExplicitFlush;
    }
}
