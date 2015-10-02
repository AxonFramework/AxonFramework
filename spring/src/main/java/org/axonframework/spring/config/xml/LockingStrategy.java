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

package org.axonframework.spring.config.xml;

import org.axonframework.common.lock.LockManager;
import org.axonframework.common.lock.NullLockManager;
import org.axonframework.common.lock.OptimisticLockManager;
import org.axonframework.common.lock.PessimisticLockManager;

/**
 * Enum indicating possible locking strategies for repositories.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public enum LockingStrategy {

    /**
     * Represents the Optimistic Lock Manager
     */
    OPTIMISTIC(OptimisticLockManager.class),
    /**
     * Represents the Pessimistic Lock Manager
     */
    PESSIMISTIC(PessimisticLockManager.class),
    /**
     * Represents the Null Lock Manager (no locking)
     */
    NO_LOCKING(NullLockManager.class);

    private final Class<? extends LockManager> lockManagerType;

    private LockingStrategy(Class<? extends LockManager> lockManagerType) {
        this.lockManagerType = lockManagerType;
    }

    /**
     * Returns the type of LockManager that belongs to this strategy.
     *
     * @return the type of LockManager that belongs to this strategy
     */
    public Class<? extends LockManager> getLockManagerType() {
        return lockManagerType;
    }
}
