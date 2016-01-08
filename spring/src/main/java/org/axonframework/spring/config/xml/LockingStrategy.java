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

import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.NullLockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;

/**
 * Enum indicating possible locking strategies for repositories.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public enum LockingStrategy {

    /**
     * Represents the Pessimistic Lock factory
     */
    PESSIMISTIC(PessimisticLockFactory.class),
    /**
     * Represents the Null Lock factory (no locking)
     */
    NO_LOCKING(NullLockFactory.class);

    private final Class<? extends LockFactory> LockFactoryType;

    LockingStrategy(Class<? extends LockFactory> LockFactoryType) {
        this.LockFactoryType = LockFactoryType;
    }

    /**
     * Returns the type of LockFactory that belongs to this strategy.
     *
     * @return the type of LockFactory that belongs to this strategy
     */
    public Class<? extends LockFactory> getLockFactoryType() {
        return LockFactoryType;
    }
}
