package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.util.Collection;
import javax.annotation.Nonnull;

/**
 * Generic implementation of the {@link DeadLetterSequence} storing the sequence in a {@link Collection}.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Milan Savic
 * @author Sara Pelligrini
 */
public class GenericDeadLetterSequence<T extends Message<?>> implements DeadLetterSequence<T> {

    private final QueueIdentifier queueIdentifier;
    private final Collection<DeadLetter<T>> sequence;

    /**
     * Constructs a default dead letter sequence with the given {@code queueIdentifier} and {@code sequence}.
     *
     * @param queueIdentifier The identifier of this {@code sequence}.
     * @param sequence        The sequence identified with the {@code queueIdentifier}
     */
    public GenericDeadLetterSequence(@Nonnull QueueIdentifier queueIdentifier,
                                     @Nonnull Collection<DeadLetter<T>> sequence) {
        this.queueIdentifier = queueIdentifier;
        this.sequence = sequence;
    }

    @Override
    public QueueIdentifier queueIdentifier() {
        return queueIdentifier;
    }

    @Override
    public Iterable<DeadLetter<T>> sequence() {
        return sequence;
    }
}
