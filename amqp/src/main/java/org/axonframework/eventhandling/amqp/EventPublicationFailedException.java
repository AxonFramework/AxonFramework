package org.axonframework.eventhandling.amqp;

import org.axonframework.common.AxonException;

/**
 * @author Allard Buijze
 */
public class EventPublicationFailedException extends AxonException {

    public EventPublicationFailedException(String message) {
        super(message);
    }

    public EventPublicationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
