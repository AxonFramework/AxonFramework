package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.util.Optional;

/**
 * An {@link EnqueueDecision} stating a {@link DeadLetter dead-letter} should not be enqueued.
 *
 * @param <D> An implementation of {@link DeadLetter} that's been made a decision on.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DoNotEnqueue<D extends DeadLetter<? extends Message<?>>> implements EnqueueDecision<D> {

    @Override
    public boolean shouldEvict() {
        return true;
    }

    @Override
    public boolean shouldEnqueue() {
        return false;
    }

    @Override
    public Optional<Throwable> enqueueCause() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "DoNotEnqueue{}";
    }
}
