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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

/**
 * Interface describing the required functionality for a dead-letter queue. Contains not a single queue of letters, but
 * several FIFO-ordered queues.
 * <p>
 * The contained queues are uniquely identifiable through the {@link QueueIdentifier}. Dead-letters are kept in the form
 * of a {@link DeadLetter}. When retrieving letters through {@link #take(String)} for evaluation, they
 * can be removed with {@link DeadLetter#acknowledge()} after successful evaluation. Upon failure, the letter may be
 * reentered in the queue through {@link DeadLetter#requeue()}.
 * <p>
 * A callback can be configured through {@link #onAvailable(String, Runnable)} that is automatically invoked when
 * dead-letters are released and thus ready to be taken. Letter sequences may be released earlier by invoking
 * {@link #release(Predicate)}.
 *
 * @param <T> An implementation of {@link Message} that represent the dead-letter.
 * @author Steven van Beelen
 * @see QueueIdentifier
 * @see DeadLetter
 * @since 4.6.0
 */
public interface DeadLetterQueue<T extends Message<?>> {

    /**
     * Enqueues a {@link Message} to this queue. The {@code deadLetter} will be FIFO ordered with all other dead letters
     * of the same {@code identifier}.
     *
     * @param identifier The identifier of the queue to store the {@code deadLetter} in.
     * @param deadLetter The {@link Message} to enqueue.
     * @param cause      The cause for enqueueing the given {@code deadLetter}.
     * @return A {@link DeadLetter} representing the enqueued {@code deadLetter}.
     * @throws DeadLetterQueueOverflowException Thrown when this queue is {@link #isFull(QueueIdentifier)} for the given
     *                                          {@code identifier}.
     */
    DeadLetter<T> enqueue(@Nonnull QueueIdentifier identifier,
                          @Nonnull T deadLetter,
                          Throwable cause) throws DeadLetterQueueOverflowException;

    /**
     * Enqueue the given {@code message} only if there already are other {@link DeadLetter dead-letters} with the same
     * {@code identifier} present in this queue.
     *
     * @param identifier The identifier of the queue to store the {@code deadLetter} in.
     * @param message    The {@link Message} validated if it should be enqueued.
     * @return An empty {@link Optional} if there are no {@link DeadLetter dead-letters} for the given
     * {@code identifier}. A non-empty {@code Optional} is the result of the execution of
     * {@link #enqueue(QueueIdentifier, Message, Throwable)}.
     * @throws DeadLetterQueueOverflowException Thrown when this queue is {@link #isFull(QueueIdentifier)} for the given
     *                                          {@code identifier}.
     */
    default Optional<DeadLetter<T>> enqueueIfPresent(@Nonnull QueueIdentifier identifier,
                                                     @Nonnull T message) throws DeadLetterQueueOverflowException {
        if (!contains(identifier)) {
            return Optional.empty();
        }

        if (isFull(identifier)) {
            throw new DeadLetterQueueOverflowException(
                    "Unable to enqueue message [" + message + "]. "
                            + "The maximum capacity of dead lettered messages has been reached."
            );
        }

        return Optional.of(enqueue(identifier, message, null));
    }

    /**
     * Check whether there's a FIFO ordered queue of {@link DeadLetter dead-letters} for the given {@code identifier}.
     *
     * @param identifier The identifier used to validate for contained {@link DeadLetter dead-letters} instances.
     * @return {@code true} if there are {@link DeadLetter dead-letters} present for the given {@code identifier},
     * {@code false} otherwise.
     */
    boolean contains(@Nonnull QueueIdentifier identifier);

    /**
     * Return all the {@link DeadLetter dead letters} for the given {@code identifier} in insert order.
     *
     * @return All the {@link DeadLetter dead letters} for the given {@code identifier} in insert order.
     */
    Iterable<DeadLetter<T>> deadLetters(@Nonnull QueueIdentifier identifier);

    /**
     * Return all {@link DeadLetterSequence dead letter sequences} held by this queue. The sequences are not necessarily
     * returned in insert order.
     *
     * @return All {@link DeadLetterSequence dead letter sequences} held by this queue.
     */
    Iterable<DeadLetterSequence<T>> deadLetterSequences();

    /**
     * Validates whether this queue is full for the given {@link QueueIdentifier}.
     * <p>
     * This method returns {@code true} either when {@link #maxQueues()} is reached or when the {@link #maxQueueSize()}
     * is reached. The former dictates no new identifiable queues can be introduced. The latter that the queue of the
     * given {@link QueueIdentifier} is full.
     *
     * @param queueIdentifier The identifier of the queue to validate for.
     * @return {@code true} either when {@link #maxQueues()} is reached or when the {@link #maxQueueSize()} is reached.
     * Returns {@code false} otherwise.
     */
    boolean isFull(@Nonnull QueueIdentifier queueIdentifier);

