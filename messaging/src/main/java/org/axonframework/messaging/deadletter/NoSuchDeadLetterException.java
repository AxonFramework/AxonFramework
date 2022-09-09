package org.axonframework.messaging.deadletter;

import org.axonframework.common.AxonException;

/**
 * An {@link AxonException} describing that there is no such {@link DeadLetter} present in a
 * {@link SequencedDeadLetterQueue}.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class NoSuchDeadLetterException extends AxonException {

    private static final long serialVersionUID = 2199691939768333102L;

    /**
     * Constructs an exception based on the given {@code message}.
     *
     * @param message The description of this {@link NoSuchDeadLetterException}.
     */
    public NoSuchDeadLetterException(String message) {
        super(message);
    }
}
