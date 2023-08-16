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

package org.axonframework.eventhandling;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that a reset is not supported by a component.
 *
 * @author Allard Buijze
 * @since 3.2
 */
public class ResetNotSupportedException extends AxonNonTransientException {

    private static final long serialVersionUID = 6915875633006978877L;

    /**
     * Initialize the exception with given {@code message}
     *
     * @param message a message describing the cause of the exception
     */
    public ResetNotSupportedException(String message) {
        super(message);
    }


    /**
     * Initialize the exception with given {@code message} and {@code cause}.
     *
     * @param message a message describing the cause of the exception
     * @param cause   the cause of this exception
     */
    public ResetNotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
