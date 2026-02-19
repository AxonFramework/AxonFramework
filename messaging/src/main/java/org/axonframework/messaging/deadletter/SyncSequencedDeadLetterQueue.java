/*
 * Copyright (c) 2010-2026. Axon Framework
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

import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.Message;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Synchronous interface describing the required functionality for a dead letter queue. Contains several FIFO-ordered
 * sequences of dead letters.
 * <p>
 * The contained sequences are uniquely identifiable through the "sequence identifier." Dead-letters are kept in the
 * form of a {@link DeadLetter}. It is highly recommended to use the
 * {@link #process(Predicate, Function, ProcessingContext) process operation} (or any of its variants) to consume letters from the queue
 * for retrying. This method ensures sequences cannot be concurrently accessed, thus protecting the user against
 * handling messages out of order.
 * <p>
 * This synchronous interface is intended for implementations that perform blocking I/O operations, such as JPA or
 * JDBC-based queues. These implementations can be wrapped with {@link SyncToAsyncDeadLetterQueueAdapter} to provide the
 * asynchronous {@link SequencedDeadLetterQueue} interface.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead letters} within this queue.
 * @author Steven van Beelen
 * @author Allard Buijze
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Mateusz Nowak
 * @see DeadLetter
 * @see SequencedDeadLetterQueue
 * @see SyncToAsyncDeadLetterQueueAdapter
 * @since 5.0.0
 */
public interface SyncSequencedDeadLetterQueue<M extends Message> {

    /**
     * Enqueues a {@link DeadLetter dead letter} containing an implementation of {@code M} to this queue.
     * <p>
     * The {@code dead letter} will be appended to a sequence depending on the {@code sequenceIdentifier}. If there is
     * no sequence yet, it will construct one.
     *
     * @param sequenceIdentifier The identifier of the sequence the {@code letter} belongs to.
     * @param letter             The {@link DeadLetter} to enqueue.
     * @param context            The processing context in which the dead letters are processed.
     * @throws DeadLetterQueueOverflowException when this queue {@link #isFull(Object, ProcessingContext) is full}.
     */
    void enqueue(@Nonnull Object sequenceIdentifier,
                 @Nonnull DeadLetter<? extends M> letter,
                 @Nullable ProcessingContext context) throws DeadLetterQueueOverflowException;

    /**
     * Enqueue the result of the given {@code letterBuilder} only if there already are other
     * {@link DeadLetter dead letters} with the same {@code sequenceIdentifier} present in this queue.
     *
     * @param sequenceIdentifier The identifier of the sequence to store the result of the {@code letterBuilder} in.
     * @param letterBuilder      The {@link DeadLetter} builder constructing the letter to enqueue. Only invoked if the
     *                           given {@code sequenceIdentifier} is contained.
     * @param context            The processing context in which the dead letters are processed.
     * @return {@code true} if there are {@link DeadLetter dead letters} for the given {@code sequenceIdentifier} and
     * thus the {@code letterBuilder's} outcome is inserted. Otherwise {@code false}.
     * @throws DeadLetterQueueOverflowException when this queue is {@link #isFull(Object, ProcessingContext)} for the given
     *                                          {@code sequenceIdentifier}.
     */
    default boolean enqueueIfPresent(@Nonnull Object sequenceIdentifier,
                                     @Nonnull Supplier<DeadLetter<? extends M>> letterBuilder, // TODO #3517 - BiFunction accepts the context?
                                     @Nullable ProcessingContext context) throws DeadLetterQueueOverflowException {
        if (!contains(sequenceIdentifier, context)) {
            return false;
        }
        enqueue(sequenceIdentifier, letterBuilder.get(), context);
        return true;
    }

    /**
     * Evict the given {@code letter} from this queue. Nothing happens if the {@link DeadLetter dead letter} does not
     * exist in this queue.
     *
     * @param letter  The {@link DeadLetter dead letter} to evict from this queue.
     * @param context The processing context in which the dead letters are processed.
     */
    void evict(@Nonnull DeadLetter<? extends M> letter, @Nullable ProcessingContext context);

