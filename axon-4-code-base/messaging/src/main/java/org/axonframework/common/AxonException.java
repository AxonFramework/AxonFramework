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

package org.axonframework.common;

/**
 * Base exception for all Axon Framework related exceptions.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class AxonException extends RuntimeException {

    private static final long serialVersionUID = 5157720304497629941L;

    /**
     * Initializes the exception using the given {@code message}.
     *
     * @param message The message describing the exception
     */
    public AxonException(String message) {
        super(message);
    }

    /**
     * Initializes the exception using the given {@code message} and {@code cause}.
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public AxonException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Initializes the exception using the given {@code message}, {@code cause} and {@code writableStackTrace}.
     *
     * @param message            The message describing the exception
     * @param cause              The underlying cause of the exception
     * @param writableStackTrace Whether the stack trace should be generated ({@code true}) or not ({@code false})
     */
    public AxonException(String message, Throwable cause, boolean writableStackTrace) {
        super(message, cause, true, writableStackTrace);
    }
}