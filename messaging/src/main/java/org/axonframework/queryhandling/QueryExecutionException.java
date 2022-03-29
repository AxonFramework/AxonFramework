/*
 * Copyright (c) 2010-2022. Axon Framework
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
package org.axonframework.queryhandling;

import org.axonframework.messaging.HandlerExecutionException;

/**
 * Exception indicating that the execution of a Query Handler has resulted in an exception
 *
 * @author Marc Gathier
 * @since 3.1
 */
public class QueryExecutionException extends HandlerExecutionException {

    private static final long serialVersionUID = 3269266885785226323L;

    /**
     * Initializes the exception with given {@code message} and {@code cause}
     *
     * @param message Message explaining the context of the error
     * @param cause   The underlying cause of the invocation failure
     */
    public QueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Initializes the exception with given {@code message} and {@code cause} and {@code details}.
     *
     * @param message Message explaining the context of the error
     * @param cause   The underlying cause of the invocation failure
     * @param details An object providing more error details (may be {@code null}
     */
    public QueryExecutionException(String message, Throwable cause, Object details) {
        super(message, cause, details);
    }
}
