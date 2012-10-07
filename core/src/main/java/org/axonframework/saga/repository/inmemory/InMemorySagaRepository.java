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

package org.axonframework.saga.repository.inmemory;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SagaRepository implementation that stores all Saga instances in memory.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class InMemorySagaRepository implements SagaRepository {

    private final ConcurrentMap<String, Saga> managedSagas = new ConcurrentHashMap<String, Saga>();

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> find(Class<? extends Saga> type, AssociationValue associationValue) {
        Set<String> result = new HashSet<String>();
        for (Saga saga : managedSagas.values()) {
            if (saga.getAssociationValues().contains(associationValue) && type.isInstance(saga)) {
                result.add(saga.getSagaIdentifier());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Saga load(String sagaIdentifier) {
        return managedSagas.get(sagaIdentifier);
    }

    @Override
    public void commit(Saga saga) {
        if (!saga.isActive()) {
            managedSagas.remove(saga.getSagaIdentifier());
        } else {
            managedSagas.put(saga.getSagaIdentifier(), saga);
        }
        saga.getAssociationValues().commit();
    }

    @Override
    public void add(Saga saga) {
        commit(saga);
    }

    /**
     * Returns the number of Sagas currently contained in this repository.
     *
     * @return the number of Sagas currently contained in this repository
     */
    public int size() {
        return managedSagas.size();
    }
}
