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

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.NoSuchSagaException;
import org.axonframework.saga.ResourceInjector;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.AbstractSagaRepository;
import org.axonframework.serializer.JavaSerializer;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;

import java.util.List;
import java.util.Set;
import javax.annotation.Resource;
import javax.persistence.EntityManager;

/**
 * JPA implementation of the Saga Repository. It uses an {@link EntityManager} to persist the actual saga in a backing
 * store.
 * <p/>
 * After each operations that modified the backing store, {@link javax.persistence.EntityManager#flush()} is invoked to
 * ensure the store contains the last modifications. To override this behavior, see {@link }
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class JpaSagaRepository extends AbstractSagaRepository {

    private final EntityManagerProvider entityManagerProvider;
    private ResourceInjector injector;
    private Serializer serializer;
    private volatile boolean useExplicitFlush = true;
    private volatile boolean initialized = false;

    /**
     * Initializes a Saga Repository with a <code>JavaSerializer</code>.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this repository
     */
    public JpaSagaRepository(EntityManagerProvider entityManagerProvider) {
        this.entityManagerProvider = entityManagerProvider;
        serializer = new JavaSerializer();
    }

    @Override
    public <T extends Saga> Set<T> find(Class<T> type, Set<AssociationValue> associationValues) {
        if (!initialized) {
            initialize();
        }
        return super.find(type, associationValues);
    }

    @Override
    public void add(Saga saga) {
        if (!initialized) {
            initialize();
        }
        super.add(saga);
    }

    @Override
    public void commit(Saga saga) {
        if (!initialized) {
            initialize();
        }
        super.commit(saga);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    protected void removeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        List<AssociationValueEntry> potentialCandidates = entityManager.createQuery(
                "SELECT ae FROM AssociationValueEntry ae "
                        + "WHERE ae.associationKey = :associationKey "
                        + "AND ae.sagaType = :sagaType "
                        + "AND  ae.sagaId = :sagaId")
                                                                       .setParameter("associationKey",
                                                                                     associationValue.getKey())
                                                                       .setParameter("sagaType", sagaType)
                                                                       .setParameter("sagaId", sagaIdentifier)
                                                                       .getResultList();
        for (AssociationValueEntry entry : potentialCandidates) {
            if (associationValue.getValue().equals(entry.getAssociationValue().getValue())) {
                entityManager.remove(entry);
            }
        }
        if (useExplicitFlush) {
            entityManager.flush();
        }
    }

    @Override
    protected void storeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        entityManager.persist(new AssociationValueEntry(sagaType, sagaIdentifier, associationValue));
        if (useExplicitFlush) {
            entityManager.flush();
        }
    }

    @Override
    protected <T extends Saga> T loadSaga(Class<T> type, String sagaId) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        SagaEntry entry = entityManager.find(SagaEntry.class, sagaId);
        if (entry == null) {
            throw new NoSuchSagaException(type, sagaId);
        }
        Saga loadedSaga = entry.getSaga(serializer);
        if (!type.isInstance(loadedSaga)) {
            return null;
        }
        T storedSaga = type.cast(loadedSaga);
        if (injector != null) {
            injector.injectResources(storedSaga);
        }
        return storedSaga;
    }

    @Override
    protected void deleteSaga(Saga saga) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        entityManager.flush();
        entityManager.createQuery("DELETE FROM SagaEntry se WHERE se.sagaId = :sagaId")
                     .setParameter("sagaId", saga.getSagaIdentifier())
                     .executeUpdate();
        entityManager.createQuery("DELETE FROM AssociationValueEntry ae WHERE ae.sagaId = :sagaId")
                     .setParameter("sagaId", saga.getSagaIdentifier())
                     .executeUpdate();
    }

    @Override
    protected void updateSaga(Saga saga) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        SerializedObject<byte[]> serializedSaga = serializer.serialize(saga, byte[].class);
        int updates = entityManager.createQuery(
                "UPDATE SagaEntry se SET se.serializedSaga = :serializedSaga, se"
                        + ".sagaType = :sagaType, "
                        + "se.revision = :revision WHERE se.sagaId = :sagaId")
                                   .setParameter("sagaId", saga.getSagaIdentifier())
                                   .setParameter("serializedSaga", serializedSaga.getData())
                                   .setParameter("sagaType", serializedSaga.getType().getName())
                                   .setParameter("revision", serializedSaga.getType().getRevision())
                                   .executeUpdate();
        if (updates == 0) {
            storeSaga(saga);
        } else if (useExplicitFlush) {
            entityManager.flush();
        }
    }

    @Override
    protected void storeSaga(Saga saga) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        entityManager.persist(new SagaEntry(saga, serializer));
        if (useExplicitFlush) {
            entityManager.flush();
        }
    }

    /**
     * Initializes the repository by loading all AssociationValues in memory. Failure to initialize properly might
     * result in Saga instance not being found based on their <code>AssociationValue</code>s.
     */
    @SuppressWarnings({"unchecked"})
    private synchronized void initialize() {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        if (!initialized) {
            List<AssociationValueEntry> entries =
                    entityManager.createQuery("SELECT ae FROM AssociationValueEntry ae").getResultList();
            getAssociationValueMap().clear();
            for (AssociationValueEntry entry : entries) {
                AssociationValue associationValue = entry.getAssociationValue();
                getAssociationValueMap().add(associationValue, entry.getSagaType(), entry.getSagaIdentifier());
            }
            initialized = true;
        }
    }

    /**
     * Sets the ResourceInjector to use to inject Saga instances with any (temporary) resources they might need. These
     * are typically the resources that could not be persisted with the Saga.
     *
     * @param resourceInjector The resource injector
     */
    @Resource
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
