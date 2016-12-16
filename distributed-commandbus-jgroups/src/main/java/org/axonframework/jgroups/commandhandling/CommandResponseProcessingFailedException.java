/*
 * Copyright (c) 2010-2015. Axon Framework
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
package org.axonframework.jgroups.commandhandling;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that a failure occurred during processing of a command response. Typically this would imply an
 * command response or exception message that could not be serialized.
 * <p/>
 * Typically, this exception indicates a non-transient exception.
 *
 * @author Srideep Prasad
 * @since 2.4.2
 */
public class CommandResponseProcessingFailedException extends AxonNonTransientException {

    private static final long serialVersionUID = -1318148724064577512L;

    /**
     * Initializes the exception using the given {@code message}.
     *
     * @param message The message describing the exception
     */
    public CommandResponseProcessingFailedException(String message) {
        super(message);
    }

    /**
     * Initializes the exception using the given {@code message} and {@code cause}.
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public CommandResponseProcessingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
