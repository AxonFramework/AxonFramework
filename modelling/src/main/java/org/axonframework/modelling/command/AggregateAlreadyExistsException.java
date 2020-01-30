package org.axonframework.modelling.command;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that concurrent access to a repository was detected. Most likely, two threads were creating the
 * same aggregate.
 *
 * @author Lucas Campos
 * @since 4.3
 */
public class AggregateAlreadyExistsException extends AxonNonTransientException {

    private static final long serialVersionUID = -4514732518167514479L;

    /**
     * Initialize the exception with the given {@code message}.
     *
     * @param message a detailed message of the cause of the exception
     */
    public AggregateAlreadyExistsException(String message) {
        super(message);
    }

    /**
     * Initialize the exception with the given {@code message} and {@code cause}
     *
     * @param message a detailed message of the cause of the exception
     * @param cause   the original cause of this exception
     */
    public AggregateAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
