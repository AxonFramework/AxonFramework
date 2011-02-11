/*
 * Copyright (c) 2011. Axon Framework
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

/**
 * Enum indicating possible locking strategies for repositories.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public enum LockingStrategy {

    /**
     * Indicator of an optimistic locking strategy. Concurrent access is not prevented. Instead, when concurrent
     * modifications are detected, an exception is thrown.
     * <p/>
     * <strong>Important note</strong>: in a highly concurrent environment (i.e. multiple threads acting on the same
     * aggregate simultaneously), the optimistic locking strategy cannot guarantee that events are dispatched in the
     * exact same order as they are generated. Whether this occurs or not depends highly on the CPU architecture and the
     * scheduling of threads. For full ordering guarantee, regardless hardware architecture, use the pessimistic locking
     * strategy.
     *
     * @see LockingRepository
     * @see ConcurrencyException
     */
    OPTIMISTIC,

    /**
     * Indicator of a pessimistic locking strategy. This strategy will block any thread that tries to load an aggregate
     * that has already been loaded by another thread. Once the other thread saves the aggregate, the lock is released,
     * giving waiting threads the opportunity to obtain it and load the aggregate.
     *
     * @see LockingRepository
     */
    PESSIMISTIC,

    /**
     * Indicator of no locking strategy. This strategy expects another mechanism to perform the necessary locking, such
     * as an event store using a relational database.
     */
    NO_LOCKING
}
