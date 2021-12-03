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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * In memory implementation of the {@link DeadLetterQueue}. Maintains a {@link PriorityQueue} per unique identifier and
 * group combination.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class InMemoryDeadLetterQueue<T extends Message<?>> implements DeadLetterQueue<T> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * {@link Clock} instance used to set the time on new {@link DeadLetterEntry}s. To fix the time while testing set
     * this value to a constant value.
     */
    public static Clock clock = Clock.systemUTC();

    private final ConcurrentNavigableMap<String, Queue<DeadLetterEntry<T>>> deadLetters = new ConcurrentSkipListMap<>();
    private final ConcurrentLinkedQueue<String> queueOrdering = new ConcurrentLinkedQueue<>();

    private final int maxEntries;
    private final long expireThreshold;

    protected InMemoryDeadLetterQueue(Builder<T> builder) {
        builder.validate();
        this.maxEntries = builder.maxEntries;
        this.expireThreshold = builder.expireThreshold;
    }

    /**
     * @param <T>
     * @return
     */
    public static <T extends Message<?>> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * @return
     */
    public static <T extends Message<?>> InMemoryDeadLetterQueue<T> defaultQueue() {
        return (InMemoryDeadLetterQueue<T>) builder().build();
    }

    @Override
    public void enqueue(String identifier, String group, T deadLetter, Throwable cause)
            throws DeadLetterQueueFilledException {
        if (isFull()) {
            throw new DeadLetterQueueFilledException("Cannot enqueue more letters since the queue is full");
        }
        logger.debug("Adding dead letter [{}] because [{}].", deadLetter, cause);

        String queueIdentifier = queueIdentifier(identifier, group);
        DeadLetterEntry<T> deadLetterEntry = buildEntry(identifier, group, deadLetter, cause);

        deadLetters.computeIfAbsent(queueIdentifier, queueId -> new PriorityQueue<>(DeadLetterEntry::compare))
                   .add(deadLetterEntry);
        if (!queueOrdering.contains(queueIdentifier)) {
            queueOrdering.add(queueIdentifier);
        }
    }

    private DeadLetterEntry<T> buildEntry(String queueIdentifier,
                                          String queueGroup,
                                          T deadLetter,
                                          Throwable cause) {
        Instant deadLettered = Clock.systemUTC().instant();
        Instant expiresAt = deadLettered.plusMillis(expireThreshold);
        return new GenericDeadLetterMessage(queueIdentifier, queueGroup, deadLetter, cause, expiresAt, deadLettered);
    }

    @Override
    public boolean contains(String identifier, String group) {
        logger.debug("Validating existence of sequence identifier [{}].", identifier);
        return deadLetters.containsKey(queueIdentifier(identifier, group));
    }

    private static String queueIdentifier(String identifier, String group) {
        return identifier + "-" + group;
    }

    @Override
    public boolean isEmpty() {
        return deadLetters.isEmpty();
    }

    @Override
    public boolean isFull() {
        return deadLetters.values()
                          .stream()
                          .mapToInt(Collection::size)
                          .count() >= maxEntries;
    }

    @Override
    public long maxSize() {
        return maxEntries;
    }

    // TODO: 29-11-21 controleren welke queue er voor t laatste geleze is
    @Override
    public DeadLetterEntry<T> peek() {
        return deadLetters.isEmpty() ? null : deadLetters.get(queueOrdering.peek()).peek();
    }

    @Override
    public void evaluationSucceeded(DeadLetterEntry<T> entry) {
        String queueIdentifier = queueIdentifier(entry.identifier(), entry.group());

        Queue<DeadLetterEntry<T>> queue = deadLetters.get(queueIdentifier);
        queue.remove(entry);

        if (queue.isEmpty()) {
            // The queue of the identifier is emptied, so we can remove the entries entirely
            deadLetters.remove(queueIdentifier);
            queueOrdering.remove(queueIdentifier);
        }
    }

    @Override
    public void evaluationFailed(DeadLetterEntry<T> entry, Throwable cause) {
        String queueIdentifier = queueIdentifier(entry.identifier(), entry.group());
        DeadLetterEntry<T> updatedEntry = buildEntry(entry.identifier(), entry.group(), entry.message(), cause);

        // Update the entry to change the expireAt and cause
        // Todo - should we adjust all the expire times? Or, should we not allow expireAt fields for enqueueIfPresent?
        // Todo - a failed reevalution shouldn't change the ordering of elements within a queue. As the ordering is based on the deadLettered() outcome, it currently is
        Queue<DeadLetterEntry<T>> queue = deadLetters.get(queueIdentifier);
        queue.remove(entry);
        queue.add(updatedEntry);

        // By removing and adding the identifier, we put it in the back on the queue
        queueOrdering.remove(queueIdentifier);
        queueOrdering.add(queueIdentifier);
    }

    /**
     * @param <T>
     */
    public static class Builder<T extends Message<?>> {

        private int maxEntries = 1024;
        private long expireThreshold = 5000;

        /**
         * @param maxEntries
         * @return
         */
        public Builder<T> maxEntries(int maxEntries) {
            assertThat(maxEntries,
                       value -> value >= 128,
                       "The maximum number of entries should be larger or equal to 128");
            this.maxEntries = maxEntries;
            return this;
        }

        /**
         * @param expireThreshold
         * @return
         */
        public Builder<T> expireThreshold(long expireThreshold) {
            assertStrictPositive(expireThreshold, "the expire threshold should be strictly positive");
            this.expireThreshold = expireThreshold;
            return this;
        }

        public InMemoryDeadLetterQueue<T> build() {
            return new InMemoryDeadLetterQueue<>(this);
        }

        protected void validate() {
            //
        }
    }

    private class GenericDeadLetterMessage implements DeadLetterEntry<T> {

        private final String identifier;
        private final String group;
        private final T message;
        private final Throwable cause;
        private final Instant expiresAt;
        private final Instant deadLettered;

        private GenericDeadLetterMessage(String identifier,
                                         String group,
                                         T message,
                                         Throwable cause,
                                         Instant expiresAt,
                                         Instant deadLettered) {
            this.identifier = identifier;
            this.group = group;
            this.message = message;
            this.cause = cause;
            this.expiresAt = expiresAt;
            this.deadLettered = deadLettered;
        }

        @Override
        public String identifier() {
            return identifier;
        }

        @Override
        public String group() {
            return group;
        }

        @Override
        public T message() {
            return message;
        }

        @Override
        public Throwable cause() {
            return cause;
        }

        @Override
        public Instant expiresAt() {
            return expiresAt;
        }

        @Override
        public Instant deadLettered() {
            return deadLettered;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            GenericDeadLetterMessage that = (GenericDeadLetterMessage) o;
            return Objects.equals(identifier, that.identifier)
                    && Objects.equals(group, that.group)
                    && Objects.equals(message, that.message)
                    && Objects.equals(cause, that.cause)
                    && Objects.equals(expiresAt, that.expiresAt)
                    && Objects.equals(deadLettered, that.deadLettered);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier, group, message, cause, expiresAt, deadLettered);
        }
    }
}