    /**
     * The maximum number of distinct queues this dead-letter queue can hold. This comes down to the maximum number of
     * unique {@link QueueIdentifier QueueIdentifiers} stored.
     * <p>
     * Note that there's a window of opportunity where the queue might exceed the {@code maxQueues} value to accompany
     * concurrent usage of this dead-letter queue.
     *
     * @return The maximum number of distinct queues this dead-letter queue can hold.
     */
    long maxQueues();

    /**
     * The maximum number of letters a single queue can contain. A single queue is referenced based on it's
     * {@link QueueIdentifier}.
     * <p>
     * Note that there's a window of opportunity where the queue might exceed the {@code maxQueueSize} value to
     * accompany concurrent usage.
     *
     * @return The maximum number of letters a single queue can contain.
     */
    long maxQueueSize();

    /**
     * Take the oldest {@link DeadLetter} from this dead-letter queue for the given {@code group} that is ready to be
     * released. Letter sequences can be made available earlier through {@link #release(Predicate)} when necessary.
     * <p>
     * Upon taking, the returned {@link DeadLetter dead-letter} is kept in the queue with an updated
     * {@link DeadLetter#expiresAt()} and {@link DeadLetter#numberOfRetries()}. Doing so guards the queue against
     * concurrent take operations accidentally retrieving (and thus handling) the same letter.
     * <p>
     * Will return an {@link Optional#empty()} if there are no letters ready to be released (i.e., they have not
     * {@link DeadLetter#expiresAt() expired} yet) or present for the given {@code group}.
     *
     * @param group The group descriptor of a {@link QueueIdentifier} to take a letter for.
     * @return The oldest {@link DeadLetter} belonging to the given {@code group} from this dead-letter queue.
     * @see #release(Predicate)
     */
    Optional<DeadLetter<T>> take(@Nonnull String group);

    /**
     * Release all {@link DeadLetter dead-letters} within this queue that match the given {@code queueFilter}.
     * <p>
     * This makes the matching letters ready to be {@link #take(String) taken}. Furthermore, it signals any matching
     * (based on the {@code group} name) callbacks registered through {@link #onAvailable(String, Runnable)}.
     *
     * @param queueFilter A lambda selecting the letters within this queue to be released.
     */
    void release(@Nonnull Predicate<QueueIdentifier> queueFilter);

    /**
     * Release all {@link DeadLetter dead-letters} within this queue that match the given {@code group}.
     * <p>
     * This makes the matching letters ready to be {@link #take(String) taken}. Furthermore, it signals any matching
     * (based on the {@code group} name) callbacks registered through {@link #onAvailable(String, Runnable)}.
     *
     * @param group The group descriptor of a {@link QueueIdentifier} to release all {@link DeadLetter dead-letters}
     *              for.
     */
    default void release(@Nonnull String group) {
        release(queueIdentifier -> Objects.equals(queueIdentifier.group(), group));
    }

    /**
     * Release all {@link DeadLetter dead-letters} within this queue.
     * <p>
     * This makes the letters ready to be {@link #take(String) taken}. Furthermore, it signals any callbacks registered
     * through {@link #onAvailable(String, Runnable)}.
     */
    default void release() {
        release(letter -> true);
    }

    /**
     * Set the given {@code callback} for the given {@code group} to be invoked when {@link DeadLetter dead-letters} are
     * ready to be {@link #take(String) taken} from the queue. Dead-letters may be released earlier through
     * {@link #release(Predicate)} to automatically trigger the {@code callback} if the {@code group} matches.
     *
     * @param group    The group descriptor of a {@link QueueIdentifier} to register a {@code callback} for.
     * @param callback The operation to run whenever {@link DeadLetter dead-letters} are released and ready to be
     *                 taken.
     */
    void onAvailable(@Nonnull String group, @Nonnull Runnable callback);

    /**
     * Clears out all {@link DeadLetter dead-letters} matching the given {@link Predicate queueFilter}.
     *
     * @param queueFilter The {@link Predicate lambda} filtering the queues based on a {@link QueueIdentifier} to clear
     *                    out all {@link DeadLetter dead-letters} for.
     */
    void clear(@Nonnull Predicate<QueueIdentifier> queueFilter);

    /**
     * Clears out all {@link DeadLetter dead-letters} belonging to the given {@code group}.
     *
     * @param group The group descriptor of a {@link QueueIdentifier} to clear out all {@link DeadLetter dead-letters}
     *              for.
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

    /**
     * Shutdown this queue. Invoking this operation ensure any
     * {@link #onAvailable(String, Runnable) registered callbacks} that are active are properly stopped too.
     *
     * @return A {@link CompletableFuture} that's completed asynchronously once all active on available callbacks have
     * completed.
     */
    CompletableFuture<Void> shutdown();
}
