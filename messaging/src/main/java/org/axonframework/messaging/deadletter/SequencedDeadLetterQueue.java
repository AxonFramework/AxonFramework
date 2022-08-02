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

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface describing the required functionality for a dead letter queue. Contains several FIFO-ordered sequences of
 * dead-letters.
 * <p>
 * The contained sequences are uniquely identifiable through the {@link SequenceIdentifier}. Dead-letters are kept in
 * the form of a {@link DeadLetter}. It is highly recommended to use the {@link #process(Function) process operation}
 * (or any of its variants) to consume letters from the queue for retrying. This method ensure sequences cannot be
 * concurrently accessed, thus protecting the user against handling messages out of order.
 *
 * @param <D> An implementation of {@link DeadLetter} contained within this queue.
 * @author Steven van Beelen
 * @author Allard Buijze
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @see SequenceIdentifier
 * @see DeadLetter
 * @since 4.6.0
 */
public interface SequencedDeadLetterQueue<D extends DeadLetter<? extends Message<?>>> {

    /**
     * Enqueues a {@link DeadLetter dead-letter} implementation of type {@code D} to this queue.
     * <p>
     * The {@code dead-letter} will be appended to a sequence depending on the {@link DeadLetter#sequenceIdentifier()}.
     * If there is no sequence yet, it will construct one.
     *
     * @param letter The {@link DeadLetter} to enqueue.
     * @throws DeadLetterQueueOverflowException Thrown when this queue {@link #isFull(SequenceIdentifier) is full}.
     */
    void enqueue(@Nonnull D letter) throws DeadLetterQueueOverflowException;

    /**
     * Enqueue the result of the given {@code letterBuilder} only if there already are other
     * {@link DeadLetter dead-letters} with the same {@code identifier} present in this queue.
     * <p>
     * The {@code letterBuilder} {@link Function} should use the given {@link SequenceIdentifier}. If this is not the
     * case a {@link MismatchingSequenceIdentifierException} might be thrown.
     *
     * @param identifier    The identifier of the sequence to store the result of the {@code letterBuilder} in.
     * @param letterBuilder The {@link DeadLetter} builder constructing the letter to enqueue. Only invoked if the given
     *                      {@code identifier} is contained.
     * @return A {@code true} if there are {@link DeadLetter dead-letters} for the given {@code identifier} and thus the
     * {@code letterBuilder's} outcome is inserted. Otherwise {@code false} is returned.
     * @throws DeadLetterQueueOverflowException       Thrown when this queue is {@link #isFull(SequenceIdentifier)} for
     *                                                the given {@code identifier}.
     * @throws MismatchingSequenceIdentifierException Thrown when the given {@code identifier} does not match the
     *                                                {@link DeadLetter#sequenceIdentifier()} constructed through the
     *                                                given {@code letterBuilder}.
     */
    default boolean enqueueIfPresent(@Nonnull SequenceIdentifier identifier,
                                     @Nonnull Function<SequenceIdentifier, D> letterBuilder)
            throws DeadLetterQueueOverflowException, MismatchingSequenceIdentifierException {
        if (!contains(identifier)) {
            return false;
        }

        if (isFull(identifier)) {
            throw new DeadLetterQueueOverflowException(identifier);
        }

        D letter = letterBuilder.apply(identifier);
        if (!Objects.equals(identifier, letter.sequenceIdentifier())) {
            throw new MismatchingSequenceIdentifierException(
                    "Cannot insert letter with sequence identifier [" + letter.sequenceIdentifier()
                            + "] since the invocation assumed sequence identifier [" + identifier + "]");
        }
        enqueue(letter);
        return true;
    }

    /**
     * Evict the given {@code letter} from this queue. Nothing happens if the {@link DeadLetter dead-letter} does not
     * exist in this queue.
     *
     * @param letter The {@link DeadLetter dead-letter} to evict from this queue.
     */
    void evict(D letter);

    /**
     * Reenters the given {@code letter} due to the given {@code requeueCause}. This method should be invoked if
     * {@link #process(Function) processing} decided to keep the letter in the queue.
     * <p>
     * This operation should adjust the {@link DeadLetter#cause()} of the given {@code letter} according to the given
     * {@code requeueCause}.
     *
     * @param letter       The {@link DeadLetter dead-letter} to reenter in this queue.
     * @param requeueCause The reason for requeueing the given {@code letter}. May be {@code null} if the reason for
     *                     requeueing is not due to a failure.
     * @throws NoSuchDeadLetterException Thrown if the given {@code letter} does not exist in the queue.
     */
    void requeue(D letter, @Nullable Throwable requeueCause) throws NoSuchDeadLetterException;

    /**
     * Check whether there's a sequence of {@link DeadLetter dead-letters} for the given {@code identifier}.
     *
     * @param identifier The identifier used to validate for contained {@link DeadLetter dead-letters} instances.
     * @return {@code true} if there are {@link DeadLetter dead-letters} present for the given {@code identifier},
     * {@code false} otherwise.
     */
    boolean contains(@Nonnull SequenceIdentifier identifier);

    /**
     * Return all the {@link DeadLetter dead-letters} for the given {@code identifier} in insert order.
     *
     * @return All the {@link DeadLetter dead-letters} for the given {@code identifier} in insert order.
     */
    Iterable<D> deadLetterSequence(@Nonnull SequenceIdentifier identifier);

    /**
     * Return all {@link DeadLetter dead-letter} sequences held by this queue. The sequences are not necessarily
     * returned in insert order.
     *
     * @return All {@link DeadLetter dead-letter} sequences held by this queue.
     */
    Map<SequenceIdentifier, Iterable<D>> deadLetters();

    /**
     * Validates whether this queue is full for the given {@link SequenceIdentifier}.
     * <p>
     * This method returns {@code true} either when {@link #maxSequences()} is reached or when the
     * {@link #maxSequenceSize()} is reached. The former dictates no new identifiable sequences can be introduced. The
     * latter that the sequence of the given {@link SequenceIdentifier} is full.
     *
     * @param identifier The identifier of the sequence to validate for.
     * @return {@code true} either when {@link #maxSequences()} is reached or when the {@link #maxSequenceSize()} is
     * reached. Returns {@code false} otherwise.
     */
    boolean isFull(@Nonnull SequenceIdentifier identifier);

    /**
     * The maximum number of distinct sequences this dead letter queue can hold. This comes down to the maximum number
     * of unique {@link SequenceIdentifier} stored.
     * <p>
     * Note that there's a window of opportunity where the queue might exceed the {@code maxSequences} value to
     * accompany concurrent usage of this dead letter queue.
     *
     * @return The maximum number of distinct sequences this dead letter queue can hold.
     */
    long maxSequences();

    /**
     * The maximum number of letters a single sequence can contain. A single sequence is referenced based on it's
     * {@link SequenceIdentifier}.
     * <p>
     * Note that there's a window of opportunity where the queue might exceed the {@code maxSequenceSize} value to
     * accompany concurrent usage.
     *
     * @return The maximum number of letters a single sequence can contain.
     */
    long maxSequenceSize();

    /**
     * Process an enqueued {@link DeadLetter dead-letter} with the given {@code processingTask} which matches the given
     * {@code sequenceFilter} and {@code letterFilter}. Will pick the oldest available dead-letter based on the
     * {@link DeadLetter#lastTouched()} field, only taking the first entries per sequence into account.
     * <p>
     * Uses the {@link EnqueueDecision} returned by the {@code processingTask} to decide whether to
     * {@link #evict(DeadLetter)} or {@link #requeue(DeadLetter, Throwable)} the dead-letter.
     * <p>
     * This operation protects against concurrent invocations of the {@code processingTask} for the same
     * {@link SequenceIdentifier}. Doing so ensure enqueued messages are handled in order.
     *
     * @param sequenceFilter A {@link Predicate lambda} selecting the sequences within this queue to process with the
     *                       {@code processingTask}.
     * @param letterFilter   A {@link Predicate lambda} selecting the letters within this queue to process with the
     *                       {@code processingTask}.
     * @param processingTask A function processing a {@link DeadLetter dead-letter} implementation. Returns a
     *                       {@link EnqueueDecision} used to deduce whether to {@link #evict(DeadLetter)} or
     *                       {@link #requeue(DeadLetter, Throwable)} the dead-letter.
     * @return {@code true} if the given {@code processingTask} was invoked successfully. This means the task processed
     * a {@link DeadLetter dead-letter} and the outcome was {@link EnqueueDecision#shouldEvict() to evict} the letter.
     * Otherwise {@code false} is returned.
     */
    boolean process(@Nonnull Predicate<SequenceIdentifier> sequenceFilter,
                    @Nonnull Predicate<D> letterFilter,
                    Function<D, EnqueueDecision<D>> processingTask);

    /**
     * Process an enqueued {@link DeadLetter dead-letter} with the given {@code processingTask} which matches the given
     * {@code sequenceFilter}. Will pick the oldest available dead-letter based on the {@link DeadLetter#lastTouched()}
     * field, only taking the first entries per sequence into account.
     * <p>
     * Uses the {@link EnqueueDecision} returned by the {@code processingTask} to decide whether to
     * {@link #evict(DeadLetter)} or {@link #requeue(DeadLetter, Throwable)} the dead-letter.
     * <p>
     * This operation protects against concurrent invocations of the {@code processingTask} for the same
     * {@link SequenceIdentifier}. Doing so ensure enqueued messages are handled in order.
     *
     * @param sequenceFilter A {@link Predicate lambda} selecting the sequences within this queue to process with the
     *                       {@code processingTask}.
     * @param processingTask A function processing a {@link DeadLetter dead-letter} implementation. Returns a
     *                       {@link EnqueueDecision} used to deduce whether to {@link #evict(DeadLetter)} or
     *                       {@link #requeue(DeadLetter, Throwable)} the dead-letter.
     * @return {@code true} if the given {@code processingTask} was invoked, {@code false} otherwise.
     */
    default boolean process(@Nonnull Predicate<SequenceIdentifier> sequenceFilter,
                            @Nonnull Function<D, EnqueueDecision<D>> processingTask) {
        return process(sequenceFilter, letter -> true, processingTask);
    }

    /**
     * Process an enqueued {@link DeadLetter dead-letter} with the given {@code processingTask} which matches the given
     * {@code group}. Will pick the oldest available dead-letter based on the {@link DeadLetter#lastTouched()} field,
     * only taking the first entries per sequence into account.
     * <p>
     * Uses the {@link EnqueueDecision} returned by the {@code processingTask} to decide whether to
     * {@link #evict(DeadLetter)} or {@link #requeue(DeadLetter, Throwable)} the dead-letter.
     * <p>
     * This operation protects against concurrent invocations of the {@code processingTask} for the same
     * {@link SequenceIdentifier}. Doing so ensure enqueued messages are handled in order.
     *
     * @param group          The group descriptor of a {@link SequenceIdentifier} to process with the
     *                       {@code processingTask}.
     * @param processingTask A function processing a {@link DeadLetter dead-letter} implementation. Returns a
     *                       {@link EnqueueDecision} used to deduce whether to {@link #evict(DeadLetter)} or
     *                       {@link #requeue(DeadLetter, Throwable)} the dead-letter.
     * @return {@code true} if the given {@code processingTask} was invoked, {@code false} otherwise.
     */
    default boolean process(@Nonnull String group,
                            @Nonnull Function<D, EnqueueDecision<D>> processingTask) {
        return process(identifier -> Objects.equals(identifier.group(), group), processingTask);
    }

    /**
     * Process an enqueued {@link DeadLetter dead-letter} with the given {@code processingTask}. Will pick the oldest
     * available dead-letter based on the {@link DeadLetter#lastTouched()} field, only taking the first entries per
     * sequence into account.
     * <p>
     * Uses the {@link EnqueueDecision} returned by the {@code processingTask} to decide whether to
     * {@link #evict(DeadLetter)} or {@link #requeue(DeadLetter, Throwable)} the dead-letter.
     * <p>
     * This operation protects against concurrent invocations of the {@code processingTask} for the same
     * {@link SequenceIdentifier}. Doing so ensure enqueued messages are handled in order.
     *
     * @param processingTask A function processing a {@link DeadLetter dead-letter} implementation. Returns a
     *                       {@link EnqueueDecision} used to deduce whether to {@link #evict(DeadLetter)} or
     *                       {@link #requeue(DeadLetter, Throwable)} the dead-letter.
     * @return {@code true} if the given {@code processingTask} was invoked, {@code false} otherwise.
     */
    default boolean process(@Nonnull Function<D, EnqueueDecision<D>> processingTask) {
        return process(identifier -> true, processingTask);
    }

    /**
     * Clears out all {@link DeadLetter dead-letters} matching the given {@link Predicate sequenceFilter}.
     *
     * @param sequenceFilter The {@link Predicate lambda} filtering the sequences to clear out all
     *                       {@link DeadLetter dead-letters} for.
     */
    void clear(@Nonnull Predicate<SequenceIdentifier> sequenceFilter);

    /**
     * Clears out all {@link DeadLetter dead-letters} belonging to the given {@code group}.
     *
     * @param group The group descriptor of a {@link SequenceIdentifier} to clear out all
     *              {@link DeadLetter dead-letters} for.
     */
    default void clear(@Nonnull String group) {
        clear(identifier -> Objects.equals(identifier.group(), group));
    }

    /**
     * Clears out all {@link DeadLetter dead-letters} present in this queue.
     */
    default void clear() {
        clear(identifier -> true);
    }
}
