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

package org.axonframework.saga.repository.inmemory;

import org.axonframework.common.CollectionUtils;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.NoSuchSagaException;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaRepository;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * SagaRepository implementation that stores all Saga instances in memory.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class InMemorySagaRepository implements SagaRepository {

    private final Set<Saga> managedSagas = new ConcurrentSkipListSet<Saga>(new SagaIdentifierComparator());

    @Override
    public <T extends Saga> Set<T> find(Class<T> type, Set<AssociationValue> associationValues) {
        Set<T> result = new HashSet<T>();
        for (AssociationValue associationValue : associationValues) {
            result.addAll(find(type, associationValue));
        }
        return result;
    }

    @Override
    public <T extends Saga> T load(Class<T> type, String sagaIdentifier) {
        List<T> sagasOfType = CollectionUtils.filterByType(managedSagas, type);
        for (T saga : sagasOfType) {
            if (saga.getSagaIdentifier().equals(sagaIdentifier)) {
                return saga;
            }
        }
        throw new NoSuchSagaException(type, sagaIdentifier);
    }

    @Override
    public void commit(Saga saga) {
        if (!saga.isActive()) {
            managedSagas.remove(saga);
        } else {
            managedSagas.add(saga);
        }
    }

    @Override
    public void add(Saga saga) {
        commit(saga);
    }

    private <T extends Saga> Set<T> find(Class<T> type, AssociationValue associationValue) {
        Set<T> result = new HashSet<T>();
        List<T> sagasOfType = CollectionUtils.filterByType(managedSagas, type);
        for (T saga : sagasOfType) {
            if (saga.getAssociationValues().contains(associationValue)) {
                result.add(saga);
            }
        }
        return result;
    }

    /**
     * Returns the number of Sagas currently contained in this repository.
     *
     * @return the number of Sagas currently contained in this repository
     */
    public int size() {
        return managedSagas.size();
    }

    private static class SagaIdentifierComparator implements Comparator<Saga>, Serializable {

        private static final long serialVersionUID = 2939897180067202510L;

        @Override
        public int compare(Saga o1, Saga o2) {
            return o1.getSagaIdentifier().compareTo(o2.getSagaIdentifier());
        }
    }
}
