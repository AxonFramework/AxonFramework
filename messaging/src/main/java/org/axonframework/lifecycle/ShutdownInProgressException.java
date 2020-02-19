package org.axonframework.lifecycle;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating a process tried to register an activity whilst the application is shutting down.
 *
 * @author Steven van Beelen
 * @see ShutdownLatch
 * @since 4.3
 */
public class ShutdownInProgressException extends AxonNonTransientException {

    private static final String DEFAULT_MESSAGE = "Cannot start the activity, shutdown in progress";

    /**
     * Construct this exception with the default message {@code "Cannot start the activity, shutdown in progress"}.
     */
    public ShutdownInProgressException() {
        this(DEFAULT_MESSAGE);
    }

    /**
     * Constructs this exception with given {@code message} explaining the cause.
     *
     * @param message The message explaining the cause
     */
    public ShutdownInProgressException(String message) {
        super(message);
    }
}
