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

package org.axonframework.serialization;

import org.axonframework.common.AxonConfigurationException;

/**
 * Exception indicating that a conversion is required between to upcasters, but there is no converter capable of doing
 * the conversion.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CannotConvertBetweenTypesException extends AxonConfigurationException {

    /**
     * Initializes the exception with the given {@code message}.
     *
     * @param message The message describing the problem
     */
    public CannotConvertBetweenTypesException(String message) {
        super(message);
    }

    /**
     * Initializing the exception with given {@code message} and {@code cause}.
     *
     * @param message The message describing the problem
     * @param cause   The original cause of the exception
     */
    public CannotConvertBetweenTypesException(String message, Throwable cause) {
        super(message, cause);
    }
}
