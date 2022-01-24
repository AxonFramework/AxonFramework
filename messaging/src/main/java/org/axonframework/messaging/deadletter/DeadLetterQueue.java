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
import java.util.function.Predicate;

/**
 * Interface describing the required functionality for a dead-letter queue. Contains not a single queue of letters, but
 * several FIFO-ordered queues.
 * <p>
 * The contained queues are uniquely identifiable through the {@link QueueIdentifier}. Dead-letters are kept in the form
 * of a {@link DeadLetterEntry DeadLetterEntries}. When retrieving letters through {@link #peek(String)} for evaluation,
 * they can be removed with {@link DeadLetterEntry#acknowledge()} to clear them from this queue.
 *
 * @param <T> An implementation of {@link Message} that represent the dead-letter.
 * @author Steven van Beelen
 * @see QueueIdentifier
 * @see DeadLetterEntry
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
     * @return A {@link DeadLetterEntry} representing the enqueued {@code deadLetter}.
     * @throws DeadLetterQueueOverflowException Thrown when this queue is {@link #isFull(QueueIdentifier)} for the given
     *                                          {@code identifier}.
     */
    DeadLetterEntry<T> enqueue(QueueIdentifier identifier,
                               T deadLetter,
                               Throwable cause) throws DeadLetterQueueOverflowException;

    /**
     * Enqueue the given {@code message} only if there already are other {@link DeadLetterEntry dead-letters} with the
     * same {@code identifier} present in this queue.
     *
     * @param identifier The identifier of the queue to store the {@code deadLetter} in.
     * @param message    The {@link Message} validated if it should be enqueued.
     * @return An empty {@link Optional} if there are no {@link DeadLetterEntry dead-letters} for the given {@code
     * identifier}. A non-empty {@code Optional} is the result of the execution of {@link #enqueue(QueueIdentifier,
     * Message, Throwable)}.
     * @throws DeadLetterQueueOverflowException Thrown when this queue is {@link #isFull(QueueIdentifier)} for the given
     *                                          {@code identifier}.
     */
    default Optional<DeadLetterEntry<T>> enqueueIfPresent(QueueIdentifier identifier,
                                                          T message) throws DeadLetterQueueOverflowException {
        if (isEmpty() || !contains(identifier)) {
            return Optional.empty();
        }

        if (isFull(identifier)) {
            throw new DeadLetterQueueOverflowException(
                    "No room left to enqueue message [" + message.toString() + "] for identifier ["
                            + identifier.combinedIdentifier() + "] since the queue is full."
            );
        }

        return Optional.of(enqueue(identifier, message, null));
    }

    /**
     * Check whether there's a FIFO ordered queue of {@link DeadLetterEntry dead-letters} for the given {@code
     * identifier}.
     *
     * @param identifier The identifier used to validate for contained {@link DeadLetterEntry dead-letters} instances.
     * @return {@code true} if there are {@link DeadLetterEntry dead-letters} present for the given {@code identifier},
     * {@code false} otherwise.
     */
    boolean contains(QueueIdentifier identifier);

    /**
     * Validates whether this queue is empty.
     *
     * @return {@code true} if this queue does not contain any {@link DeadLetterEntry dead-letters}, {@code false}
     * otherwise.
     */
    boolean isEmpty();

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
    boolean isFull(QueueIdentifier queueIdentifier);

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
     * The maximum number of entries a single queue can contain. A single queue is referenced based on it's {@link
     * QueueIdentifier}.
     * <p>
     * Note that there's a window of opportunity where the queue might exceed the {@code maxQueueSize} value to
     * accompany concurrent usage.
     *
     * @return The maximum number of entries a single queue can contain.
     */
    long maxQueueSize();

    /**
     * Peeks the oldest {@link DeadLetterEntry} from this dead-letter queue for the given {@code group}.
     * <p>
     * Upon peeking, the returned {@link DeadLetterEntry dead-letter} is automatically reentered into the queue with an
     * updated {@link DeadLetterEntry#expiresAt()}. Doing so guards the queue against concurrent peek operations
     * accidentally retrieving (and thus handling) the same letter.
     * <p>
     * Will return an {@link Optional#empty()} if there are no entries present for the given {@code group}.
     *
     * @param group The group descriptor of a {@link QueueIdentifier} to peek an entry for.
     * @return The oldest {@link DeadLetterEntry} belonging to the given {@code group} from this dead-letter queue.
     */
    Optional<DeadLetterEntry<T>> peek(String group);

    /**
     * Clears out all {@link DeadLetterEntry dead-letters} matching the given {@link Predicate queueFilter}.
     *
     * @param queueFilter The {@link Predicate lambda} filtering the queues based on a {@link QueueIdentifier} to clear
     *                    out all {@link DeadLetterEntry dead-letters} for.
     */
    void clear(Predicate<QueueIdentifier> queueFilter);

    /**
     * Clears out all {@link DeadLetterEntry dead-letters} belonging to the given {@code group}.
     *
     * @param group The group descriptor of a {@link QueueIdentifier} to clear out all {@link DeadLetterEntry
     *              dead-letters} for.
     */
    default void clear(String group) {
        clear(identifier -> Objects.equals(identifier.group(), group));
    }

    /**
     * Clears out all {@link DeadLetterEntry dead-letters} present in this queue.
     */
    default void clear() {
        clear(identifier -> true);
    }
}
