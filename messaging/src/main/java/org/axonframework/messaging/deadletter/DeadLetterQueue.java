/*
 * Copyright (c) 2010-2021. Axon Framework
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

/**
 * @author Steven van Beelen
 * @since 4.6.0
 */
public interface DeadLetterQueue<T extends Message<?>> {

    /**
     * Add a {@link Message} to this queue. The {@code deadLetter} will be FIFO ordered with all other dead letters
     * having the same {@code identifier} and {@code group} combination.
     *
     * @param identifier the identifier of given {@code deadLetter}
     * @param group      the group the {@code deadLetter} originates from
     * @param deadLetter the {@link Message} to add to this queue
     * @param cause      the cause for enqueueing the given {@code deadLetter}
     */
    void enqueue(String identifier, String group, T deadLetter, Throwable cause) throws DeadLetterQueueFilledException;

    /**
     * Adds the result of the given {@code deadLetterSupplier} if this queue contains the given {@code
     * sequenceIdentifier}. If there's no queue for the {@code deadLetter} it is ignored.
     *
     * @param identifier the identifier of given {@code message}. Used ot validate if the given {@code message} should
     *                   be enqueued
     * @param group      the group the {@code deadLetter} originates from. Used ot validate if the given {@code message}
     *                   should be enqueued
     * @param message    the {@link Message} validated if it should be enqueued
     * @return {@code true} if the {@code message} is added, {@code false} otherwise
     */
    default boolean enqueueIfPresent(String identifier, String group, T message) {
        if (!isEmpty() && !isFull() && contains(identifier, group)) {
            enqueue(identifier, group, message, null);
            return true;
        }
        return false;
    }

    /**
     * Check whether there's a FIFO ordered queue of {@link DeadLetterEntry} instances with the given {@code
     * identifier}.
     *
     * @param identifier the identifier used to validate for contained {@link DeadLetterEntry} instances
     * @return {@code true} if the identifiers has {@link DeadLetterEntry} instances in this queue, {@code false}
     * otherwise
     */
    boolean contains(String identifier, String group);

    /**
     * Validates whether this queue is empty.
     *
     * @return {@code true} if this queue does not contain any {@link DeadLetterEntry} instances, {@code false}
     * otherwise
     */
    boolean isEmpty();

    /**
     * Validates whether this queue is full. When full, no new messages can be dead lettered.
     *
     * @return {@code true} if this queue is full, {@code false} otherwise
     */
    boolean isFull();

    /**
     *
     * @return
     */
    long maxSize();

    /**
     * Peeks the most recently introduced {@link DeadLetterEntry} instances.
     *
     * @return
     */
    DeadLetterEntry<T> peek();

    /**
     *
     * @param deadLetter
     */
    void evaluationSucceeded(DeadLetterEntry<T> deadLetter);

    /**
     *
     * @param deadLetter
     * @param cause
     */
    void evaluationFailed(DeadLetterEntry<T> deadLetter, Throwable cause);
}
