package org.axonframework.commandhandling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Enumeration describing a set of reasonable {@link DuplicateCommandHandlerResolver} implementations. Can be used to
 * configure how a {@link org.axonframework.commandhandling.CommandBus} should react upon a duplicate subscription of a
 * command handling function.
 *
 * @author Steven van Beelen
 * @since 4.2
 */
public enum DuplicateCommandHandlerResolution {

    /**
     * A {@link DuplicateCommandHandlerResolver} implementation which logs a warning message and resolve to returning
     * the duplicate handler.
     */
    LOG_AND_RETURN_DUPLICATE((initialHandler, duplicateHandler) -> {
        String warningMessage = String.format(
                "A duplicate command handler was found. "
                        + "The initial and the duplicate handlers respectively reside in [%s] and [%s]."
                        + "The duplicate handler will be maintained according to the used resolver.",
                initialHandler.getTargetType(), duplicateHandler.getTargetType()
        );
        logWarning(warningMessage);
        return duplicateHandler;
    }),

    /**
     * A {@link DuplicateCommandHandlerResolver} implementation which logs a warning message and resolve to returning
     * the initial handler.
     */
    LOG_AND_RETURN_INITIAL((initialHandler, duplicateHandler) -> {
        String warningMessage = String.format(
                "A duplicate command handler was found. "
                        + "The initial and the duplicate handlers respectively reside in [%s] and [%s]."
                        + "The initial handler will be maintained according to the used resolver.",
                initialHandler.getTargetType(), duplicateHandler.getTargetType()
        );
        logWarning(warningMessage);
        return initialHandler;
    }),

    /**
     * A {@link DuplicateCommandHandlerResolver} implementation which throws a
     * {@link DuplicateCommandHandlerSubscriptionException}.
     */
    THROW_EXCEPTION((initialHandler, duplicateHandler) -> {
        throw new DuplicateCommandHandlerSubscriptionException(initialHandler, duplicateHandler);
    });

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final DuplicateCommandHandlerResolver resolver;

    DuplicateCommandHandlerResolution(DuplicateCommandHandlerResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Return the {@link DuplicateCommandHandlerResolver} implementation specified.
     *
     * @return the {@link DuplicateCommandHandlerResolver} implementation specified
     */
    public DuplicateCommandHandlerResolver getResolver() {
        return resolver;
    }

    /**
     * Private static method used to allow using the set {@code logger} within the provided enums to log a warning.
     *
     * @param message the message to log on warning level
     */
    private static void logWarning(String message) {
        logger.warn(message);
    }
}
