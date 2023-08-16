/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.distributed;

/**
 * Represents a listener that is notified when a ConsistentHash instance of the component it is registered with has
 * changed.
 *
 * @author Allard Buijze
 * @since 3.2
 */
@FunctionalInterface
public interface ConsistentHashChangeListener {

    /**
     * Notification that a consistent hash has changed. Implementations should take into account that this listener may
     * be called concurrently, and that multiple invocations do not necessarily reflect the order in which changes have
     * occurred. If this is order important to the implementation, it should verify the {@link ConsistentHash#version()}
     * of the given {@code newConsistentHash}.
     *
     * @param newConsistentHash The new consistent hash
     */
    void onConsistentHashChanged(ConsistentHash newConsistentHash);

    /**
     * Returns a No-op version of the functional interface {@link ConsistentHashChangeListener}.
     *
     * @return a no-op lambda taking a {@link org.axonframework.commandhandling.distributed.ConsistentHash} as its only
     * parameter.
     */
    static ConsistentHashChangeListener noOp() {
        return newConsistentHash -> {
        };
    }
}
