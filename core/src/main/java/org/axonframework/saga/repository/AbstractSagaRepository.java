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

package org.axonframework.saga.repository;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract implementation for saga repositories. This (partial) implementation will take care of the uniqueness of
 * saga
 * instances in the JVM. That means it will prevent multiple instances of the same conceptual Saga (i.e. with same
 * identifier) to exist within the JVM.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractSagaRepository implements SagaRepository {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSagaRepository.class);

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
                if (cachedSaga != null) {
                    result.add(cachedSaga);
                }
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
            if (isSameInstance(cachedSaga, storedSaga)) {
                logger.debug("Loaded saga with id {} was not cached yet.", sagaIdentifier);
                cachedSaga.getAssociationValues().addChangeListener(
                        new AssociationValueChangeListener(cachedSaga.getSagaIdentifier()));
            } else {
                logger.debug("Loaded saga with id {} has been replaced by a cached instance", sagaIdentifier);
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
        if (isSameInstance(cachedSaga, saga)) {
            for (AssociationValue av : saga.getAssociationValues()) {
                associationValueMap.add(av, saga.getSagaIdentifier());
                storeAssociationValue(av, saga.getSagaIdentifier());
            }
            saga.getAssociationValues().addChangeListener(new AssociationValueChangeListener(saga.getSagaIdentifier()));
            storeSaga(saga);
        }
    }

    /**
     * Returns <code>true</code> if the two parameters point to exactly the same object instance. This method will
     * always return <code>true</code> if sagaInstance.equals(anotherSaga) return <code>true</code>, but not
     * necessarily
     * vice versa.
     *
     * @param sagaInstance One instance to compare
     * @param anotherSaga  Another instance to compare
     * @return <code>true</code> if both references point to exactly the same instance
     */
    private boolean isSameInstance(Saga sagaInstance, Saga anotherSaga) {
        return sagaInstance == anotherSaga;
    }

    @Override
    public void commit(Saga saga) {
        if (!saga.isActive()) {
            for (AssociationValue associationValue : saga.getAssociationValues()) {
                associationValueMap.remove(associationValue, saga.getSagaIdentifier());
            }
            deleteSaga(saga);
        } else {
            updateSaga(saga);
        }
    }

    /**
     * Remove the given saga as well as all known association values pointing to it from the repository. If no such
     * saga exists, nothing happens.
     *
     * @param saga The saga instance to remove from the repository
     */
    protected abstract void deleteSaga(Saga saga);

    /**
     * Loads a known Saga instance by its unique identifier. Implementations are encouraged, but not required to return
     * the same instance when two Sagas with equal identifiers are loaded.
     * <p/>
     * If the saga with given identifier is not of the given type, <code>null</code> should be returned instead.
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
