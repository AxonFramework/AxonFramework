package org.axonframework.eventstore.mongo;

/**
 * <p>Exception used to indicate the configuration of the MongoDB connection has errors</p>
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoInitializationException extends RuntimeException {
    public MongoInitializationException(String message) {
        super(message);
    }

    public MongoInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
