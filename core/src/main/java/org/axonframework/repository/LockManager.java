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

package org.axonframework.repository;

import org.axonframework.domain.AggregateRoot;

/**
 * Interface to the lock manager. A lock manager will maintain and validate locks on aggregates of a single type
 *
 * @author Allard Buijze
 * @since 0.3
 */
public interface LockManager {

    /**
     * Make sure that the current thread holds a valid lock for the given aggregate.
     *
     * @param aggregate the aggregate to validate the lock for
     * @return true if a valid lock is held, false otherwise
     */
    boolean validateLock(AggregateRoot aggregate);

    /**
     * Obtain a lock for an aggregate with the given <code>aggregateIdentifier</code>. Depending on the strategy, this
     * method may return immediately or block until a lock is held.
     *
     * @param aggregateIdentifier the identifier of the aggregate to obtains a lock for.
     */
    void obtainLock(Object aggregateIdentifier);

    /**
     * Release the lock held for an aggregate with the given <code>aggregateIdentifier</code>. The caller of this
     * method must ensure a valid lock was requested using {@link #obtainLock(Object)}. If no lock was successfully
     * obtained, the behavior of this method is undefined.
     *
     * @param aggregateIdentifier the identifier of the aggregate to release the lock for.
     */
    void releaseLock(Object aggregateIdentifier);
}
