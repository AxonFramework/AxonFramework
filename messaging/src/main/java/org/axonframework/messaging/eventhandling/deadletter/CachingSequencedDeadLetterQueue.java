/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter;

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import jakarta.annotation.Nonnull;

/**
 * A decorator for {@link SequencedDeadLetterQueue} that adds caching of sequence identifiers to optimize
 * {@link #contains(Object)} lookups. This is particularly important for high-throughput event processing
 * where checking if an event's sequence is already dead-lettered should be as fast as possible.
 * <p>
 * The caching mechanism uses a {@link SequenceIdentifierCache} to track which sequence identifiers are
 * known to be enqueued or not enqueued. When {@link #contains(Object)} is called, the cache is checked
 * first to potentially avoid a roundtrip to the underlying queue.
 * <p>
 * The cache should be cleared when a segment is released to ensure consistency. Use {@link #onSegmentReleased()}
 * to clear the cache when segment ownership changes.
 * <p>
 * Example usage:
 * <pre>{@code
 * SequencedDeadLetterQueue<EventMessage<?>> delegate = InMemorySequencedDeadLetterQueue.defaultQueue();
 * CachingSequencedDeadLetterQueue<EventMessage<?>> cachingQueue = new CachingSequencedDeadLetterQueue<>(delegate);
 *
 * // Later, when segment is released:
 * cachingQueue.onSegmentReleased();
 * }</pre>
 *
 * @param <M> The type of {@link Message} contained in the {@link DeadLetter dead letters} within this queue.
 * @author Mateusz Nowak
 * @see SequenceIdentifierCache
 * @see SequencedDeadLetterQueue
 * @since 5.0.0
 */
