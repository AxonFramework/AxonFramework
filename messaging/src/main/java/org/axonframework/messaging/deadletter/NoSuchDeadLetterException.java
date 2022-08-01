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
     * Construct a default {@link NoSuchDeadLetterException} based on the given {@code identifier} and
     * {@code sequenceIdentifier}.
     *
     * @param identifier         The {@link DeadLetter#identifier()} of the {@link DeadLetter dead-letter} that is not
     *                           present in the queue.
     * @param sequenceIdentifier The {@link DeadLetter#sequenceIdentifier()} of the {@link DeadLetter dead-letter} that
     *                           is not present in the queue.
     */
    public NoSuchDeadLetterException(String identifier, SequenceIdentifier sequenceIdentifier) {
        this("There's no dead letter present for identifier [" + identifier
                     + "] referencing the given queue identifier [" + sequenceIdentifier + "]");
    }

    /**
     * Constructs an exception based on the given {@code message}.
     *
     * @param message The description of this {@link NoSuchDeadLetterException}.
     */
    public NoSuchDeadLetterException(String message) {
        super(message);
    }
}
