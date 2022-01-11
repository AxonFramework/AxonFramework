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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * In memory implementation of the {@link DeadLetterQueue}.
 * <p>
 * Maintains a {@link PriorityQueue} per unique {@link QueueIdentifier} entry. The maximum amount of {@code
 * PriorityQueues} contained by this {@code DeadLetterQueue} is {@code 1024} (configurable through {@link
 * Builder#maxQueues(int)}). The maximum amount of {@link DeadLetterEntry letters} per queue also defaults to {@code
 * 1024} (configurable through {@link Builder#maxQueueSize(int)}).
 *
 * @param <T> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
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

    private final ConcurrentNavigableMap<QueueIdentifier, Deque<DeadLetterEntry<T>>> deadLetters = new ConcurrentSkipListMap<>();

    private final int maxQueues;
    private final int maxQueueSize;
    private final long expireThreshold;

    /**
     * Instantiate an in-memory {@link DeadLetterQueue} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link InMemoryDeadLetterQueue} instance.
     */
    protected InMemoryDeadLetterQueue(Builder<T> builder) {
        builder.validate();
        this.maxQueues = builder.maxQueues;
        this.maxQueueSize = builder.maxQueueSize;
        this.expireThreshold = builder.expireThreshold;
    }

    /**
     * Instantiate a builder to construct an {@link InMemoryDeadLetterQueue}.
     * <p>
     * The maximum number of queues defaults to {@code 1024}, the maximum amount of dead letters inside a queue defaults
     * to {@code 1024}, and the dead letter expire threshold defaults to {@code 5000} milliseconds.
     *
     * @param <T> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
     * @return A Builder that can construct an {@link InMemoryDeadLetterQueue}.
     */
    public static <T extends Message<?>> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Construct a default {@link InMemoryDeadLetterQueue}.
     * <p>
     * The maximum number of queues defaults to {@code 1024}, the maximum amount of dead letters inside a queue defaults
     * to {@code 1024}, and the dead letter expire threshold defaults to {@code 5000} milliseconds.
     *
     * @return A default {@link InMemoryDeadLetterQueue}.
     */
    public static <T extends Message<?>> InMemoryDeadLetterQueue<T> defaultQueue() {
        //noinspection unchecked
        return (InMemoryDeadLetterQueue<T>) builder().build();
    }

    @Override
    public DeadLetterEntry<T> enqueue(QueueIdentifier identifier,
                                      T deadLetter,
                                      Throwable cause) throws DeadLetterQueueOverflowException {
        if (isFull(identifier)) {
            throw new DeadLetterQueueOverflowException(
                    "No room left to enqueue message [" + deadLetter.toString() + "] for identifier ["
                            + identifier.combinedIdentifier() + "] since the queue is full.",
                    cause
            );
        }
        logger.debug("Adding dead letter [{}] because [{}].", deadLetter, cause);

        DeadLetterEntry<T> entry = buildEntry(identifier, deadLetter, cause);

        deadLetters.computeIfAbsent(identifier, id -> new ConcurrentLinkedDeque<>())
                   .add(entry);

        return entry;
    }

    private DeadLetterEntry<T> buildEntry(QueueIdentifier identifier, T deadLetter, Throwable cause) {
        Instant deadLettered = clock.instant();
        Instant expiresAt = deadLettered.plusMillis(expireThreshold);
        return new GenericDeadLetterMessage(identifier,
                                            deadLetter,
                                            cause,
                                            deadLettered,
                                            expiresAt,
                                            this::releaseOperation);
    }

    private void releaseOperation(DeadLetterEntry<T> deadLetter) {
        QueueIdentifier queueId = deadLetter.queueIdentifier();
        Deque<DeadLetterEntry<T>> queue = deadLetters.get(queueId);
        queue.remove(deadLetter);
        if (queue.isEmpty()) {
            deadLetters.remove(queueId);
        }
    }

    @Override
    public boolean contains(QueueIdentifier identifier) {
        logger.debug("Validating existence of sequence identifier [{}].", identifier.combinedIdentifier());
        return deadLetters.containsKey(identifier);
    }

    @Override
    public boolean isEmpty() {
        return deadLetters.isEmpty();
    }

    @Override
    public boolean isFull(QueueIdentifier queueIdentifier) {
         return maximumNumberOfQueuesReached(queueIdentifier) || maximumQueueSizeReached(queueIdentifier);
    }

    private boolean maximumNumberOfQueuesReached(QueueIdentifier queueIdentifier) {
        return !deadLetters.containsKey(queueIdentifier) && deadLetters.keySet().size() >= maxQueues;
    }

    private boolean maximumQueueSizeReached(QueueIdentifier queueIdentifier) {
        return deadLetters.containsKey(queueIdentifier) && deadLetters.get(queueIdentifier).size() >= maxQueueSize;
    }

    @Override
    public long maxQueues() {
        return maxQueues;
    }

    @Override
    public long maxQueueSize() {
        return maxQueueSize;
    }

    @Override
    public Optional<DeadLetterEntry<T>> peek(String group) {
        if (deadLetters.isEmpty()) {
            logger.debug("Queue is empty while peeking. Returning an empty optional.");
            return Optional.empty();
        }

        List<QueueIdentifier> queuesMatchingGroup = deadLetters.keySet()
                                                               .stream()
                                                               .filter(queueId -> queueId.group().equals(group))
                                                               .collect(Collectors.toList());
        if (queuesMatchingGroup.isEmpty()) {
            logger.debug(
                    "No queues present with a group matching [{}] while peeking. Returning an empty optional.", group
            );
            return Optional.empty();
        }

        DeadLetterEntry<T> letter = peekEarliestLetter(queuesMatchingGroup);
        if (letter == null) {
            return Optional.empty();
        }
        updateAndReinsert(letter);

        return Optional.of(letter);
    }

    private DeadLetterEntry<T> peekEarliestLetter(List<QueueIdentifier> queueIds) {
        Set<Map.Entry<QueueIdentifier, Deque<DeadLetterEntry<T>>>> queuesToPeek =
                deadLetters.entrySet()
                           .stream()
                           .filter(entry -> queueIds.contains(entry.getKey()))
                           .collect(Collectors.toSet());

        DeadLetterEntry<T> letter = null;
        Instant earliestQueue = Instant.MAX;
        for (Map.Entry<QueueIdentifier, Deque<DeadLetterEntry<T>>> queues : queuesToPeek) {
            DeadLetterEntry<T> peeked = queues.getValue().peek();
            if (peeked != null && peeked.expiresAt().isBefore(earliestQueue)) {
                earliestQueue = peeked.expiresAt();
                letter = peeked;
            }
        }
        return letter;
    }

    private void updateAndReinsert(DeadLetterEntry<T> letter) {
        // Reinsert the entry with an updated expireAt. This solves concurrent access of the same letter.
        Instant newExpiresAt = clock.instant().plusMillis(expireThreshold);
        DeadLetterEntry<T> updatedLetter = new GenericDeadLetterMessage(letter, newExpiresAt, this::releaseOperation);
        Queue<DeadLetterEntry<T>> queue = deadLetters.get(updatedLetter.queueIdentifier());
        queue.remove(letter);
        queue.add(updatedLetter);
    }

    @Override
    public void clear(Predicate<QueueIdentifier> queueFilter) {
        List<QueueIdentifier> queuesToClear = deadLetters.keySet()
                                                         .stream()
                                                         .filter(queueFilter)
                                                         .collect(Collectors.toList());

        queuesToClear.forEach(queueId -> {
            deadLetters.get(queueId).clear();
            deadLetters.remove(queueId);
            logger.info("Cleared out all entries for queue [{}].", queueId);
        });
    }

    /**
     * Builder class to instantiate an {@link InMemoryDeadLetterQueue}.
     * <p>
     * The maximum number of queues defaults to {@code 1024}, the maximum amount of dead letters inside a queue defaults
     * to {@code 1024}, and the dead letter expire threshold defaults to {@code 5000} milliseconds.
     *
     * @param <T> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
     */
    public static class Builder<T extends Message<?>> {

        private int maxQueues = 1024;
        private int maxQueueSize = 1024;
        private long expireThreshold = 5000;

        /**
         * Sets the maximum number of queues this {@link DeadLetterQueue} may contain. This requirement reflects itself
         * as the maximum amount of unique {@link QueueIdentifier QueueIdentifiers} referencing a dead-letter queue.
         * <p>
         * The given {@code maxQueues} is required to be a positive number, higher or equal to {@code 128}. It defaults
         * to {@code 1024}.
         *
         * @param maxQueues The maximum amount of queues for the queue under construction.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<T> maxQueues(int maxQueues) {
            assertThat(maxQueues,
                       value -> value >= 128,
                       "The maximum number of queues should be larger or equal to 128");
            this.maxQueues = maxQueues;
            return this;
        }

        /**
         * Sets the maximum amount of {@link DeadLetterEntry letters} per queue this {@link DeadLetterQueue} can store.
         * <p>
         * The given {@code maxQueueSize} is required to be a positive number, higher or equal to {@code 128}. It
         * defaults to {@code 1024}.
         *
         * @param maxQueueSize The maximum amount of {@link DeadLetterEntry letters} per queue.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<T> maxQueueSize(int maxQueueSize) {
            assertThat(maxQueueSize,
                       value -> value >= 128,
                       "The maximum number of entries in a queue should be larger or equal to 128");
            this.maxQueueSize = maxQueueSize;
            return this;
        }

        /**
         * Sets the threshold when newly {@link #enqueue(QueueIdentifier, Message, Throwable) enqueued} letters are
         * considered expired.
         * <p>
         * The earlier the {@link DeadLetterEntry#expiresAt()} date, the earlier it is returned from {@link
         * #peek(String)}. Defaults to  {@code 5000} milliseconds.
         *
         * @param expireThreshold The threshold for enqueued {@link DeadLetterEntry letters} to be considered expired.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<T> expireThreshold(long expireThreshold) {
            assertStrictPositive(expireThreshold, "the expire threshold should be strictly positive");
            this.expireThreshold = expireThreshold;
            return this;
        }

        /**
         * Initializes a {@link InMemoryDeadLetterQueue} as specified through this Builder.
         *
         * @return A {@link InMemoryDeadLetterQueue} as specified through this Builder.
         */
        public InMemoryDeadLetterQueue<T> build() {
            return new InMemoryDeadLetterQueue<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            // No assertions required, kept for overriding
        }
    }

    /**
     * Generic implementation of the {@link DeadLetterEntry} allowing any type of {@link Message} to be dead lettered.
     */
    private class GenericDeadLetterMessage implements DeadLetterEntry<T> {

        private final QueueIdentifier queueIdentifier;
        private final T message;
        private final Throwable cause;
        private final Instant expiresAt;
        private final Instant deadLettered;
        private final Consumer<GenericDeadLetterMessage> releaseOperation;

        private GenericDeadLetterMessage(DeadLetterEntry<T> entry,
                                         Instant expiresAt,
                                         Consumer<GenericDeadLetterMessage> releaseOperation) {
            this(entry.queueIdentifier(),
                 entry.message(),
                 entry.cause(),
                 entry.deadLettered(),
                 expiresAt,
                 releaseOperation);
        }

        private GenericDeadLetterMessage(QueueIdentifier queueIdentifier,
                                         T message,
                                         Throwable cause,
                                         Instant deadLettered,
                                         Instant expiresAt,
                                         Consumer<GenericDeadLetterMessage> releaseOperation) {
            this.queueIdentifier = queueIdentifier;
            this.message = message;
            this.cause = cause;
            this.deadLettered = deadLettered;
            this.expiresAt = expiresAt;
            this.releaseOperation = releaseOperation;
        }

        @Override
        public QueueIdentifier queueIdentifier() {
            return queueIdentifier;
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
        public Instant deadLettered() {
            return deadLettered;
        }

        @Override
        public Instant expiresAt() {
            return expiresAt;
        }

        @Override
        public void release() {
            releaseOperation.accept(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            // Validation does not include the expiresAt and releaseOperation, to allow stable removal of entries
            //noinspection unchecked
            GenericDeadLetterMessage that = (GenericDeadLetterMessage) o;
            return Objects.equals(queueIdentifier, that.queueIdentifier)
                    && Objects.equals(message, that.message)
                    && Objects.equals(cause, that.cause)
                    && Objects.equals(deadLettered, that.deadLettered);
        }

        @Override
        public int hashCode() {
            return Objects.hash(queueIdentifier, message, cause, expiresAt, deadLettered, releaseOperation);
        }

        @Override
        public String toString() {
            return "DeadLetterEntry{" +
                    "queueIdentifier=" + queueIdentifier +
                    ", message=" + message +
                    ", cause=" + cause +
                    ", deadLettered=" + deadLettered +
                    ", expiresAt=" + expiresAt +
                    '}';
        }
    }
}