public class CachingSequencedDeadLetterQueue<M extends Message> implements SequencedDeadLetterQueue<M> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final SequencedDeadLetterQueue<M> delegate;
    private final SequenceIdentifierCache cache;

    /**
     * Constructs a caching decorator with the given delegate and default cache settings.
     * <p>
     * The cache is initialized by checking if the delegate queue is empty. If empty, the cache
     * operates in an optimized mode where unknown identifiers are assumed to not be present.
     *
     * @param delegate The underlying {@link SequencedDeadLetterQueue} to delegate to.
     */
    public CachingSequencedDeadLetterQueue(SequencedDeadLetterQueue<M> delegate) {
        this(delegate, SequenceIdentifierCache.DEFAULT_MAX_SIZE);
    }

    /**
     * Constructs a caching decorator with the given delegate and cache max size.
     * <p>
     * The cache is initialized by checking if the delegate queue is empty. If empty, the cache
     * operates in an optimized mode where unknown identifiers are assumed to not be present.
     *
     * @param delegate     The underlying {@link SequencedDeadLetterQueue} to delegate to.
     * @param cacheMaxSize The maximum size of the non-enqueued identifiers cache.
     */
    public CachingSequencedDeadLetterQueue(SequencedDeadLetterQueue<M> delegate, int cacheMaxSize) {
        this.delegate = delegate;
        boolean startedEmpty = delegate.amountOfSequences().join() == 0L;
        this.cache = new SequenceIdentifierCache(startedEmpty, cacheMaxSize);
        if (logger.isDebugEnabled()) {
            logger.debug("Initialized caching dead letter queue. Delegate started empty: [{}], cache max size: [{}].",
                         startedEmpty, cacheMaxSize);
        }
    }

    /**
     * Constructs a caching decorator with the given delegate and pre-configured cache.
     * <p>
     * This constructor allows for fine-grained control over cache initialization.
     *
     * @param delegate The underlying {@link SequencedDeadLetterQueue} to delegate to.
     * @param cache    The pre-configured {@link SequenceIdentifierCache} to use.
     */
    public CachingSequencedDeadLetterQueue(SequencedDeadLetterQueue<M> delegate, SequenceIdentifierCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> enqueue(@Nonnull Object sequenceIdentifier,
                                           @Nonnull DeadLetter<? extends M> letter) {
        return delegate.enqueue(sequenceIdentifier, letter)
                       .whenComplete((result, error) -> {
                           if (error == null) {
                               cache.markEnqueued(sequenceIdentifier);
                           }
                       });
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> enqueueIfPresent(@Nonnull Object sequenceIdentifier,
                                                       @Nonnull Supplier<DeadLetter<? extends M>> letterBuilder) {
        // Check cache first - if we know it's not present, skip the delegate call
        if (!cache.mightBePresent(sequenceIdentifier)) {
            return CompletableFuture.completedFuture(false);
        }
        return delegate.enqueueIfPresent(sequenceIdentifier, letterBuilder)
                       .whenComplete((result, error) -> {
                           if (error == null) {
                               if (Boolean.TRUE.equals(result)) {
                                   cache.markEnqueued(sequenceIdentifier);
                               } else {
                                   cache.markNotEnqueued(sequenceIdentifier);
                               }
                           }
                       });
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> evict(@Nonnull DeadLetter<? extends M> letter) {
        // We don't have the sequence identifier here, so we cannot update the cache.
        // The cache will self-correct on the next contains() call.
        return delegate.evict(letter);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> requeue(@Nonnull DeadLetter<? extends M> letter,
                                           @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater) {
        // Requeue doesn't change the presence status, so no cache update needed.
        return delegate.requeue(letter, letterUpdater);
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> contains(@Nonnull Object sequenceIdentifier) {
        // Check cache first - if we know it's not present, return false immediately
        if (!cache.mightBePresent(sequenceIdentifier)) {
            if (logger.isTraceEnabled()) {
                logger.trace("Cache indicates sequenceIdentifier [{}] is not present.", sequenceIdentifier);
            }
            return CompletableFuture.completedFuture(false);
        }

        // Cache says it might be present, verify with delegate
        return delegate.contains(sequenceIdentifier)
                       .whenComplete((result, error) -> {
                           if (error == null) {
                               if (Boolean.TRUE.equals(result)) {
                                   cache.markEnqueued(sequenceIdentifier);
                               } else {
                                   cache.markNotEnqueued(sequenceIdentifier);
                               }
                           }
                       });
    }

    @Nonnull
    @Override
    public CompletableFuture<Iterable<DeadLetter<? extends M>>> deadLetterSequence(@Nonnull Object sequenceIdentifier) {
        return delegate.deadLetterSequence(sequenceIdentifier);
    }

    @Nonnull
    @Override
    public CompletableFuture<Iterable<Iterable<DeadLetter<? extends M>>>> deadLetters() {
        return delegate.deadLetters();
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> isFull(@Nonnull Object sequenceIdentifier) {
        return delegate.isFull(sequenceIdentifier);
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> size() {
        return delegate.size();
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> sequenceSize(@Nonnull Object sequenceIdentifier) {
        return delegate.sequenceSize(sequenceIdentifier);
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> amountOfSequences() {
        return delegate.amountOfSequences();
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> process(
            @Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
            @Nonnull Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> processingTask) {
        // Processing may evict letters, but we don't know which sequences.
        // The cache will self-correct on subsequent contains() calls.
        return delegate.process(sequenceFilter, processingTask);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> clear() {
        return delegate.clear()
                       .whenComplete((result, error) -> {
                           if (error == null) {
                               cache.clear();
                           }
                       });
    }

    /**
     * Clears the sequence identifier cache.
     * <p>
     * This should be called when a segment is released to ensure cache consistency. When a segment
     * is released, another processor instance may modify the queue, making the cached information
     * potentially stale.
     */
    public void onSegmentReleased() {
        if (logger.isDebugEnabled()) {
            logger.debug("Segment released. Clearing sequence identifier cache.");
        }
        cache.clear();
    }

    /**
     * Returns the underlying delegate queue.
     *
     * @return The delegate {@link SequencedDeadLetterQueue}.
     */
    public SequencedDeadLetterQueue<M> getDelegate() {
        return delegate;
    }

    /**
     * Returns the current size of identifiers cached as enqueued.
     *
     * @return The count of enqueued identifiers in the cache.
     */
    public int cacheEnqueuedSize() {
        return cache.enqueuedSize();
    }

    /**
     * Returns the current size of identifiers cached as not enqueued.
     *
     * @return The count of non-enqueued identifiers in the cache.
     */
    public int cacheNonEnqueuedSize() {
        return cache.nonEnqueuedSize();
    }
}
