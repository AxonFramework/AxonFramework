package org.axonframework.commandhandling;

import org.axonframework.messaging.MessageHandler;

import java.util.function.BiFunction;

/**
 * Functional interface towards resolving the occurrence of a duplicate command handler being resolved.
 * As such it ingests two {@link MessageHandler} instances and returns another one as the resolution.
 * Several default implementations towards this interface have been provided in the {@link
 * DuplicateCommandHandlerResolution}.
 *
 * @author Steven van Beelen
 * @see DuplicateCommandHandlerResolution
 * @since 4.2
 */
@FunctionalInterface
public interface DuplicateCommandHandlerResolver extends
        BiFunction<MessageHandler<? super CommandMessage<?>>, MessageHandler<? super CommandMessage<?>>, MessageHandler<? super CommandMessage<?>>> {

    /**
     * Short hand towards {@link BiFunction#apply(Object, Object)}, mainly renamed for a nicer API.
     *
     * @param initialHandler   the initial {@link MessageHandler} for which a duplicate was encountered
     * @param duplicateHandler the duplicated {@link MessageHandler}
     * @return the resolved {@link MessageHandler}. Could be the {@code initialHandler}, the {@code duplicateHandler} or
     * another handler entirely
     */
    default MessageHandler<? super CommandMessage<?>> resolve(MessageHandler<? super CommandMessage<?>> initialHandler,
                                                              MessageHandler<? super CommandMessage<?>> duplicateHandler) {
        return apply(initialHandler, duplicateHandler);
    }
}
