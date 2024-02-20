package org.axonframework.spring.authorization;

/**
 * Exception indicating that a command has been rejected due to a lack of authorization.
 *
 * @author Roald Bankras
 */
public class UnauthorizedCommandException extends RuntimeException {

    /**
     * Construct the exception with the given {$code message}
     *
     * @param message the message describing the cause
     */
    public UnauthorizedCommandException(String message) {
        super(message);
    }
}
