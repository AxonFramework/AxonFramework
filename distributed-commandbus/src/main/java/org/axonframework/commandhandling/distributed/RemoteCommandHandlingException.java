/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.common.AxonException;

/**
 * Exception indicating that an error has occurred while remotely handling a command. This may mean that a command was
 * dispatched, but the segment that handled the command is no longer available.
 * <p/>
 * The sender of the command <strong>cannot</strong> assume that the command has not been handled. It may, if the type
 * of command or the infrastructure allows it, try to dispatch the command again.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class RemoteCommandHandlingException extends AxonException {

    private static final long serialVersionUID = 7310513417002285205L;

    /**
     * Initializes the exception using the given {@code message}.
     *
     * @param message The message describing the exception
     */
    public RemoteCommandHandlingException(String message) {
        super(message);
    }

    /**
     * Initializes the exception using the given {@code message} and {@code cause}.
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public RemoteCommandHandlingException(String message, Throwable cause) {
        super(message, cause);
    }
}
