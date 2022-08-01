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
     * Construct a {@link IgnoreDecision} defining that a {@link DeadLetter dead-letter} should remain in the queue.
     *
     * @param <D> An implementation of {@link DeadLetter} that's been made a decision on.
     * @return A {@link IgnoreDecision} defining that a {@link DeadLetter dead-letter} should remain in the queue.
     */
    public static <D extends DeadLetter<? extends Message<?>>> IgnoreDecision<D> ignore() {
        return new IgnoreDecision<>();
    }

    /**
     * Construct a {@link DoNotEnqueue} defining that a {@link DeadLetter dead-letter} should not be enqueued at all.
     *
     * @param <D> An implementation of {@link DeadLetter} that's been made a decision on.
     * @return A {@link DoNotEnqueue} defining that a {@link DeadLetter dead-letter} should not be enqueued at all.
     */
    public static <D extends DeadLetter<? extends Message<?>>> DoNotEnqueue<D> doNotEnqueue() {
        return new DoNotEnqueue<>();
    }

    /**
     * Construct a {@link DoNotEnqueue} defining that a {@link DeadLetter dead-letter} should be evicted from the
     * queue.
     *
     * @param <D> An implementation of {@link DeadLetter} that's been made a decision on.
     * @return A {@link DoNotEnqueue} defining that a {@link DeadLetter dead-letter} should be evicted from the queue.
     */
    public static <D extends DeadLetter<? extends Message<?>>> DoNotEnqueue<D> evict() {
        return new DoNotEnqueue<>();
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be enqueued.
     *
     * @param <D> An implementation of {@link DeadLetter} that's been made a decision on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be enqueued.
     */
    public static <D extends DeadLetter<?>> ShouldEnqueue<D> enqueue() {
        return enqueue(null);
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be enqueued because of
     * the given {@code enqueueCause}.
     *
     * @param enqueueCause The reason for enqueueing a {@link DeadLetter dead-letter}.
     * @param <D>          An implementation of {@link DeadLetter} that's been made a decision on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be enqueued because of the
     * given {@code enqueueCause}.
     */
    public static <D extends DeadLetter<?>> ShouldEnqueue<D> enqueue(Throwable enqueueCause) {
        return enqueue(enqueueCause, letter -> MetaData.emptyInstance());
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be enqueued because of
     * the given {@code enqueueCause}. The {@code diagnosticsBuilder} constructs
     * {@link DeadLetter#diagnostic() diagnostic} {@link MetaData} to append to the letter to enqueue.
     *
     * @param enqueueCause       The reason for enqueueing a {@link DeadLetter dead-letter}.
     * @param diagnosticsBuilder A builder of {@link DeadLetter#diagnostic() diagnostic} {@link MetaData}.
     * @param <D>                An implementation of {@link DeadLetter} that's been made a decision on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be enqueued because of the
     * given {@code enqueueCause}.
     */
    public static <D extends DeadLetter<?>> ShouldEnqueue<D> enqueue(Throwable enqueueCause,
                                                                     Function<D, MetaData> diagnosticsBuilder) {
        return new ShouldEnqueue<>(enqueueCause, diagnosticsBuilder);
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be requeued because of
     * the given {@code requeueCause}.
     *
     * @param requeueCause The reason for requeueing a {@link DeadLetter dead-letter}.
     * @param <D>          An implementation of {@link DeadLetter} that's been made a decision on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be requeued because of the
     * given {@code requeueCause}.
     */
    public static <D extends DeadLetter<?>> ShouldEnqueue<D> requeue(Throwable requeueCause) {
        return requeue(requeueCause, letter -> MetaData.emptyInstance());
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be requeued because of
     * the given {@code requeueCause}. The {@code diagnosticsBuilder} constructs
     * {@link DeadLetter#diagnostic() diagnostic} {@link MetaData} to append to the letter to requeue.
     *
     * @param requeueCause       The reason for requeueing a {@link DeadLetter dead-letter}.
     * @param diagnosticsBuilder A builder of {@link DeadLetter#diagnostic() diagnostic} {@link MetaData}.
     * @param <D>                An implementation of {@link DeadLetter} that's been made a decision on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead-letter} should be requeued because of the
     * given {@code requeueCause}.
     */
    public static <D extends DeadLetter<?>> ShouldEnqueue<D> requeue(Throwable requeueCause,
                                                                     Function<D, MetaData> diagnosticsBuilder) {
        return new ShouldEnqueue<>(requeueCause, diagnosticsBuilder);
    }

    private Decisions() {
        // Utility class
    }
}
