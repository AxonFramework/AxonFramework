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
 * Exception indicating that a deadlock has been detected while a thread was attempting to acquire a lock. This
 * typically happens when a Thread attempts to acquire a lock that is owned by a Thread that is in turn waiting for a
 * lock held by the current thread.
 * <p/>
 * It is typically safe to retry the operation when this exception occurs.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DeadlockException extends LockAcquisitionFailedException {

    /**
     * Initializes the exception with given {@code message}.
     *
     * @param message The message describing the exception
     */
    public DeadlockException(String message) {
        super(message);
    }
}