    /**
     * Reenters the given {@code letter}, updating the contents with the {@code letterUpdater}. This method should be
     * invoked if {@link #process(Predicate, Function, ProcessingContext) processing} decided to keep the letter in the queue.
     * <p>
     * This operation adjusts the {@link DeadLetter#lastTouched()}. It may adjust the {@link DeadLetter#cause()} and
     * {@link DeadLetter#diagnostics()}, depending on the given {@code letterUpdater}.
     *
     * @param letter        The {@link DeadLetter dead letter} to reenter in this queue.
     * @param letterUpdater A {@link UnaryOperator lambda} taking in the given {@code letter} and updating the entry for
     *                      requeueing. This may adjust the {@link DeadLetter#cause()} and
     *                      {@link DeadLetter#diagnostics()}, for example.
     * @param context       The processing context in which the dead letters are processed.
     * @throws NoSuchDeadLetterException if the given {@code letter} does not exist in the queue.
     */
    void requeue(@Nonnull DeadLetter<? extends M> letter,
                 @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater,
                 @Nullable ProcessingContext context
    ) throws NoSuchDeadLetterException;

    /**
     * Check whether there's a sequence of {@link DeadLetter dead letters} for the given {@code sequenceIdentifier}.
     *
     * @param sequenceIdentifier The identifier used to validate for contained {@link DeadLetter dead letters}
     *                           instances.
     * @param context            The {@link ProcessingContext} for the current unit of work, or {@code null} if not
     *                           available.
     * @return {@code true} if there are {@link DeadLetter dead letters} present for the given
     * {@code sequenceIdentifier}, {@code false} otherwise.
     */
    boolean contains(@Nonnull Object sequenceIdentifier, @Nullable ProcessingContext context);

    /**
     * Return all the {@link DeadLetter dead letters} for the given {@code sequenceIdentifier} in insert order.
     *
     * @param sequenceIdentifier The identifier of the sequence of {@link DeadLetter dead letters} to return.
     * @param context            The {@link ProcessingContext} for the current unit of work, or {@code null} if not
     *                           available.
     * @return All the {@link DeadLetter dead letters} for the given {@code sequenceIdentifier} in insert order.
     */
    @Nonnull
    Iterable<DeadLetter<? extends M>> deadLetterSequence(@Nonnull Object sequenceIdentifier,
                                                         @Nullable ProcessingContext context);

    /**
     * Return all {@link DeadLetter dead letter} sequences held by this queue. The sequences are not necessarily
     * returned in insert order.
     *
     * @param context The {@link ProcessingContext} for the current unit of work, or {@code null} if not available.
     * @return All {@link DeadLetter dead letter} sequences held by this queue.
     */
    @Nonnull
    Iterable<Iterable<DeadLetter<? extends M>>> deadLetters(@Nullable ProcessingContext context);

    /**
     * Validates whether this queue is full for the given {@code sequenceIdentifier}.
     * <p>
     * This method returns {@code true} either when the maximum amount of sequences or the maximum sequence size is
     * reached.
     *
     * @param sequenceIdentifier The identifier of the sequence to validate for.
     * @param context The {@link ProcessingContext} for the current unit of work, or {@code null} if not available.
     * @return {@code true} either when the limit of this queue is reached, {@code false} otherwise.
     */
    boolean isFull(@Nonnull Object sequenceIdentifier, @Nullable ProcessingContext context);

    /**
     * Returns the number of dead letters contained in this queue.
     *
     * @param context The {@link ProcessingContext} for the current unit of work, or {@code null} if not available.
     * @return The number of dead letters contained in this queue.
     */
    long size(@Nullable ProcessingContext context);

    /**
     * Returns the number of dead letters for the sequence matching the given {@code sequenceIdentifier} contained in
     * this queue.
     * <p>
     * Note that there's a window of opportunity where the size might exceed the maximum sequence size to accompany
     * concurrent usage.
     *
     * @param context The {@link ProcessingContext} for the current unit of work, or {@code null} if not available.
     * @param sequenceIdentifier The identifier of the sequence to retrieve the size from.
     * @return The number of dead letters for the sequence matching the given {@code sequenceIdentifier}.
     */
    long sequenceSize(@Nonnull Object sequenceIdentifier, @Nullable ProcessingContext context);

