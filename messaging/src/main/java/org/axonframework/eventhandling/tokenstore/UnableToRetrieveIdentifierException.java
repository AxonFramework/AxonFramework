package org.axonframework.eventhandling.tokenstore;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating that a TokenStore implementation was unable determine its identifier based on the underlying
 * storage.
 *
 * @author Allard Buijze
 * @see TokenStore#retrieveStorageIdentifier()
 * @since 4.3
 */
public class UnableToRetrieveIdentifierException extends AxonTransientException {

    /**
     * Initialize the exception using given {@code message} and {@code cause}.
     *
     * @param message A message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public UnableToRetrieveIdentifierException(String message, Throwable cause) {
        super(message, cause);
    }
}
