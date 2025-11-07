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

package org.axonframework.common.lock;

/**
 * Interface to the lock factory. A lock factory produces locks on resources that are shared between threads.
 *
 * @author Allard Buijze
 * @since 0.3
 */
@FunctionalInterface
public interface LockFactory {

    /**
     * Obtain a lock for a resource identified by given {@code identifier}. Depending on the strategy, this
     * method may return immediately or block until a lock is held.
     *
     * @param identifier the identifier of the resource to obtain a lock for.
     * @return a handle to release the lock.
     */
    Lock obtainLock(String identifier);
}
