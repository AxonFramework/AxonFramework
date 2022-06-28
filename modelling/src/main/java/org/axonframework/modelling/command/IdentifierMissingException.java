package org.axonframework.modelling.command;

/**
 * Exception mean to indicate that an required identifier is missing in the processed message.
 * @author Stefan Andjelkovic
 * @since 4.6
 */
public class IdentifierMissingException extends IllegalArgumentException {

    /**
     * Construct the exception instance from a message
     * @param s message to pass on to parent exception
     */
    public IdentifierMissingException(String s) {
        super(s);
    }
}
