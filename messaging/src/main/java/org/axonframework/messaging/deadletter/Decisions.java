package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;

import java.util.function.Function;

/**
 * Utility class providing a number of reasonable {@link EnqueueDecision EnqueueDecisions}. Can, for example, be used by
 * an {@link EnqueuePolicy} to return a decision.
 *
 * @author Steven van Beelen
 * @see EnqueuePolicy
 * @since 4.6.0
 */
public abstract class Decisions {

    /**
     * Construct a {@link Ignore} defining that a {@link DeadLetter dead letter} should remain in the queue.
     *
     * @param <M> The type of message contained in  the {@link DeadLetter} that's been made a decision on.
     * @return A {@link Ignore} defining that a {@link DeadLetter dead letter} should remain in the queue.
     */
    public static <M extends Message<?>> Ignore<M> ignore() {
        return new Ignore<>();
    }

    /**
     * Construct a {@link DoNotEnqueue} defining that a {@link DeadLetter dead letter} should not be enqueued at all.
     *
     * @param <M> The type of message contained in  the {@link DeadLetter} that's been made a decision on.
     * @return A {@link DoNotEnqueue} defining that a {@link DeadLetter dead letter} should not be enqueued at all.
     */
    public static <M extends Message<?>> DoNotEnqueue<M> doNotEnqueue() {
        return new DoNotEnqueue<>();
    }

    /**
     * Construct a {@link DoNotEnqueue} defining that a {@link DeadLetter dead letter} should be evicted from the
     * queue.
     *
     * @param <M> The type of message contained in  the {@link DeadLetter} that's been made a decision on.
     * @return A {@link DoNotEnqueue} defining that a {@link DeadLetter dead letter} should be evicted from the queue.
     */
    public static <M extends Message<?>> DoNotEnqueue<M> evict() {
        return new DoNotEnqueue<>();
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be enqueued.
     *
     * @param <M> The type of message contained in  the {@link DeadLetter} that's been made a decision on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be enqueued.
     */
    public static <M extends Message<?>> ShouldEnqueue<M> enqueue() {
        return enqueue(null);
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be enqueued because of
     * the given {@code enqueueCause}.
     *
     * @param enqueueCause The reason for enqueueing a {@link DeadLetter dead letter}.
     * @param <M>          The type of message contained in  the {@link DeadLetter} that's been made a decision on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be enqueued because of the
     * given {@code enqueueCause}.
     */
    public static <M extends Message<?>> ShouldEnqueue<M> enqueue(Throwable enqueueCause) {
        return enqueue(enqueueCause, DeadLetter::diagnostics);
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be enqueued because of
     * the given {@code enqueueCause}. The {@code diagnosticsBuilder} constructs
     * {@link DeadLetter#diagnostics() diagnostic} {@link MetaData} to append to the letter to enqueue.
     *
     * @param enqueueCause       The reason for enqueueing a {@link DeadLetter dead letter}.
     * @param diagnosticsBuilder A builder of {@link DeadLetter#diagnostics() diagnostic} {@link MetaData}.
     * @param <M>                The type of message contained in  the {@link DeadLetter} that's been made a decision
     *                           on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be enqueued because of the
     * given {@code enqueueCause}.
     */
    public static <M extends Message<?>> ShouldEnqueue<M> enqueue(
            Throwable enqueueCause,
            Function<DeadLetter<? extends M>, MetaData> diagnosticsBuilder
    ) {
        return new ShouldEnqueue<>(enqueueCause, diagnosticsBuilder);
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be requeued because of
     * the given {@code requeueCause}.
     *
     * @param requeueCause The reason for requeueing a {@link DeadLetter dead letter}.
     * @param <M>          The type of message contained in  the {@link DeadLetter} that's been made a decision on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be requeued because of the
     * given {@code requeueCause}.
     */
    public static <M extends Message<?>> ShouldEnqueue<M> requeue(Throwable requeueCause) {
        return requeue(requeueCause, DeadLetter::diagnostics);
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be requeued because of
     * the given {@code requeueCause}. The {@code diagnosticsBuilder} constructs
     * {@link DeadLetter#diagnostics() diagnostic} {@link MetaData} to append to the letter to requeue.
     *
     * @param requeueCause       The reason for requeueing a {@link DeadLetter dead letter}.
     * @param diagnosticsBuilder A builder of {@link DeadLetter#diagnostics() diagnostic} {@link MetaData}.
     * @param <M>                The type of message contained in  the {@link DeadLetter} that's been made a decision
     *                           on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be requeued because of the
     * given {@code requeueCause}.
     */
    public static <M extends Message<?>> ShouldEnqueue<M> requeue(
            Throwable requeueCause,
            Function<DeadLetter<? extends M>, MetaData> diagnosticsBuilder
    ) {
        return new ShouldEnqueue<>(requeueCause, diagnosticsBuilder);
    }

    private Decisions() {
        // Utility class
    }
}
