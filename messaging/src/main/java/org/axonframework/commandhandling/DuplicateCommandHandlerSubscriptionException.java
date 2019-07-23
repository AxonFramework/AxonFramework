package org.axonframework.commandhandling;

import org.axonframework.common.AxonNonTransientException;
import org.axonframework.messaging.MessageHandler;

/**
 * Exception indicating a duplicate Command Handler was subscribed whilst this behavior is purposefully guarded against.
 *
 * @author Steven van Beelen
 * @since 4.2
 */
public class DuplicateCommandHandlerSubscriptionException extends AxonNonTransientException {

    /**
     * Initialize a duplicate command handler subscription exception using the given {@code initialHandler} and {@code
     * duplicateHandler} to form a specific message.
     *
     * @param initialHandler   the initial {@link MessageHandler} for which a duplicate was encountered
     * @param duplicateHandler the duplicated {@link MessageHandler}
     */
    public DuplicateCommandHandlerSubscriptionException(MessageHandler<? super CommandMessage<?>> initialHandler,
                                                        MessageHandler<? super CommandMessage<?>> duplicateHandler) {
        this(String.format(
                "A duplicate Command Handler has been subscribed residing in class [%s]"
                        + " that would override an identical handler in class [%s].",
                duplicateHandler.getTargetType(), initialHandler.getTargetType()
        ));
    }

    /**
     * Initializes a duplicate command handler subscription exception using the given {@code message}.
     *
     * @param message the message describing the exception
     */
    public DuplicateCommandHandlerSubscriptionException(String message) {
        super(message);
    }

    /**
     * Initializes a duplicate command handler subscription exception using the given {@code message} and {@code cause}.
     *
     * @param message the message describing the exception
     * @param cause   the underlying cause of the exception
     */
    public DuplicateCommandHandlerSubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
