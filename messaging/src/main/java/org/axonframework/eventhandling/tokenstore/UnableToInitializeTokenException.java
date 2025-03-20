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

package org.axonframework.eventhandling.tokenstore;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating that the TokenStore was unable to initialize a Token for a tracking processor and Segment.
 *
 * @author Allard Buijze
 * @since 4.1
 */
public class UnableToInitializeTokenException extends AxonTransientException {

    /**
     * Initialize the exception with given {@code message}
     *
     * @param message The message explaining the cause of the initialization problem
     */
    public UnableToInitializeTokenException(String message) {
        super(message);
    }

    /**
     * Initialize the exception with given {@code message} and underlying {@code cause}.
     *
     * @param message The message explaining the cause of the initialization problem
     * @param cause   The exception that cause the initialization to fail
     */
    public UnableToInitializeTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