    /**
     * Returns the number of unique sequences contained in this queue.
     * <p>
     * Note that there's a window of opportunity where the size might exceed the maximum amount of sequences to
     * accompany concurrent usage of this dead letter queue.
     *
     * @param context The {@link ProcessingContext} for the current unit of work, or {@code null} if not available.
     * @return The number of unique sequences contained in this queue.
     */
    long amountOfSequences(@Nullable ProcessingContext context);

    /**
     * Process a sequence of enqueued {@link DeadLetter dead letters} through the given {@code processingTask} matching
     * the {@code sequenceFilter}. Will pick the oldest available sequence based on the {@link DeadLetter#lastTouched()}
     * field from every sequence's first entry.
     * <p>
     * Note that only a <em>single</em> matching sequence is processed! Furthermore, only the first dead letter is
     * validated, because it is the blocker for the processing of the rest of the sequence.
     * <p>
     * Uses the {@link EnqueueDecision} returned by the {@code processingTask} to decide whether to
     * {@link #evict(DeadLetter, ProcessingContext)} or {@link #requeue(DeadLetter, UnaryOperator, ProcessingContext)} a dead letter from the selected
     * sequence. The {@code processingTask} is invoked as long as letters are present in the selected sequence and the
     * result of processing returns {@code false} for {@link EnqueueDecision#shouldEnqueue()} decision. The latter means
     * the dead letter should be evicted.
     * <p>
     * This operation protects against concurrent invocations of the {@code processingTask} on the filtered sequence.
     * Doing so ensures enqueued messages are handled in order.
     *
     * @param sequenceFilter A {@link Predicate lambda} selecting the sequences within this queue to process with the
     *                       {@code processingTask}.
     * @param processingTask A function processing a {@link DeadLetter dead letter}. Returns an {@link EnqueueDecision}
     *                       used to deduce whether to {@link #evict(DeadLetter, ProcessingContext)} or
     *                       {@link #requeue(DeadLetter, UnaryOperator, ProcessingContext)} the dead letter.
     * @param context The {@link ProcessingContext} for the current unit of work, or {@code null} if not available.
     * @return {@code true} if an entire sequence of {@link DeadLetter dead letters} was processed successfully,
     * {@code false} otherwise. This means the {@code processingTask} processed all {@link DeadLetter dead letters} of a
     * sequence and the outcome was to evict each instance.
     */
    boolean process(@Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
                    @Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask,
                    @Nullable ProcessingContext context);

    /**
     * Process a sequence of enqueued {@link DeadLetter dead letters} with the given {@code processingTask}. Will pick
     * the oldest available sequence based on the {@link DeadLetter#lastTouched()} field from every sequence's first
     * entry.
     * <p>
     * Note that only a <em>single</em> matching sequence is processed!
     * <p>
     * Uses the {@link EnqueueDecision} returned by the {@code processingTask} to decide whether to
     * {@link #evict(DeadLetter, ProcessingContext)} or {@link #requeue(DeadLetter, UnaryOperator, ProcessingContext)} the dead letter. The
     * {@code processingTask} is invoked as long as letters are present in the selected sequence and the result of
     * processing returns {@code false} for {@link EnqueueDecision#shouldEnqueue()} decision. The latter means the dead
     * letter should be evicted.
     * <p>
     * This operation protects against concurrent invocations of the {@code processingTask} on the filtered sequence.
     * Doing so ensures enqueued messages are handled in order.
     *
     * @param processingTask A function processing a {@link DeadLetter dead letter}. Returns an {@link EnqueueDecision}
     *                       used to deduce whether to {@link #evict(DeadLetter, ProcessingContext)} or
     *                       {@link #requeue(DeadLetter, UnaryOperator, ProcessingContext)} the dead letter.
     * @param context The {@link ProcessingContext} for the current unit of work, or {@code null} if not available.
     * @return {@code true} if an entire sequence of {@link DeadLetter dead letters} was processed successfully,
     * {@code false} otherwise. This means the {@code processingTask} processed all {@link DeadLetter dead letters} of a
     * sequence and the outcome was to evict each instance.
     */
    default boolean process(@Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask, @Nullable ProcessingContext context) {
        return process(letter -> true, processingTask, context);
    }

    /**
     * Clears out all {@link DeadLetter dead letters} present in this queue.
     *
     * @param context The {@link ProcessingContext} for the current unit of work, or {@code null} if not available.
     */
    void clear(@Nullable ProcessingContext context);
}
