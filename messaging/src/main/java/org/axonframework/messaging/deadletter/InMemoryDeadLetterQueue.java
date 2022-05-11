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
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * In memory implementation of the {@link DeadLetterQueue}.
 * <p>
 * Maintains a {@link Deque} per unique {@link QueueIdentifier} entry. The maximum amount of {@code Deques} contained by
 * this {@code DeadLetterQueue} is {@code 1024} (configurable through {@link Builder#maxQueues(int)}). The maximum
 * amount of {@link DeadLetterEntry letters} per queue also defaults to {@code 1024} (configurable through
 * {@link Builder#maxQueueSize(int)}).
 *
 * @param <T> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class InMemoryDeadLetterQueue<T extends Message<?>> implements DeadLetterQueue<T>, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * {@link Clock} instance used to set the time on new {@link DeadLetterEntry}s. To fix the time while testing set
     * this value to a constant value.
     */
    public static Clock clock = Clock.systemUTC();

    private final Map<QueueIdentifier, Deque<DeadLetterEntry<T>>> deadLetters = new ConcurrentSkipListMap<>();
    private final Set<QueueIdentifier> takenSequences = new ConcurrentSkipListSet<>();
    private final Map<String, Runnable> availabilityCallbacks = new ConcurrentSkipListMap<>();

    private final int maxQueues;
    private final int maxQueueSize;
    private final Duration expireThreshold;
    private final ScheduledExecutorService scheduledExecutorService;
    private final boolean customExecutor;

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
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.customExecutor = builder.customExecutor;
    }

    /**
     * Instantiate a builder to construct an {@link InMemoryDeadLetterQueue}.
     * <p>
     * The maximum number of queues defaults to {@code 1024}, the maximum amount of dead letters inside a queue defaults
     * to {@code 1024}, the dead letter expire threshold defaults to a {@link Duration} of 5000 milliseconds, and the
     * {@link ScheduledExecutorService} defaults to a {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)},
     * using an {@link AxonThreadFactory}.
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
     * to {@code 1024}, the dead letter expire threshold defaults to a {@link Duration} of 5000 milliseconds, and the
     * {@link ScheduledExecutorService} defaults to a {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)},
     * using an {@link AxonThreadFactory}.
     *
     * @return A default {@link InMemoryDeadLetterQueue}.
     */
    public static <T extends Message<?>> InMemoryDeadLetterQueue<T> defaultQueue() {
        //noinspection unchecked
        return (InMemoryDeadLetterQueue<T>) builder().build();
    }

    @Override
    public DeadLetterEntry<T> enqueue(@Nonnull QueueIdentifier identifier,
                                      @Nonnull T deadLetter,
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

        DeadLetterEntry<T> entry = buildEntry(identifier, deadLetter, cause);

        deadLetters.computeIfAbsent(identifier, id -> new ConcurrentLinkedDeque<>())
                   .addLast(entry);
        scheduleAvailabilityCallbacks(identifier);

        return entry;
    }

    private DeadLetterEntry<T> buildEntry(QueueIdentifier identifier, T deadLetter, Throwable cause) {
        Instant deadLettered = clock.instant();
        Instant expiresAt = cause == null ? deadLettered : deadLettered.plus(expireThreshold);
        return new GenericDeadLetterEntry(identifier,
                                          deadLetter,
                                          cause,
                                          deadLettered,
                                          expiresAt,
                                          this::acknowledge,
                                          this::requeue);
    }

    private void acknowledge(DeadLetterEntry<T> letter) {
        QueueIdentifier queueId = letter.queueIdentifier();
        Deque<DeadLetterEntry<T>> sequence = deadLetters.get(queueId);
        sequence.remove(letter);
        logger.trace("Removed letter [{}].", letter.message().getIdentifier());
        if (sequence.isEmpty()) {
            logger.trace("Queue [{}] is empty and will be removed.", queueId);
            deadLetters.remove(queueId);
        }
        takenSequences.remove(queueId);
    }

    private void requeue(DeadLetterEntry<T> letter) {
        QueueIdentifier queueId = letter.queueIdentifier();
        Deque<DeadLetterEntry<T>> sequence = deadLetters.get(queueId);
        sequence.remove(letter);

        Instant newExpiresAt = clock.instant().plus(expireThreshold);
        DeadLetterEntry<T> updatedLetter =
                new GenericDeadLetterEntry(letter, newExpiresAt, this::acknowledge, this::requeue);
        sequence.addFirst(updatedLetter);

        takenSequences.remove(queueId);
    }

    private void scheduleAvailabilityCallbacks(QueueIdentifier identifier) {
        availabilityCallbacks.entrySet()
                             .stream()
                             .filter(callbackEntry -> callbackEntry.getKey().equals(identifier.group()))
                             .map(Map.Entry::getValue)
                             .forEach(callback -> scheduledExecutorService.schedule(
                                     callback, expireThreshold.toMillis(), TimeUnit.MILLISECONDS
                             ));
    }

    @Override
    public boolean contains(@Nonnull QueueIdentifier identifier) {
        logger.debug("Validating existence of sequence identifier [{}].", identifier.combinedIdentifier());
        return deadLetters.containsKey(identifier);
    }

    @Override
    public boolean isEmpty() {
        return deadLetters.isEmpty();
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
    public synchronized Optional<DeadLetterEntry<T>> take(@Nonnull String group) {
        if (deadLetters.isEmpty()) {
            logger.debug("Queue is empty while taking. Returning an empty optional.");
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

        DeadLetterEntry<T> letter = getEarliestLetter(availableSequences);
        if (letter == null) {
            return Optional.empty();
        }
        takenSequences.add(letter.queueIdentifier());

        return Optional.of(letter);
    }

    private DeadLetterEntry<T> getEarliestLetter(List<QueueIdentifier> queueIds) {
        Set<Map.Entry<QueueIdentifier, Deque<DeadLetterEntry<T>>>> availableSequences =
                deadLetters.entrySet()
                           .stream()
                           .filter(entry -> queueIds.contains(entry.getKey()))
                           .collect(Collectors.toSet());

        Instant current = clock.instant();
        Instant earliestExpiredSequence = Instant.MAX;
        DeadLetterEntry<T> earliestExpiredLetter = null;
        for (Map.Entry<QueueIdentifier, Deque<DeadLetterEntry<T>>> sequence : availableSequences) {
            DeadLetterEntry<T> letter = sequence.getValue().peekFirst();
            if (letter != null
                    && letter.expiresAt().isBefore(current)
                    && letter.expiresAt().isBefore(earliestExpiredSequence)) {
                earliestExpiredSequence = letter.expiresAt();
                earliestExpiredLetter = letter;
            }
        }
        return earliestExpiredLetter;
    }

    @Override
    public void release(@Nonnull Predicate<DeadLetterEntry<T>> letterFilter) {
        Instant expiresAt = clock.instant();
        Set<String> releasedGroups = new HashSet<>();
        logger.debug("Received a request to release matching dead-letters for evaluation.");

        deadLetters.values()
                   .stream()
                   .flatMap(Collection::stream)
                   .filter(letterFilter)
                   .map(entry -> (GenericDeadLetterEntry) entry)
                   .forEach(entry -> {
                       entry.setExpiresAt(expiresAt);
                       releasedGroups.add(entry.queueIdentifier().group());
                   });

        releasedGroups.stream()
                      .map(availabilityCallbacks::get)
                      .filter(Objects::nonNull)
                      .forEach(scheduledExecutorService::submit);
    }

    @Override
    public void onAvailable(@Nonnull String group, @Nonnull Runnable callback) {
        if (availabilityCallbacks.put(group, callback) != null) {
            logger.info("Replaced the availability callback for group [{}].", group);
        }
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
            logger.info("Cleared out all entries for queue [{}].", queueId);
        });
    }

    @Override
    public void registerLifecycleHandlers(LifecycleRegistry lifecycle) {
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS + 1, this::shutdown);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        // When the executor is customized by the user, it's their job to shut it down.
        return customExecutor
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.runAsync(scheduledExecutorService::shutdown);
    }

    /**
     * Builder class to instantiate an {@link InMemoryDeadLetterQueue}.
     * <p>
     * The maximum number of queues defaults to {@code 1024}, the maximum amount of dead letters inside a queue defaults
     * to {@code 1024}, the dead letter expire threshold defaults to a {@link Duration} of 5000 milliseconds, and the
     * {@link ScheduledExecutorService} defaults to a {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)},
     * using an {@link AxonThreadFactory}.
     *
     * @param <T> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
     */
    public static class Builder<T extends Message<?>> {

        private int maxQueues = 1024;
        private int maxQueueSize = 1024;
        private Duration expireThreshold = Duration.ofMillis(5000);
        private ScheduledExecutorService scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor(new AxonThreadFactory("InMemoryDeadLetterQueue"));
        private boolean customExecutor = false;

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
         * considered ready to be {@link #take(String) taken}.
         * <p>
         * The provided threshold is also used to schedule
         * {@link #onAvailable(String, Runnable) configured availability checks}. Defaults to a {@link Duration} of 5000
         * milliseconds.
         *
         * @param expireThreshold The threshold for enqueued {@link DeadLetterEntry letters} to be considered ready to
         *                        be {@link #take(String) taken}.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<T> expireThreshold(Duration expireThreshold) {
            assertThat(expireThreshold,
                       threshold -> threshold != null && !threshold.isZero() && !threshold.isNegative(),
                       "The expire threshold should be strictly positive");
            this.expireThreshold = expireThreshold;
            return this;
        }

        /**
         * Sets the {@link ScheduledExecutorService} this queue uses to invoke
         * {@link #onAvailable(String, Runnable) configured availability callbacks}. Defaults to a
         * {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)}, using an {@link AxonThreadFactory}.
         *
         * @param scheduledExecutorService The {@link ScheduledExecutorService} this queue uses to invoke
         *                                 {@link #onAvailable(String, Runnable) configured availability callbacks}.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<T> scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            assertNonNull(scheduledExecutorService, "The ScheduledExecutorService should be non null");
            this.scheduledExecutorService = scheduledExecutorService;
            this.customExecutor = true;
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
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            // No assertions required, kept for overriding
        }
    }

    /**
     * Generic implementation of the {@link DeadLetterEntry} allowing any type of {@link Message} to be dead lettered.
     */
    private class GenericDeadLetterEntry implements DeadLetterEntry<T> {

        private final QueueIdentifier queueIdentifier;
        private final T message;
        private final Throwable cause;
        private Instant expiresAt;
        private final int numberOfRetries;
        private final Instant deadLettered;
        private final Consumer<GenericDeadLetterEntry> acknowledgeOperation;
        private final Consumer<GenericDeadLetterEntry> requeueOperation;

        private GenericDeadLetterEntry(DeadLetterEntry<T> entry,
                                       Instant expiresAt,
                                       Consumer<GenericDeadLetterEntry> acknowledgeOperation,
                                       Consumer<GenericDeadLetterEntry> requeueOperation) {
            this(entry.queueIdentifier(),
                 entry.message(),
                 entry.cause(),
                 entry.deadLettered(),
                 expiresAt,
                 entry.numberOfRetries() + 1,
                 acknowledgeOperation,
                 requeueOperation);
        }

        private GenericDeadLetterEntry(QueueIdentifier queueIdentifier,
                                       T message,
                                       Throwable cause,
                                       Instant deadLettered,
                                       Instant expiresAt,
                                       Consumer<GenericDeadLetterEntry> acknowledgeOperation,
                                       Consumer<GenericDeadLetterEntry> requeueOperation) {
            this(queueIdentifier, message, cause, deadLettered, expiresAt, 0, acknowledgeOperation, requeueOperation);
        }

        private GenericDeadLetterEntry(QueueIdentifier queueIdentifier,
                                       T message,
                                       Throwable cause,
                                       Instant deadLettered,
                                       Instant expiresAt,
                                       int numberOfRetries,
                                       Consumer<GenericDeadLetterEntry> acknowledgeOperation,
                                       Consumer<GenericDeadLetterEntry> requeueOperation) {
            this.queueIdentifier = queueIdentifier;
            this.message = message;
            this.cause = cause;
            this.deadLettered = deadLettered;
            this.expiresAt = expiresAt;
            this.numberOfRetries = numberOfRetries;
            this.acknowledgeOperation = acknowledgeOperation;
            this.requeueOperation = requeueOperation;
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

        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        @Override
        public int numberOfRetries() {
            return numberOfRetries;
        }

        @Override
        public void acknowledge() {
            acknowledgeOperation.accept(this);
        }

        @Override
        public void requeue() {
            requeueOperation.accept(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            // Check does not include the expiresAt, numberOfRetries, and releaseOperation allowing easy entry removal.
            //noinspection unchecked
            GenericDeadLetterEntry that = (GenericDeadLetterEntry) o;
            return Objects.equals(queueIdentifier, that.queueIdentifier)
                    && Objects.equals(message, that.message)
                    && Objects.equals(cause, that.cause)
                    && Objects.equals(deadLettered, that.deadLettered);
        }

        @Override
        public int hashCode() {
            return Objects.hash(queueIdentifier, message, cause, expiresAt, deadLettered, acknowledgeOperation);
        }

        @Override
        public String toString() {
            return "GenericDeadLetterEntry{" +
                    "queueIdentifier=" + queueIdentifier +
                    ", message=" + message +
                    ", cause=" + cause +
                    ", expiresAt=" + expiresAt +
                    ", numberOfRetries=" + numberOfRetries +
                    ", deadLettered=" + deadLettered +
                    '}';
        }
    }
}
