package org.axonframework.eventhandling.amqp;

import org.axonframework.common.AxonException;

/**
 * Exception indication that an error occurred while publishing an event to an AMQP Broker
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class EventPublicationFailedException extends AxonException {

    private static final long serialVersionUID = 3663633361627495227L;

    /**
     * Initialize the exception using given descriptive <code>message</code> and <code>cause</code>
     *
     * @param message A message describing the exception
     * @param cause   The exception describing the cause of the failure
     */
    public EventPublicationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
