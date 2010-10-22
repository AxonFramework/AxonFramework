package org.axonframework.eventstore.mongo;

/**
 * <p>Exception used to indicate the configuration of the MongoDB connection has errors</p>
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoInitializationException extends RuntimeException {
    /**
     * Constructor accepting a custom message
     *
     * @param message String describing the exception
     */
    public MongoInitializationException(String message) {
        super(message);
    }

    /**
     * Constructor excepting a custom message and the original exception
     *
     * @param message String describing the exception
     * @param cause   Original exception
     */
    public MongoInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
