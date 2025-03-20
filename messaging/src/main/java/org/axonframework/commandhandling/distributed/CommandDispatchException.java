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

package org.axonframework.commandhandling.distributed;

import org.axonframework.common.AxonException;

/**
 * Exception indicating that an error has occurred while trying to dispatch a command to another (potentially remote)
 * segment of the CommandBus.
 * <p/>
 * The dispatcher of the command may assume that the Command has never reached its destination, and has not been
 * handled.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandDispatchException extends AxonException {

    /**
     * Initializes the exception using the given {@code message}.
     *
     * @param message The message describing the exception
     */
    public CommandDispatchException(String message) {
        super(message);
    }

    /**
     * Initializes the exception using the given {@code message} and {@code cause}.
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public CommandDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
