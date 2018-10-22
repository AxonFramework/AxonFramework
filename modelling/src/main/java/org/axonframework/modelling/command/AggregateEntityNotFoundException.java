/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that the an entity for an aggregate could not be found.
 *
 * @author Steven van Beelen
 * @since 3.3
 */
public class AggregateEntityNotFoundException extends AxonNonTransientException {

    private static final long serialVersionUID = 3244586128749940025L;

    /**
     * Initialize a AggregateEntityNotFoundException with given {@code message}.
     *
     * @param message The message describing the cause of the exception
     */
    public AggregateEntityNotFoundException(String message) {
        super(message);
    }

    /**
     * Initialize a AggregateEntityNotFoundException with given {@code message} and {@code cause}.
     *
     * @param message The message describing the cause of the exception
     * @param cause   The underlying cause of the exception
     */
    public AggregateEntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
