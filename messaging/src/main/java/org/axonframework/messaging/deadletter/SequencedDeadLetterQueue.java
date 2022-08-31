/*
 * Copyright (c) 2010-2022. Axon Framework
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

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

/**
 * Interface describing the required functionality for a dead letter queue. Contains several FIFO-ordered sequences of
 * dead-letters.
 * <p>
 * The contained sequences are uniquely identifiable through the "sequence identifier." Dead-letters are kept in the
 * form of a {@link DeadLetter}. It is highly recommended to use the {@link #process(Function) process operation} (or
 * any of its variants) to consume letters from the queue for retrying. This method ensure sequences cannot be
 * concurrently accessed, thus protecting the user against handling messages out of order.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this queue.
 * @author Steven van Beelen
 * @author Allard Buijze
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @see DeadLetter
 * @since 4.6.0
 */
public interface SequencedDeadLetterQueue<M extends Message<?>> {

    /**
     * Enqueues a {@link DeadLetter dead-letter} containing an implementation of {@code M} to this queue.
     * <p>
     * The {@code dead-letter} will be appended to a sequence depending on the {@code sequenceIdentifier}. If there is
     * no sequence yet, it will construct one.
     *
     * @param sequenceIdentifier The identifier of the sequence the {@code letter} belongs to.
     * @param letter             The {@link DeadLetter} to enqueue.
     * @throws DeadLetterQueueOverflowException Thrown when this queue {@link #isFull(Object) is full}.
     */
    void enqueue(@Nonnull Object sequenceIdentifier,
                 @Nonnull DeadLetter<? extends M> letter) throws DeadLetterQueueOverflowException;

    /**
     * Enqueue the result of the given {@code letterBuilder} only if there already are other
     * {@link DeadLetter dead-letters} with the same {@code sequenceIdentifier} present in this queue.
     *
     * @param sequenceIdentifier The identifier of the sequence to store the result of the {@code letterBuilder} in.
     * @param letterBuilder      The {@link DeadLetter} builder constructing the letter to enqueue. Only invoked if the
     *                           given {@code sequenceIdentifier} is contained.
     * @return A {@code true} if there are {@link DeadLetter dead-letters} for the given {@code sequenceIdentifier} and
     * thus the {@code letterBuilder's} outcome is inserted. Otherwise {@code false} is returned.
     * @throws DeadLetterQueueOverflowException Thrown when this queue is {@link #isFull(Object)} for the given
     *                                          {@code sequenceIdentifier}.
     */
    default boolean enqueueIfPresent(
            @Nonnull Object sequenceIdentifier,
            @Nonnull Supplier<DeadLetter<? extends M>> letterBuilder
    ) throws DeadLetterQueueOverflowException {
        if (!contains(sequenceIdentifier)) {
            return false;
        }
        enqueue(sequenceIdentifier, letterBuilder.get());
        return true;
    }

    /**
     * Evict the given {@code letter} from this queue. Nothing happens if the {@link DeadLetter dead-letter} does not
     * exist in this queue.
     *
     * @param letter The {@link DeadLetter dead-letter} to evict from this queue.
     */
    void evict(DeadLetter<? extends M> letter);

    /**
     * Reenters the given {@code letter}, updating the contents with the {@code letterUpdater}. This method should be
     * invoked if {@link #process(Function) processing} decided to keep the letter in the queue.
     * <p>
     * This operation adjusts the {@link DeadLetter#lastTouched()}. It may adjust the {@link DeadLetter#cause()} and
     * {@link DeadLetter#diagnostics()}, depending on the given {@code letterUpdater}.
     *
     * @param letter        The {@link DeadLetter dead-letter} to reenter in this queue.
     * @param letterUpdater A {@link UnaryOperator lambda} taking in the given {@code letter} and updating the entry for
     *                      requeueing. This may adjust the {@link DeadLetter#cause()} and
     *                      {@link DeadLetter#diagnostics()}, for example.
     * @throws NoSuchDeadLetterException Thrown if the given {@code letter} does not exist in the queue.
     */
    void requeue(@Nonnull DeadLetter<? extends M> letter,
                 @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater) throws NoSuchDeadLetterException;

    /**
     * Check whether there's a sequence of {@link DeadLetter dead-letters} for the given {@code sequenceIdentifier}.
     *
     * @param sequenceIdentifier The identifier used to validate for contained {@link DeadLetter dead-letters}
     *                           instances.
     * @return {@code true} if there are {@link DeadLetter dead-letters} present for the given
     * {@code sequenceIdentifier}, {@code false} otherwise.
     */
    boolean contains(@Nonnull Object sequenceIdentifier);

    /**
     * Return all the {@link DeadLetter dead-letters} for the given {@code sequenceIdentifier} in insert order.
     *
     * @param sequenceIdentifier The identifier of the sequence of {@link DeadLetter dead-letters }to return.
     * @return All the {@link DeadLetter dead-letters} for the given {@code sequenceIdentifier} in insert order.
     */
    Iterable<DeadLetter<? extends M>> deadLetterSequence(@Nonnull Object sequenceIdentifier);

    /**
     * Return all {@link DeadLetter dead-letter} sequences held by this queue. The sequences are not necessarily
     * returned in insert order.
     *
     * @return All {@link DeadLetter dead-letter} sequences held by this queue.
     */
    Iterable<Iterable<DeadLetter<? extends M>>> deadLetters();

