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

package org.axonframework.modelling.command;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating that concurrent access to a repository was detected. Most likely, two threads were modifying
 * the
 * same aggregate.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class ConcurrencyException extends AxonTransientException {

    private static final long serialVersionUID = -739879545165860129L;

    /**
     * Initialize a ConcurrencyException with the given {@code message}.
     *
     * @param message The message describing the cause of the exception
     */
    public ConcurrencyException(String message) {
        super(message);
    }

    /**
     * Initialize a ConcurrencyException with the given {@code message} and {@code cause}.
     *
     * @param message The message describing the cause of the exception
     * @param cause   The cause of the exception
     */
    public ConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
