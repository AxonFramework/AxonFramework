/*
 * Copyright (c) 2011. Axon Framework
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
            final Saga storedSaga = loadSaga(type, sagaIdentifier);
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

    @Override
    public void add(Saga saga) {
        Saga cachedSaga = sagaCache.put(saga);
        if (cachedSaga == saga) {
            for (AssociationValue av : saga.getAssociationValues()) {
                associationValueMap.add(av, saga.getSagaIdentifier());
                storeAssociationValue(av, saga.getSagaIdentifier());
            }
            saga.getAssociationValues().addChangeListener(new AssociationValueChangeListener(saga.getSagaIdentifier()));
            storeSaga(saga);
        }
    }

    @Override
    public void commit(Saga saga) {
        updateSaga(saga);
    }

    /**
     * Loads a known Saga instance by its unique identifier. Implementations are encouraged, but not required to return
     * the same instance when two Sagas with equal identifiers are loaded.
     *
     * @param type           The expected type of Saga
     * @param sagaIdentifier The unique identifier of the Saga to load
     * @param <T>            The expected type of Saga
     * @return The Saga instance
     *
     * @throws org.axonframework.saga.NoSuchSagaException
     *          if no Saga with given identifier can be found
     */
    protected abstract <T extends Saga> T loadSaga(Class<T> type, String sagaIdentifier);

    /**
     * Update a stored Saga, by replacing it with the given <code>saga</code> instance.
     *
     * @param saga The saga that has been modified and needs to be updated in the storage
     */
    protected abstract void updateSaga(Saga saga);

    /**
     * Stores a newly created Saga instance.
     *
     * @param saga The newly created Saga instance to store.
     */
    protected abstract void storeSaga(Saga saga);

    /**
     * Store the given <code>associationValue</code>, which has been associated with given <code>sagaIdentifier</code>.
     *
     * @param associationValue The association value to store
     * @param sagaIdentifier   The saga related to the association value
     */
    protected abstract void storeAssociationValue(AssociationValue associationValue, String sagaIdentifier);

    /**
     * Removes the association value that has been associated with Saga, identified with the given
     * <code>sagaIdentifier</code>.
     *
     * @param associationValue The value to remove as association value for the given saga
     * @param sagaIdentifier   The identifier of the Saga to remove the association from
     */
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
