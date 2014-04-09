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

package org.axonframework.saga.repository;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;

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

    @Override
    public Set<String> find(Class<? extends Saga> type, AssociationValue associationValue) {
        return findAssociatedSagaIdentifiers(type, associationValue);
    }

    @Override
    public void add(Saga saga) {
        if (saga.isActive()) {
            final String sagaType = typeOf(saga.getClass());
            final AssociationValues associationValues = saga.getAssociationValues();
            for (AssociationValue av : associationValues.addedAssociations()) {
                storeAssociationValue(av, sagaType, saga.getSagaIdentifier());
            }
            associationValues.commit();
            storeSaga(saga);
        }
    }

    @Override
    public void commit(Saga saga) {
        if (!saga.isActive()) {
            deleteSaga(saga);
        } else {
            final String sagaType = typeOf(saga.getClass());
            final AssociationValues associationValues = saga.getAssociationValues();
            for (AssociationValue associationValue : associationValues.addedAssociations()) {
                storeAssociationValue(associationValue, sagaType, saga.getSagaIdentifier());
            }
            for (AssociationValue associationValue : associationValues.removedAssociations()) {
                removeAssociationValue(associationValue, sagaType, saga.getSagaIdentifier());
            }
            associationValues.commit();
            updateSaga(saga);
        }
    }

    /**
     * Finds the identifiers of the sagas of given <code>type</code> associated with the given
     * <code>associationValue</code>.
     *
     * @param type             The type of saga to find identifiers for
     * @param associationValue The value the saga must be associated with
     * @return The identifiers of sagas associated with the given <code>associationValue</code>
     */
    protected abstract Set<String> findAssociatedSagaIdentifiers(Class<? extends Saga> type,
                                                                 AssociationValue associationValue);

    /**
     * Returns the type identifier to use for the given <code>sagaClass</code>. This information is typically provided
     * by the Serializer, if the repository stores serialized instances.
     *
     * @param sagaClass The type of saga to get the type identifier for.
     * @return The type identifier to use for the given sagaClass
     */
    protected abstract String typeOf(Class<? extends Saga> sagaClass);

    /**
     * Remove the given saga as well as all known association values pointing to it from the repository. If no such
     * saga exists, nothing happens.
     *
     * @param saga The saga instance to remove from the repository
     */
    protected abstract void deleteSaga(Saga saga);

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
     * @param sagaType         Type type of saga the association value belongs to
     * @param sagaIdentifier   The saga related to the association value
     */
    protected abstract void storeAssociationValue(AssociationValue associationValue,
                                                  String sagaType, String sagaIdentifier);

    /**
     * Removes the association value that has been associated with Saga, identified with the given
     * <code>sagaIdentifier</code>.
     *
     * @param associationValue The value to remove as association value for the given saga
     * @param sagaType         The type of the Saga to remove the association from
     * @param sagaIdentifier   The identifier of the Saga to remove the association from
     */
    protected abstract void removeAssociationValue(AssociationValue associationValue,
                                                   String sagaType, String sagaIdentifier);
}
