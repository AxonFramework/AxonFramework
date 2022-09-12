package org.axonframework.modelling.command;

import org.axonframework.common.AxonException;

/**
 * Exception mean to indicate that a required identifier is missing in the processed message.
 *
 * @author Stefan Andjelkovic
 * @since 4.6.0
 */
public class IdentifierMissingException extends AxonException {

    /**
     * Construct the exception instance from a message.
     *
     * @param message The message to pass on to parent exception.
     */
    public IdentifierMissingException(String message) {
        super(message);
    }
}
