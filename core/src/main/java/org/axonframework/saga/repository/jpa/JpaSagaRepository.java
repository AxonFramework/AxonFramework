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

package org.axonframework.saga.repository.jpa;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaResourceInjector;
import org.axonframework.saga.repository.SagaRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * @author Allard Buijze
 */
public class JpaSagaRepository implements SagaRepository {

    private ConcurrentMap<AssociationValue, Set<String>> lookup = new ConcurrentHashMap<AssociationValue, Set<String>>();
    private ConcurrentMap<String, Saga> sagaCache = new ConcurrentHashMap<String, Saga>();

    @PersistenceContext
    private EntityManager entityManager;

    private SagaResourceInjector injector;

    @Override
    public <T extends Saga> Set<T> find(Class<T> type, AssociationValue associationValue) {
        Set<String> sagaIdentifiers = lookup.get(associationValue);
        Set<T> result = new HashSet<T>();
        if (sagaIdentifiers != null) {
            for (String sagaId : sagaIdentifiers) {
                T cachedSaga = load(type, sagaId);
                result.add(cachedSaga);
            }
        }
        return result;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <T extends Saga> T load(Class<T> type, String sagaIdentifier) {
        Saga cachedSaga = sagaCache.get(sagaIdentifier);
        if (cachedSaga == null) {
            final Saga storedSaga = loadFromBackingStore(sagaIdentifier);
            if (storedSaga == null) {
                return null;
            }
            injector.injectResources(storedSaga);
            cachedSaga = sagaCache.putIfAbsent(storedSaga.getSagaIdentifier(), storedSaga);
            if (cachedSaga == null) {
                cachedSaga = storedSaga;
                storedSaga.getAssociationValues().addChangeListener(
                        new AssociationValueChangeListener(storedSaga.getSagaIdentifier()));
            }
        }
        if (!type.isInstance(cachedSaga)) {
            return null;
        }
        return (T) cachedSaga;
    }

    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    @Override
    public void commit(Saga saga) {
        synchronized (saga) {
            entityManager.merge(SagaEntry.forSaga(saga));
        }
    }

    @Override
    public void add(Saga saga) {
        entityManager.persist(SagaEntry.forSaga(saga));
        for (AssociationValue av : saga.getAssociationValues()) {
            entityManager.persist(new AssociationEntry(saga.getSagaIdentifier(), av));
            cacheAssociationValue(av, saga.getSagaIdentifier());
        }
        entityManager.flush();
    }

    private Saga loadFromBackingStore(String sagaId) {
        SagaEntry entry = entityManager.find(SagaEntry.class, sagaId);
        if (entry == null) {
            return null;
        }
        return entry.getSaga();
    }

    @PostConstruct
    public void initialize() {
        List<AssociationEntry> entries =
                entityManager.createQuery("SELECT ae FROM AssociationEntry ae").getResultList();
        lookup.clear();
        for (AssociationEntry entry : entries) {
            AssociationValue associationValue = entry.getAssociationValue();
            cacheAssociationValue(associationValue, entry.getSagaIdentifier());
        }
    }

    private void cacheAssociationValue(AssociationValue associationValue, String sagaIdentifier) {
        if (!lookup.containsKey(associationValue)) {
            lookup.putIfAbsent(associationValue, new HashSet<String>());
        }
        lookup.get(associationValue).add(sagaIdentifier);
    }

    @Resource
    public void setSagaResourceInjector(SagaResourceInjector injector) {
        this.injector = injector;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private class AssociationValueChangeListener implements AssociationValues.ChangeListener {

        private final String sagaIdentifier;

        public AssociationValueChangeListener(String sagaIdentifier) {
            this.sagaIdentifier = sagaIdentifier;
        }

        @Override
        public void onAssociationValueAdded(AssociationValue newAssociationValue) {
            lookup.putIfAbsent(newAssociationValue, new HashSet<String>());
            lookup.get(newAssociationValue).add(sagaIdentifier);
        }

        @SuppressWarnings({"unchecked"})
        @Override
        public void onAssociationValueRemoved(AssociationValue associationValue) {
            Set<String> associations = lookup.get(associationValue);
            if (associations != null) {
                associations.remove(sagaIdentifier);
            }
            List<AssociationEntry> potentialCandidates = entityManager.createQuery(
                    "SELECT ae FROM AssociationEntry ae WHERE ae.associationKey = :associationKey AND ae.sagaId = :sagaId")
                    .setParameter("associationKey", associationValue.getKey())
                    .setParameter("sagaId", sagaIdentifier)
                    .getResultList();
            for (AssociationEntry entry : potentialCandidates) {
                if (associationValue.getValue().equals(entry.getAssociationValue().getValue())) {
                    entityManager.remove(entry);
                }
            }
        }
    }
}