    /**
     * Validates whether this queue is full for the given {@code sequenceIdentifier}.
     * <p>
     * This method returns {@code true} either when {@link #maxSequences()} is reached or when the
     * {@link #maxSequenceSize()} is reached. The former dictates no new identifiable sequences can be introduced. The
     * latter that the sequence of the given {@code sequenceIdentifier} is full.
     *
     * @param sequenceIdentifier The identifier of the sequence to validate for.
     * @return {@code true} either when {@link #maxSequences()} is reached or when the {@link #maxSequenceSize()} is
     * reached. Returns {@code false} otherwise.
     */
    boolean isFull(@Nonnull Object sequenceIdentifier);

    /**
     * Returns the number of dead-letters contained in this queue.
     *
     * @return The number of dead-letters contained in this queue.
     */
    long size();

    /**
     * Returns the number of dead-letters for the sequence matching the given {@code sequenceIdentifier} contained in
     * this queue.
     * <p>
     * Note that there's a window of opportunity where the size might exceed the {@link #maxSequenceSize()} value to
     * accompany concurrent usage.
     *
     * @param sequenceIdentifier The identifier of the sequence to retrieve the size from.
     * @return The number of dead-letters for the sequence matching the given {@code sequenceIdentifier}.
     */
    long sequenceSize(@Nonnull Object sequenceIdentifier);

    /**
     * Returns the number of unique sequences contained in this queue.
     * <p>
     * Note that there's a window of opportunity where the size might exceed the {@link #maxSequences()} value to
     * accompany concurrent usage of this dead letter queue.
     *
     * @return The number of unique sequences contained in this queue.
     */
    long amountOfSequences();

    /**
     * The maximum number of distinct sequences this dead letter queue can hold. This comes down to the maximum number
     * of unique "sequence identifiers" stored.
     * <p>
     * Note that there's a window of opportunity where the queue might exceed the {@code maxSequences} value to
     * accompany concurrent usage of this dead letter queue.
     *
     * @return The maximum number of distinct sequences this dead letter queue can hold.
     */
    long maxSequences();

    /**
     * The maximum number of letters a single sequence can contain. A single sequence is referenced based on it's
     * "sequence identifier".
     * <p>
     * Note that there's a window of opportunity where the queue might exceed the {@code maxSequenceSize} value to
     * accompany concurrent usage.
     *
     * @return The maximum number of letters a single sequence can contain.
     */
    long maxSequenceSize();

    /**
     * Process a sequence of enqueued {@link DeadLetter dead-letters} through the given {@code processingTask} matching
     * the {@code sequenceFilter}. Will pick the oldest available sequence based on the {@link DeadLetter#lastTouched()}
     * field from every sequence's first entry.
     * <p>
     * Note that only a <em>single</em> matching sequence is processed! Furthermore, only the first dead-letter is
     * validated, because it is the blocker for the processing of the rest of the sequence.
     * <p>
     * Uses the {@link EnqueueDecision} returned by the {@code processingTask} to decide whether to
     * {@link #evict(DeadLetter)} or {@link #requeue(DeadLetter, UnaryOperator)} a dead-letter from the selected
     * sequence. The {@code processingTask} is invoked as long as letters are present in the selected sequence and the
     * result of processing returns {@code false} for {@link EnqueueDecision#shouldEnqueue()} decision. The latter means
     * the dead-letter should be evicted.
     * <p>
     * This operation protects against concurrent invocations of the {@code processingTask} on the filtered sequence.
     * Doing so ensure enqueued messages are handled in order.
     *
     * @param sequenceFilter A {@link Predicate lambda} selecting the sequences within this queue to process with the
     *                       {@code processingTask}.
     * @param processingTask A function processing a {@link DeadLetter dead-letter} implementation. Returns a
     *                       {@link EnqueueDecision} used to deduce whether to {@link #evict(DeadLetter)} or
     *                       {@link #requeue(DeadLetter, UnaryOperator)} the dead-letter.
     * @return {@code true} if an entire sequence of {@link DeadLetter dead-letters} was processed successfully,
     * {@code false} otherwise. This means the {@code processingTask} processed all {@link DeadLetter dead-letters} of a
     * sequence and the outcome was to evict each instance.
     */
    boolean process(@Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
                    @Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask);

    /**
     * Process a sequence of enqueued {@link DeadLetter dead-letters} with the given {@code processingTask}. Will pick
     * the oldest available sequence based on the {@link DeadLetter#lastTouched()} field from every sequence's first
     * entry.
     * <p>
     * Note that only a <em>single</em> matching sequence is processed!
     * <p>
     * Uses the {@link EnqueueDecision} returned by the {@code processingTask} to decide whether to
     * {@link #evict(DeadLetter)} or {@link #requeue(DeadLetter, UnaryOperator)} the dead-letter. The
     * {@code processingTask} is invoked as long as letters are present in the selected sequence and the result of
     * processing returns {@code false} for {@link EnqueueDecision#shouldEnqueue()} decision. The latter means the
     * dead-letter should be evicted.
     * <p>
     * This operation protects against concurrent invocations of the {@code processingTask} on the filtered sequence. *
     * Doing so ensure enqueued messages are handled in order.
     *
     * @param processingTask A function processing a {@link DeadLetter dead-letter} implementation. Returns a
     *                       {@link EnqueueDecision} used to deduce whether to {@link #evict(DeadLetter)} or
     *                       {@link #requeue(DeadLetter, UnaryOperator)} the dead-letter.
     * @return {@code true} if an entire sequence of {@link DeadLetter dead-letters} was processed successfully,
     * {@code false} otherwise. This means the {@code processingTask} processed all {@link DeadLetter dead-letters} of a
     * sequence and the outcome was to evict each instance.
     */
    default boolean process(@Nonnull Function<DeadLetter<? extends M>, EnqueueDecision<M>> processingTask) {
        return process(letter -> true, processingTask);
    }

    /**
     * Clears out all {@link DeadLetter dead-letters} present in this queue.
     */
    void clear();
}
