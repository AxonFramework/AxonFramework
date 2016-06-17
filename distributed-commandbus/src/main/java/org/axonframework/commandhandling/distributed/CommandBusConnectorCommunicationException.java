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
