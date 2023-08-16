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

import org.axonframework.common.AxonTransientException;

/**
 * Exception thrown when the CommandBusConnector has a communication failure
 */
public class CommandBusConnectorCommunicationException extends AxonTransientException {
    /**
     * Initializes the CommandBusConnectorCommunicationException
     * @param message The message of the exception
     */
    public CommandBusConnectorCommunicationException(String message) {
        super(message);
    }

    /**
     * Initializes the CommandBusConnectorCommunicationException
     * @param message The message of the exception
     * @param cause   The cause of this exception
     */
    public CommandBusConnectorCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
