package org.axonframework.common.jdbc;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating an error occurred while interacting with a JDBC resource.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public class JdbcTransactionException extends AxonTransientException {

    /**
     * Initialize the exception with given <code>message</code> and <code>cause</code>
     *
     * @param message The message describing the error
     * @param cause   The cause of the error
     */
    public JdbcTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
