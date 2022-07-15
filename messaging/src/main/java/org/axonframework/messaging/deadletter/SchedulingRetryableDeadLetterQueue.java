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
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of a {@link DeadLetterQueue} containing scheduling logic for registered
 * {@link #onAvailable(String, Runnable) availability callbacks}.
 *
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class SchedulingRetryableDeadLetterQueue<M extends Message<?>>
        implements RetryableDeadLetterQueue<RetryableDeadLetter<M>, M>, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected final Map<String, Runnable> availabilityCallbacks = new ConcurrentSkipListMap<>();

    private final DeadLetterQueue<RetryableDeadLetter<M>, M> delegate;
    private final RetryPolicy<M> retryPolicy;
    protected final ScheduledExecutorService scheduledExecutorService;
    private final boolean customExecutor;

    /**
     * Instantiate a scheduling {@link DeadLetterQueue} implementation based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link SchedulingRetryableDeadLetterQueue}
     *                implementation with.
     */
    protected SchedulingRetryableDeadLetterQueue(Builder<M> builder) {
        builder.validate();
        this.delegate = builder.delegate;
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.customExecutor = builder.customExecutor;
        this.retryPolicy = builder.retryPolicy;
    }

    @Override
    public void registerLifecycleHandlers(LifecycleRegistry lifecycle) {
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS + 1, this::shutdown);
    }

    @Override
    public RetryableDeadLetter<M> enqueue(@Nonnull QueueIdentifier identifier,
                                          @Nonnull M deadLetter,
                                          Throwable cause) throws DeadLetterQueueOverflowException {
        RetryableDeadLetter<M> letter = delegate.enqueue(identifier, deadLetter, cause);
        RetryDecision decision = retryPolicy.decide(letter, cause);
        GenericRetryableDeadLetter<M> retryableLetter =
                new GenericRetryableDeadLetter<>(letter, decision.retryAt(), 0, this::acknowledge, this::requeue);
        enqueue(retryableLetter);
        return letter;
    }

    @Override
    public void enqueue(@Nonnull RetryableDeadLetter<M> letter) throws DeadLetterQueueOverflowException {
        RetryDecision decision = retryPolicy.decide(letter, null);
        decision.retryAt();
        if (decision.shouldRetry()) {
            delegate.enqueue(new GenericRetryableDeadLetter<>(letter, decision.retryAt(), this::acknowledge, this::requeue));
            letter.release();
        } else {
            letter.evict();
        }
    }

    private void acknowledge(RetryableDeadLetter<M> letter) {
        letter.evict();
    }

    private void requeue(RetryableDeadLetter<M> letter, Throwable cause) {
        RetryDecision decision = retryPolicy.decide(letter, cause);
        if (decision.shouldRetry()) {
            // TODO: 15-07-22 how do we ensure this enters the letter back in front of it's own queue?
            delegate.enqueue(new GenericRetryableDeadLetter<>(letter, decision.retryAt(), this::acknowledge, this::requeue));
            letter.release();
        } else {
            letter.evict();
        }
    }

    /**
     * Schedule registered {@link #onAvailable(String, Runnable) availability callbacks} matching the given
     * {@code identifier}. Will use the configured {@code Builder#expireThreshold(Duration) expiry threshold} when
     * scheduling.
     *
     * @param letter The {@link QueueIdentifier} to schedule registered
     *               {@link #onAvailable(String, Runnable) availability callbacks} for.
     */
    protected void scheduleAvailabilityCallbacks(RetryableDeadLetter<M> letter) {
        RetryDecision decision = retryPolicy.decide(letter, null);
        if (decision.shouldRetry()) {
            long retryAt = decision.retryAt().toEpochMilli();
            long current = Instant.now().toEpochMilli();
            long retryIn = retryAt - current;
            availabilityCallbacks.entrySet()
                                 .stream()
                                 .filter(callbackEntry -> callbackEntry.getKey()
                                                                       .equals(letter.queueIdentifier().group()))
                                 .map(Map.Entry::getValue)
                                 .forEach(callback -> scheduledExecutorService.schedule(
                                         callback, retryIn, TimeUnit.MILLISECONDS
                                 ));
        }
    }

    @Override
    public boolean contains(@Nonnull QueueIdentifier identifier) {
        return delegate.contains(identifier);
    }

    @Override
    public Iterable<RetryableDeadLetter<M>> deadLetters(@Nonnull QueueIdentifier identifier) {
        return delegate.deadLetters(identifier);
    }

    @Override
    public Iterable<DeadLetterSequence<M>> deadLetterSequences() {
        return delegate.deadLetterSequences();
    }

    @Override
    public boolean isFull(@Nonnull QueueIdentifier queueIdentifier) {
        return delegate.isFull(queueIdentifier);
    }

    @Override
    public long maxQueues() {
        return delegate.maxQueues();
    }

    @Override
    public long maxQueueSize() {
        return delegate.maxQueueSize();
    }

    @Override
    public Optional<RetryableDeadLetter<M>> take(@Nonnull String group) {
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

    //    private RetryableDeadLetter<M> getEarliestLetter(List<QueueIdentifier> queueIds) {
//        Set<Map.Entry<QueueIdentifier, Deque<RetryableDeadLetter<M>>>> availableSequences =
//                deadLetters.entrySet()
//                           .stream()
//                           .filter(entry -> queueIds.contains(entry.getKey()))
//                           .collect(Collectors.toSet());
//
//        Instant current = clock.instant();
//        long earliestExpiredSequence = Long.MAX_VALUE;
//        RetryableDeadLetter<M> earliestExpiredLetter = null;
//        for (Map.Entry<QueueIdentifier, Deque<RetryableDeadLetter<M>>> sequence : availableSequences) {
//            RetryableDeadLetter<M> letter = sequence.getValue().peekFirst();
//            if (letter != null
//                    && letter.expiresAt().toEpochMilli() <= current.toEpochMilli()
//                    && letter.expiresAt().toEpochMilli() < earliestExpiredSequence) {
//                earliestExpiredSequence = letter.expiresAt().toEpochMilli();
//                earliestExpiredLetter = letter;
//            }
//        }
//        return earliestExpiredLetter;
//    }

    @Override
    public void clear(@Nonnull Predicate<QueueIdentifier> queueFilter) {
        delegate.clear(queueFilter);
    }

    @Override
    public void release(@Nonnull Predicate<QueueIdentifier> queueFilter) {
//        Instant now = clock.instant();
        Set<String> releasedGroups = new HashSet<>();
        logger.debug("Received a request to release matching dead-letters for evaluation.");

        // retrieve entries to adjust
//        deadLetters.values()
//                   .stream()
//                   .flatMap(Collection::stream)
//                   .filter(letter -> queueFilter.test(letter.queueIdentifier()))
//                   .map(letter -> (GenericRetryableDeadLetter<M>) letter)
//                   .forEach(letter -> {
//                       letter.setExpiresAt(now);
//                       releasedGroups.add(letter.queueIdentifier().group());
//                   });

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
    public CompletableFuture<Void> shutdown() {
        // When the executor is customized by the user, it's their job to shut it down.
        return customExecutor
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.runAsync(scheduledExecutorService::shutdown);
    }

    /**
     * Abstract builder class to instantiate a {@link SchedulingRetryableDeadLetterQueue} implementations.
     * <p>
     * The expiry threshold defaults to a {@link Duration} of 5000 milliseconds, and the
     * {@link ScheduledExecutorService} defaults to a {@link Executors#newSingleThreadScheduledExecutor(ThreadFactory)},
     * using an {@link AxonThreadFactory}.
     *
     * @param <M> The type of {@link Message} maintained in this {@link DeadLetterQueue}.
     */
    //@param <B> The type of builder implementing this abstract builder class.
//    <D extends RetryableDeadLetter<M>, M extends Message<?>>
    protected static class Builder<M extends Message<?>> {

        private DeadLetterQueue<RetryableDeadLetter<M>, M> delegate;
        private RetryPolicy<M> retryPolicy = new IntervalRetryPolicy<>();
        protected ScheduledExecutorService scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor(new AxonThreadFactory("AbstractDeadLetterQueue"));
        protected boolean customExecutor = false;

        /**
         * @param delegate
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> delegate(DeadLetterQueue<RetryableDeadLetter<M>, M> delegate) {
            assertNonNull(delegate, "The RetryPolicy should be non null");
            this.delegate = delegate;
            return this;
        }

        /**
         * @param retryPolicy
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> retryPolicy(RetryPolicy<M> retryPolicy) {
            assertNonNull(retryPolicy, "The RetryPolicy should be non null");
            this.retryPolicy = retryPolicy;
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
        public Builder<M> scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            assertNonNull(scheduledExecutorService, "The ScheduledExecutorService should be non null");
            this.scheduledExecutorService = scheduledExecutorService;
            this.customExecutor = true;
            return this;
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
}
