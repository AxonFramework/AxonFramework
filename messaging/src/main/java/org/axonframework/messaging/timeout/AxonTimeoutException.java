/*
 * Copyright (c) 2010-2025. Axon Framework
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
package org.axonframework.messaging.timeout;

/**
 * Exception indicated that an Axon-specific task has timed out. This task can represent an entire
 * {@link org.axonframework.messaging.unitofwork.UnitOfWork} or the handling of a specific
 * {@link org.axonframework.messaging.Message}.
 *
 * @author Mitchell Herrijgers
 * @see AxonTimeLimitedTask
 * @see AxonTaskJanitor
 * @since 4.11.3
 */
public class AxonTimeoutException extends RuntimeException {

    /**
     * Initializes an {@link AxonTimeoutException} with the given {@code message}.
     *
     * @param message The message describing the cause of the timeout.
     */
    public AxonTimeoutException(String message) {
        super(message);
    }
}
