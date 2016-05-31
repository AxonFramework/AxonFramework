/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventhandling.saga.repository;

import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.AssociationValues;
import org.axonframework.eventsourcing.eventstore.TrackingToken;

import java.util.Set;

public interface SagaStore<T> {

    Set<String> findSagas(Class<? extends T> sagaType, AssociationValue associationValue);

    <S extends T> Entry<S> loadSaga(Class<S> sagaType, String sagaIdentifier);

    void deleteSaga(Class<? extends T> sagaType, String sagaIdentifier, Set<AssociationValue> associationValues);

    void insertSaga(Class<? extends T> sagaType, String sagaIdentifier, T saga,
                    TrackingToken token,
                    Set<AssociationValue> associationValues);

    void updateSaga(Class<? extends T> sagaType, String sagaIdentifier, T saga,
                    TrackingToken token,
                    AssociationValues associationValues);

    interface Entry<T> {

        TrackingToken trackingToken();

        Set<AssociationValue> associationValues();

        T saga();
    }
}
