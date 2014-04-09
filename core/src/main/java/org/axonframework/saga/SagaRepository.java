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

package org.axonframework.saga;

import java.util.Set;

/**
 * Interface towards the storage mechanism of Saga instances. Saga Repositories can find sagas either through the
 * values
 * they have been associated with (see {@link AssociationValue}) or via their unique identifier.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface SagaRepository {

    /**
     * Find saga instances of the given <code>type</code> that have been associated with the given
     * <code>associationValue</code>.
     * <p/>
     * Returned Sagas must be {@link #commit(Saga) committed} after processing.
     *
     * @param type             The type of Saga to return
     * @param associationValue The value that the returned Sagas must be associated with
     * @return A Set containing the found Saga instances. If none are found, an empty Set is returned. Will never
     *         return <code>null</code>.
     */
    Set<String> find(Class<? extends Saga> type, AssociationValue associationValue);

    /**
     * Loads a known Saga instance by its unique identifier. Returned Sagas must be {@link #commit(Saga) committed}
     * after processing.
     * Due to the concurrent nature of Sagas, it is not unlikely for a Saga to have ceased to exist after it has been
     * found based on associations. Therefore, a repository should return <code>null</code> in case a Saga doesn't
     * exists, as opposed to throwing an exception.
     *
     * @param sagaIdentifier The unique identifier of the Saga to load
     * @return The Saga instance, or <code>null</code> if no such saga exists
     */
    Saga load(String sagaIdentifier);

    /**
     * Commits the changes made to the Saga instance. At this point, the repository may release any resources kept for
     * this saga. If the committed saga is marked inActive ({@link org.axonframework.saga.Saga#isActive()} returns
     * {@code false}), the repository should delete the saga from underlying storage and remove all stored association
     * values associated with that Saga.
     * <p/>
     * Implementations *may* (temporarily) return a cached version of the Saga, which is marked inactive.
     *
     * @param saga The Saga instance to commit
     */
    void commit(Saga saga);

    /**
     * Registers a newly created Saga with the Repository. Once a Saga instance has been added, it can be found using
     * its association values or its unique identifier.
     * <p/>
     * Note that if the added Saga is marked inActive ({@link org.axonframework.saga.Saga#isActive()} returns
     * {@code false}), it is not stored.
     *
     * @param saga The Saga instances to add.
     */
    void add(Saga saga);
}
