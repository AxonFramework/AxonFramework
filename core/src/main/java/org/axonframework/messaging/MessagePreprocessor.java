package org.axonframework.messaging;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Interface of a component that can pre-process {@link Message Messages} before they are being processed by another
 * component.
 *
 * @author Rene de Waele
 */
public interface MessagePreprocessor {

    /**
     * Apply this preprocessor to the given <code>message</code> and return a modified version.
     *
     * @param message   The Message to apply the transformation to
     * @param <T>       The Message type
     * @return a transformed version of the <code>message</code>
     */
    default <T extends Message<?>> T on(T message) {
        return on(Collections.singletonList(message)).apply(0);
    }

    /**
     * Apply this preprocessor to the given list of <code>messages</code>. This method returns a Function that can be
     * queried to obtain a modified version of messages at each position in the list. For instance, to obtain the
     * pre-processed message at index 2, use:
     * <p/>
     * Message modifiedMessage = preprocessor.on(someListOfMessages).apply(2);
     *
     * @param messages  The Messages to pre-process
     * @param <T>       The Message type
     * @return a function that pre-processes messages based on their position in the list
     */
    <T extends Message<?>> Function<Integer, T> on(List<T> messages);
}
