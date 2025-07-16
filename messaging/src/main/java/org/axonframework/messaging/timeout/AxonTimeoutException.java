package org.axonframework.messaging.timeout;

/**
 * Exception indicated that a task has timed out. This can be the entire
 * {@link org.axonframework.messaging.unitofwork.UnitOfWork}, or the handling of a specific
 * {@link org.axonframework.messaging.Message}.
 *
 * @author Mitchell Herrijgers
 * @see AxonTimeLimitedTask
 * @see AxonTaskJanitor
 * @since 4.11.3
 */
public class AxonTimeoutException extends RuntimeException {

    /**
     * Initializes an {@link AxonTimeoutException} with the given {@code message}.
     *
     * @param message The message describing the cause of the timeout.
     */
    public AxonTimeoutException(String message) {
        super(message);
    }
}
