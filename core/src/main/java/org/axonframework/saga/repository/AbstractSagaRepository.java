/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga.repository;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract implementation for saga repositories. This (partial) implementation will take care of the uniqueness of saga
 * instances in the JVM. That means it will prevent multiple instances of the same conceptual Saga (i.e. with same
 * identifier) to exist within the JVM.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractSagaRepository implements SagaRepository {

    private final AssociationValueMap associationValueMap = new AssociationValueMap();
    private final SagaCache sagaCache = new SagaCache();

    @Override
    public <T extends Saga> Set<T> find(Class<T> type, Set<AssociationValue> associationValues) {
        Set<String> sagaIdentifiers = new HashSet<String>();
        Set<T> result = new HashSet<T>();
        for (AssociationValue associationValue : associationValues) {
            Set<String> identifiers = associationValueMap.findSagas(associationValue);
            if (identifiers != null) {
                sagaIdentifiers.addAll(identifiers);
            }
        }
        if (!sagaIdentifiers.isEmpty()) {
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
            final Saga storedSaga = loadSaga(sagaIdentifier);
            if (storedSaga == null) {
                return null;
            }
            cachedSaga = sagaCache.put(storedSaga);
            if (cachedSaga == storedSaga) {
                cachedSaga.getAssociationValues().addChangeListener(
                        new AssociationValueChangeListener(cachedSaga.getSagaIdentifier()));
            }
        }
        if (!type.isInstance(cachedSaga)) {
            return null;
        }
        return (T) cachedSaga;
    }

    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    @Override
    public void add(Saga saga) {
        synchronized (saga) {
            Saga cachedSaga = sagaCache.put(saga);
            if (cachedSaga == saga) {
                for (AssociationValue av : saga.getAssociationValues()) {
                    associationValueMap.add(av, saga.getSagaIdentifier());
                    storeAssociationValue(av, saga.getSagaIdentifier());
                }
                storeSaga(saga);
            }
        }
    }

    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter"})
    @Override
    public void commit(Saga saga) {
        synchronized (saga) {
            updateSaga(saga);
        }
    }

    protected abstract Saga loadSaga(String sagaIdentifier);

    protected abstract void updateSaga(Saga saga);

    protected abstract void storeSaga(Saga saga);

    protected abstract void storeAssociationValue(AssociationValue newAssociationValue, String sagaIdentifier);

    protected abstract void removeAssociationValue(AssociationValue associationValue, String sagaIdentifier);

    /**
     * Returns the AssociationValueMap containing the mappings of AssociationValue to Saga.
     *
     * @return the AssociationValueMap containing the mappings of AssociationValue to Saga
     */
    protected AssociationValueMap getAssociationValueMap() {
        return associationValueMap;
    }

    /**
     * Returns the SagaCache used to prevent multiple instances of the same conceptual Saga (i.e. with same identifier)
     * from being active in the JVM.
     *
     * @return the saga cache
     */
    protected SagaCache getSagaCache() {
        return sagaCache;
    }

    /**
     * Remove all elements from the cache pointing to Saga instances that have been garbage collected.
     */
    public void purgeCache() {
        sagaCache.purge();
    }

    private class AssociationValueChangeListener implements AssociationValues.ChangeListener {

        private final String sagaIdentifier;

        public AssociationValueChangeListener(String sagaIdentifier) {
            this.sagaIdentifier = sagaIdentifier;
        }

        @Override
        public void onAssociationValueAdded(AssociationValue newAssociationValue) {
            associationValueMap.add(newAssociationValue, sagaIdentifier);
            storeAssociationValue(newAssociationValue, sagaIdentifier);
        }

        @SuppressWarnings({"unchecked"})
        @Override
        public void onAssociationValueRemoved(AssociationValue associationValue) {
            associationValueMap.remove(associationValue, sagaIdentifier);
            removeAssociationValue(associationValue, sagaIdentifier);
        }
    }
}
