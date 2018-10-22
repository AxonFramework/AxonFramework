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

package org.axonframework.common.property;


import org.axonframework.common.AxonConfigurationException;

/**
 * Exception indicating that a predefined property is not accessible. Generally, this means that the property does not
 * conform to the accessibility requirements of the accessor.
 *
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public class PropertyAccessException extends AxonConfigurationException {

    private static final long serialVersionUID = -1360531453606316133L;

    /**
     * Initializes the PropertyAccessException with given {@code message} and {@code cause}.
     *
     * @param message The message describing the cause
     * @param cause   The underlying cause of the exception
     */
    public PropertyAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
