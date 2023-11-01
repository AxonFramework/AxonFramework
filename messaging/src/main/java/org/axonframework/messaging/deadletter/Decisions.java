/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;

import java.util.function.Function;

/**
 * Utility class providing a number of reasonable {@link EnqueueDecision EnqueueDecisions}. Can, for example, be used by
 * an {@link EnqueuePolicy} to return a decision.
 * <p>
 * Note that the {@code EnqueueDecisions} are <em>only</em> used for deciding if to enqueue or requeue a letter, and
 * nothing more.
 *
 * @author Steven van Beelen
 * @see EnqueuePolicy
 * @since 4.6.0
 */
public abstract class Decisions {

    /**
     * Construct an {@link Ignore} defining that a {@link DeadLetter dead letter} should remain in the queue.
     * <p>
     * Note that the result is <em>only</em> used to define the letter should remain in the queue, and nothing more.
     *
     * @param <M> The type of message contained in the {@link DeadLetter} that's been made a decision on.
     * @return An {@link Ignore} defining that a {@link DeadLetter dead letter} should remain in the queue.
     */
    public static <M extends Message<?>> Ignore<M> ignore() {
        return new Ignore<>();
    }

    /**
     * Construct a {@link DoNotEnqueue} defining that a {@link DeadLetter dead letter} should not be enqueued at all.
     * <p>
     * Note that the result is <em>only</em> used to define the letter should not be enqueued, and nothing more.
     *
     * @param <M> The type of message contained in the {@link DeadLetter} that's been made a decision on.
     * @return A {@link DoNotEnqueue} defining that a {@link DeadLetter dead letter} should not be enqueued at all.
     */
    public static <M extends Message<?>> DoNotEnqueue<M> doNotEnqueue() {
        return new DoNotEnqueue<>();
    }

    /**
     * Construct a {@link DoNotEnqueue} defining that a {@link DeadLetter dead letter} should be evicted from the
     * queue.
     * <p>
     * Note that the result is <em>only</em> used to define the letter should be evicted from the queue, and nothing
     * more.
     *
     * @param <M> The type of message contained in the {@link DeadLetter} that's been made a decision on.
     * @return A {@link DoNotEnqueue} defining that a {@link DeadLetter dead letter} should be evicted from the queue.
     */
    public static <M extends Message<?>> DoNotEnqueue<M> evict() {
        return new DoNotEnqueue<>();
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be enqueued.
     * <p>
     * Note that the result is <em>only</em> used to define the letter should be enqueued, and nothing more.
     *
     * @param <M> The type of message contained in the {@link DeadLetter} that's been made a decision on.
     * @return A {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be enqueued.
     */
    public static <M extends Message<?>> ShouldEnqueue<M> enqueue() {
        return enqueue(null);
    }

    /**
     * Construct a {@link ShouldEnqueue} defining that a {@link DeadLetter dead letter} should be enqueued because of
     * the given {@code enqueueCause}.
     * <p>
     * Note that the result is <em>only</em> used to define the letter should be enqueued with the given
     * {@code enqueueCause}, and nothing more.
     *
     * @param enqueueCause The reason for enqueueing a {@link DeadLetter dead letter}.
     * @param <M>          The type of message contained in the {@link DeadLetter} that's been made a decision on.
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
     * <p>
     * Note that the result is <em>only</em> used to define the letter should be enqueued with the given
     * {@code enqueueCause} and diagnostics, and nothing more.
     *
     * @param enqueueCause       The reason for enqueueing a {@link DeadLetter dead letter}.
     * @param diagnosticsBuilder A builder of {@link DeadLetter#diagnostics() diagnostic} {@link MetaData}.
     * @param <M>                The type of message contained in the {@link DeadLetter} that's been made a decision
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
     * <p>
     * Note that the result is <em>only</em> used to define the letter should be requeued with the given
     * {@code requeueCause}, and nothing more.
     *
     * @param requeueCause The reason for requeueing a {@link DeadLetter dead letter}.
     * @param <M>          The type of message contained in the {@link DeadLetter} that's been made a decision on.
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
     * <p>
     * Note that the result is <em>only</em> used to define the letter should be requeued with the given
     * {@code requeueCause} and diagnostics, and nothing more.
     *
     * @param requeueCause       The reason for requeueing a {@link DeadLetter dead letter}.
     * @param diagnosticsBuilder A builder of {@link DeadLetter#diagnostics() diagnostic} {@link MetaData}.
     * @param <M>                The type of message contained in the {@link DeadLetter} that's been made a decision
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
