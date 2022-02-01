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

import java.time.Instant;

/**
 * Entry describing a dead-lettered {@link Message}.
 * <p>
 * The time of storing the {@link #message()} is kept through {@link #deadLettered()}. This letter can be regarded for
 * evaluation once the {@link #expiresAt()} time is reached. Upon successful evaluation the entry can be cleared through
 * {@link #acknowledge()}. The {@link #evict()} method should be used to remove an entry regardless of any evaluation.
 *
 * @param <T> The type of {@link Message} represented by this entry.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public interface DeadLetterEntry<T extends Message<?>> {

    /**
     * The {@link QueueIdentifier} this dead-letter belongs to.
     *
     * @return The {@link QueueIdentifier} this dead-letter belongs to
     */
    QueueIdentifier queueIdentifier();

    /**
     * The {@link Message} of type {@code T} contained in this entry.
     *
     * @return The {@link Message} of type {@code T} contained in this entry.
     */
    T message();

    /**
     * The cause for the {@link #message()} to be dead-lettered.
     *
     * @return The cause for the {@link #message()} to be dead-lettered
     */
    Throwable cause();

    /**
     * The moment in time when the {@link #message()} was dead-lettered.
     *
     * @return The moment in time when the {@link #message()} was dead-lettered.
     */
    Instant deadLettered();

    /**
     * The moment in time when this letter may be evaluated again. Will equal the {@link #deadLettered()} value if this
     * entry is enqueued as part of a sequence.
     *
     * @return The moment in time when this letter may be evaluated.
     */
    Instant expiresAt();

    /**
     * The number of retries this {@link DeadLetterEntry dead-letter} has gone through.
     *
     * @return The number of retries this {@link DeadLetterEntry dead-letter} has gone through.
     */
    int numberOfRetries();

    /**
     * Acknowledges this {@link DeadLetterEntry dead-letter} as successfully evaluated. This operation will remove the
     * entry from its queue.
     */
    void acknowledge();

    /**
     * Evict this {@link DeadLetterEntry dead-letter} from the queue it originates from. This operation will remove the
     * entry from its queue. It is recommended to use this method to remove an entry if it cannot be {@link
     * #acknowledge() acknowledged} anymore.
     */
    void evict();

    /**
     * Compares two {@link DeadLetterEntry dead-letters} with one another, based on when they {@link #expiresAt()}.
     *
     * @param first  The first {@link DeadLetterEntry dead-letter} to compare with.
     * @param second The second {@link DeadLetterEntry dead-letter} to compare with.
     * @return The result of {@link Instant#compareTo(Instant)} between the {@code first} and {@code second} {@link
     * DeadLetterEntry dead-letter}.
     */
    static int compare(DeadLetterEntry<?> first, DeadLetterEntry<?> second) {
        return first.expiresAt().compareTo(second.expiresAt());
    }
}
