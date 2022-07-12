package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

/**
 * Contract describing a sequence of {@link DeadLetter dead letters}, identified through the
 * {@link #queueIdentifier()}.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Milan Savic
 * @author Sara Pelligrini
 */
public interface DeadLetterSequence<T extends Message<?>> {

    /**
     * Returns the identifier of this sequence of {@link DeadLetter dead letters}.
     *
     * @return The identifier of this sequence of {@link DeadLetter dead letters}.
     */
    QueueIdentifier queueIdentifier();

    /**
     * Return the sequence of {@link DeadLetter dead letters}.
     *
     * @return The sequence of {@link DeadLetter dead letters}.
     */
    Iterable<DeadLetter<T>> sequence();
}
