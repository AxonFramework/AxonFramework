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

import org.axonframework.common.AxonThreadFactory;
import org.axonframework.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * In memory implementation of the {@link DeadLetterQueue}.
 * <p>
 * Maintains a {@link Deque} per unique {@link QueueIdentifier}. The maximum amount of {@code Deques} contained by this
 * {@code DeadLetterQueue} is {@code 1024} (configurable through {@link Builder#maxQueues(int)}). The maximum amount of
 * {@link DeadLetter letters} per queue also defaults to {@code 1024} (configurable through
 * {@link Builder#maxQueueSize(int)}).
 *
 * @param <M> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class InMemoryDeadLetterQueue<M extends Message<?>> implements DeadLetterQueue<GenericDeadLetter<M>, M> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * {@link Clock} instance used to set the time on new {@link DeadLetter DeadLetters}. To fix the time while testing
     * set this value to a constant value.
     */
    public static Clock clock = Clock.systemUTC();

    private final Map<QueueIdentifier, Deque<GenericDeadLetter<M>>> deadLetters = new ConcurrentSkipListMap<>();
    private final Set<QueueIdentifier> takenSequences = new ConcurrentSkipListSet<>();

    private final int maxQueues;
    private final int maxQueueSize;

    /**
     * Instantiate an in-memory {@link DeadLetterQueue} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link InMemoryDeadLetterQueue} instance.
     */
    protected InMemoryDeadLetterQueue(Builder<M> builder) {
        builder.validate();
        this.maxQueues = builder.maxQueues;
        this.maxQueueSize = builder.maxQueueSize;
    }

    /**
     * Instantiate a builder to construct an {@link InMemoryDeadLetterQueue}.
     * <p>
     * The maximum number of queues defaults to {@code 1024}, the maximum amount of dead letters inside a queue defaults
     * to {@code 1024}, the dead letter expire threshold defaults to a {@link Duration} of 5000 milliseconds, and the
     * {@link ScheduledExecutorService} defaults to a {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)},
     * using an {@link AxonThreadFactory}.
     *
     * @param <M> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
     * @return A Builder that can construct an {@link InMemoryDeadLetterQueue}.
     */
    public static <M extends Message<?>> Builder<M> builder() {
        return new Builder<>();
    }

    /**
     * Construct a default {@link InMemoryDeadLetterQueue}.
     * <p>
     * The maximum number of queues defaults to {@code 1024}, the maximum amount of dead letters inside a queue defaults
     * to {@code 1024}, the dead letter expire threshold defaults to a {@link Duration} of 5000 milliseconds, and the
     * {@link ScheduledExecutorService} defaults to a {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)},
     * using an {@link AxonThreadFactory}.
     *
     * @return A default {@link InMemoryDeadLetterQueue}.
     */
    public static <M extends Message<?>> InMemoryDeadLetterQueue<M> defaultQueue() {
        //noinspection unchecked
        return (InMemoryDeadLetterQueue<M>) builder().build();
    }

    @Override
    public GenericDeadLetter<M> enqueue(@Nonnull QueueIdentifier identifier,
                                        @Nonnull M deadLetter,
                                        Throwable cause) throws DeadLetterQueueOverflowException {
        if (isFull(identifier)) {
            throw new DeadLetterQueueOverflowException(
                    "No room left to enqueue message [" + deadLetter + "] for identifier ["
                            + identifier.combinedIdentifier() + "] since the queue is full.",
                    cause
            );
        }

        if (cause != null) {
            logger.debug("Adding dead letter [{}] because [{}].", deadLetter, cause);
        } else {
            logger.debug("Adding dead letter [{}] because the queue identifier [{}] is already present.",
                         deadLetter, identifier);
        }

        // TODO: 15-07-22 this should work...right? Why do I need to cast this >_>
        GenericDeadLetter<M> letter = new GenericDeadLetter<>(identifier,
                                                              deadLetter,
                                                              cause,
                                                              clock.instant(),
                                                              this::evict,
                                                              this::release);

        enqueue(letter);
        return letter;
    }

    @Override
    public void enqueue(@Nonnull GenericDeadLetter<M> letter) throws DeadLetterQueueOverflowException {
        deadLetters.computeIfAbsent(letter.queueIdentifier(), id -> new ConcurrentLinkedDeque<>())
                   .addLast(letter);
        // TODO: 14-07-22 I'd rather move this out of this impl into a specific retryable variant that uses scheduling
//        scheduleAvailabilityCallbacks(letter);
    }


    private void evict(GenericDeadLetter<M> letter) {
        // TODO I can't make DeadLetter<M> of type D...
        QueueIdentifier queueId = letter.queueIdentifier();
        Deque<GenericDeadLetter<M>> sequence = deadLetters.get(queueId);
        // TODO: 15-07-22 this should work...right? Why do I need to cast this >_>
        sequence.remove(letter);
        logger.trace("Removed letter [{}].", letter.message().getIdentifier());
        if (sequence.isEmpty()) {
            logger.trace("Queue [{}] is empty and will be removed.", queueId);
            deadLetters.remove(queueId);
        }
        takenSequences.remove(queueId);
    }

    // TODO I can't make DeadLetter<M> of type D...
    private void release(GenericDeadLetter<M> letter) {
        takenSequences.remove(letter.queueIdentifier());
    }

    @Override
    public boolean contains(@Nonnull QueueIdentifier identifier) {
        if (logger.isDebugEnabled()) {
            logger.debug("Validating existence of sequence identifier [{}].", identifier.combinedIdentifier());
        }
        return deadLetters.containsKey(identifier);
    }

    @Override
    public Iterable<GenericDeadLetter<M>> deadLetters(@Nonnull QueueIdentifier identifier) {
        return contains(identifier) ? new ArrayList<>(deadLetters.get(identifier)) : Collections.emptyList();
    }

    @Override
    public Iterable<DeadLetterSequence<M>> deadLetterSequences() {
        return deadLetters.entrySet()
                          .stream()
                          .map(entry -> new GenericDeadLetterSequence<>(
                                  entry.getKey(), new ArrayList<>(entry.getValue())
                          ))
                          .collect(Collectors.toList());
    }

    @Override
    public boolean isFull(@Nonnull QueueIdentifier queueIdentifier) {
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
    public synchronized Optional<GenericDeadLetter<M>> take(@Nonnull String group) {
        logger.trace("Attempting to take a dead letter from the queue for [{}].", group);
        if (deadLetters.isEmpty()) {
            return Optional.empty();
        }

        List<QueueIdentifier> availableSequences = deadLetters.keySet()
                                                              .stream()
                                                              .filter(queueId -> !takenSequences.contains(queueId))
                                                              .filter(queueId -> queueId.group().equals(group))
                                                              .collect(Collectors.toList());
        if (availableSequences.isEmpty()) {
            logger.debug(
                    "No queues present with a group matching [{}] while taking. Returning an empty optional.", group
            );
            return Optional.empty();
        }

        GenericDeadLetter<M> letter = getOldestLetter(availableSequences);
        if (letter == null) {
            return Optional.empty();
        }
        takenSequences.add(letter.queueIdentifier());

        return Optional.of(letter);
    }

    private GenericDeadLetter<M> getOldestLetter(List<QueueIdentifier> queueIds) {
        Set<Map.Entry<QueueIdentifier, Deque<GenericDeadLetter<M>>>> availableSequences =
                deadLetters.entrySet()
                           .stream()
                           .filter(entry -> queueIds.contains(entry.getKey()))
                           .collect(Collectors.toSet());

        Instant current = clock.instant();
        long oldestSequence = Long.MAX_VALUE;
        GenericDeadLetter<M> oldestLetter = null;
        for (Map.Entry<QueueIdentifier, Deque<GenericDeadLetter<M>>> sequence : availableSequences) {
            GenericDeadLetter<M> letter = sequence.getValue().peekFirst();
            if (letter != null
                    && letter.enqueuedAt().toEpochMilli() <= current.toEpochMilli()
                    && letter.enqueuedAt().toEpochMilli() < oldestSequence) {
                oldestSequence = letter.enqueuedAt().toEpochMilli();
                oldestLetter = letter;
            }
        }
        return oldestLetter;
    }

    @Override
    public void clear(@Nonnull Predicate<QueueIdentifier> queueFilter) {
        List<QueueIdentifier> queuesToClear = deadLetters.keySet()
                                                         .stream()
                                                         .filter(queueFilter)
                                                         .collect(Collectors.toList());

        queuesToClear.forEach(queueId -> {
            deadLetters.get(queueId).clear();
            deadLetters.remove(queueId);
            logger.info("Cleared out all letters for queue [{}].", queueId);
        });
    }

    /**
     * Builder class to instantiate an {@link InMemoryDeadLetterQueue}.
     * <p>
     * The maximum number of queues defaults to {@code 1024}, the maximum amount of dead letters inside a queue defaults
     * to {@code 1024}, the dead letter expire threshold defaults to a {@link Duration} of 5000 milliseconds, and the
     * {@link ScheduledExecutorService} defaults to a {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)},
     * using an {@link AxonThreadFactory}.
     *
     * @param <M> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
     */
    public static class Builder<M extends Message<?>> {

        private int maxQueues = 1024;
        private int maxQueueSize = 1024;

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
        public Builder<M> maxQueues(int maxQueues) {
            assertThat(maxQueues,
                       value -> value >= 128,
                       "The maximum number of queues should be larger or equal to 128");
            this.maxQueues = maxQueues;
            return this;
        }

        /**
         * Sets the maximum amount of {@link DeadLetter letters} per queue this {@link DeadLetterQueue} can store.
         * <p>
         * The given {@code maxQueueSize} is required to be a positive number, higher or equal to {@code 128}. It
         * defaults to {@code 1024}.
         *
         * @param maxQueueSize The maximum amount of {@link DeadLetter letters} per queue.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> maxQueueSize(int maxQueueSize) {
            assertThat(maxQueueSize,
                       value -> value >= 128,
                       "The maximum number of letters in a queue should be larger or equal to 128");
            this.maxQueueSize = maxQueueSize;
            return this;
        }

        /**
         * Initializes a {@link InMemoryDeadLetterQueue} as specified through this Builder.
         *
         * @return A {@link InMemoryDeadLetterQueue} as specified through this Builder.
         */
        public InMemoryDeadLetterQueue<M> build() {
            return new InMemoryDeadLetterQueue<>(this);
        }

        protected void validate() {
            // TODO: 15-07-22 fill in comment and javadoc
        }
    }
}
