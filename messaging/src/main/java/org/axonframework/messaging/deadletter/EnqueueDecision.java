package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.util.Optional;

/**
 * A contract towards describing a decision among a {@link DeadLetter dead-letter} of type {@code D}.
 * <p>
 * Either describes the letter should be {@link #shouldEnqueue() enqueued} or {@link #shouldEvict() evicted}. If the
 * letter should be enqueued the {@link #enqueueCause()} may contain a {@link Throwable}. Furthermore,
 * {@link #addDiagnostics(DeadLetter)} may add {@link DeadLetter#diagnostic() diagnostic} information to the dead-letter
 * that should be taken into account when enqueueing the letter.
 *
 * @param <D> An implementation of {@link DeadLetter} that's been made a decision on.
 * @author Steven van Beelen
 * @since 4.6.0
 * @see Decisions
 */
public interface EnqueueDecision<D extends DeadLetter<? extends Message<?>>> {

    /**
     * The decision whether the {@link DeadLetter dead-letter} should be evicted from its queue.
     *
     * @return {@code true} if the {@link DeadLetter dead-letter} should be evicted, otherwise {@code false}.
     */
    boolean shouldEvict();

    /**
     * The decision whether the {@link DeadLetter dead-letter} should be enqueued in a queue.
     *
     * @return {@code true} if the {@link DeadLetter dead-letter} should be enqueued, otherwise {@code false}.
     */
    boolean shouldEnqueue();

    /**
     * A {@link Throwable} {@link Optional} that was part of deciding to enqueue the {@link DeadLetter dead-letter} in a
     * queue. Empty if the {@code dead-letter} {@link #shouldEvict() should be evicted} or when there is no failure
     * cause used for deciding to enqueue.
     *
     * @return The deciding failure for enqueueing a {@link DeadLetter dead-letter}, when present.
     */
    Optional<Throwable> enqueueCause();

    /**
     * Adds {@link DeadLetter#diagnostic() diagnostic} {@link org.axonframework.messaging.MetaData} to the given
     * {@code letter}. The added diagnostics may provide additional information on the decision that may be used to
     * influence future decisions.
     * <p>
     * By default, the {@code letter} is returned as is.
     *
     * @param letter The {@link DeadLetter dead-letter} of type {@code D} to add
     *               {@link DeadLetter#diagnostic() diagnostic} {@link org.axonframework.messaging.MetaData} to.
     * @return A copy of the given {@code letter} when {@link DeadLetter#diagnostic() diagnostic}
     * {@link org.axonframework.messaging.MetaData} was added.
     */
    default D addDiagnostics(D letter) {
        return letter;
    }
}
