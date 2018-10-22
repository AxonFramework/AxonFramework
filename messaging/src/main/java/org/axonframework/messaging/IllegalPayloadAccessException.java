/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.messaging;

import org.axonframework.common.AxonNonTransientException;

/**
 * Indicates situations when access to the Payload of a Message is not allowed.
 *
 * @author Milan Savic
 * @since 4.0
 */
public class IllegalPayloadAccessException extends AxonNonTransientException {

    /**
     * Creates the exception with given {@code message}.
     *
     * @param message the exception message
     */
    public IllegalPayloadAccessException(String message) {
        super(message);
    }

    /**
     * Creates the exception with given {@code message} and {@code cause}.
     *
     * @param message the exception message
     * @param cause   the exception which caused illegal payload access
     */
    public IllegalPayloadAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
