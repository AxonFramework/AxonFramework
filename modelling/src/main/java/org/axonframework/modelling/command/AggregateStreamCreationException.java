/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.modelling.command;

import org.axonframework.common.AxonException;

/**
 * Exception indicating that concurrent access to a repository was detected. Most likely, two threads were using the
 * same aggregate identifier to create an aggregate.
 *
 * @author Lucas Campos
 * @since 4.3
 */
public class AggregateStreamCreationException extends AxonException {

    private static final long serialVersionUID = -4514732518167514479L;

    /**
     * Initialize the exception with the given {@code message}.
     *
     * @param message a detailed message of the cause of the exception
     */
    public AggregateStreamCreationException(String message) {
        super(message);
    }

    /**
     * Initialize the exception with the given {@code message} and {@code cause}
     *
     * @param message a detailed message of the cause of the exception
     * @param cause   the original cause of this exception
     */
    public AggregateStreamCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
