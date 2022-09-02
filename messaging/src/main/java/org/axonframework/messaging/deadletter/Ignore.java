package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.util.Optional;

/**
 * An {@link EnqueueDecision} stating a {@link DeadLetter dead-letter} should be ignored. This typically means that a
 * dead-letter remains in the queue.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letter} that's been made a
 *            decision on.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class Ignore<M extends Message<?>> implements EnqueueDecision<M> {

    @Override
    public boolean shouldEnqueue() {
        return true;
    }

    @Override
    public Optional<Throwable> enqueueCause() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "Ignore{}";
    }
}
