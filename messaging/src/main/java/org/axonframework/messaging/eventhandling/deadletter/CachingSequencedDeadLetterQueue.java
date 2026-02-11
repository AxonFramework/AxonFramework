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

import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import jakarta.annotation.Nonnull;

/**
 * A decorator for {@link SequencedDeadLetterQueue} that adds caching of sequence identifiers to optimize
 * {@link #contains(Object)} lookups. This is particularly important for high-throughput event processing where checking
 * if an event's sequence is already dead-lettered should be as fast as possible.
 * <p>
 * The caching mechanism uses a {@link SequenceIdentifierCache} to track which sequence identifiers are known to be
 * enqueued or not enqueued. When {@link #contains(Object)} is called, the cache is checked first to potentially avoid a
 * roundtrip to the underlying queue.
 * <p>
 * The cache should be cleared when a segment is released to ensure consistency. Use {@link #invalidateCache()} to clear
 * the cache when segment ownership changes.
 * <p>
 * <b>Thread-safety note:</b> This class is not thread-safe when performing operations for the same sequence identifier
 * concurrently. It is designed for internal use by {@link DeadLetteringEventHandlingComponent}, where operations on a
 * given sequence identifier are already serialized by the upstream
 * {@link org.axonframework.messaging.eventhandling.processing.streaming.segmenting.SequencingEventHandlingComponent}. External synchronization must be
 * provided if this class is used in other contexts where concurrent access to the same sequence identifier is possible.
 *
 * @param <M> The type of {@link Message} contained in the {@link DeadLetter dead letters} within this queue.
 * @author Mateusz Nowak
 * @see SequenceIdentifierCache
 * @see SequencedDeadLetterQueue
 * @since 5.1.0
 */
@Internal
public class CachingSequencedDeadLetterQueue<M extends Message> implements SequencedDeadLetterQueue<M> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final SequencedDeadLetterQueue<M> delegate;
    private final int cacheMaxSize;
    private final AtomicReference<SequenceIdentifierCache> cacheRef = new AtomicReference<>();

    /**
     * Constructs a caching decorator with the given delegate and default cache settings.
     * <p>
     * The cache is lazily initialized on first use by checking if the delegate queue is empty. If empty, the cache
     * operates in an optimized mode where unknown identifiers are assumed to not be present.
     *
     * @param delegate the underlying {@link SequencedDeadLetterQueue} to delegate to
     */
    public CachingSequencedDeadLetterQueue(SequencedDeadLetterQueue<M> delegate) {
        this(delegate, SequenceIdentifierCache.DEFAULT_MAX_SIZE);
    }

    /**
     * Constructs a caching decorator with the given delegate and cache max size.
     * <p>
     * The cache is lazily initialized on first use by checking if the delegate queue is empty. If empty, the cache
     * operates in an optimized mode where unknown identifiers are assumed to not be present.
     *
     * @param delegate     the underlying {@link SequencedDeadLetterQueue} to delegate to
     * @param cacheMaxSize the maximum size of the non-enqueued identifiers cache
     */
    public CachingSequencedDeadLetterQueue(SequencedDeadLetterQueue<M> delegate, int cacheMaxSize) {
        this.delegate = delegate;
        this.cacheMaxSize = cacheMaxSize;
    }

    /**
     * Gets the cache, initializing it lazily if necessary.
     * <p>
     * The initialization checks if the delegate queue is empty. This check is performed asynchronously and the returned
     * future completes when the cache is ready.
     *
     * @return a future that completes with the initialized cache
     */
    private CompletableFuture<SequenceIdentifierCache> getOrInitializeCache() {
        SequenceIdentifierCache existingCache = cacheRef.get();
        if (existingCache != null) {
            return CompletableFuture.completedFuture(existingCache);
        }

        return delegate.amountOfSequences()
                       .thenApply(count -> {
                           boolean startedEmpty = count == 0L;
                           SequenceIdentifierCache newCache = new SequenceIdentifierCache(startedEmpty, cacheMaxSize);
                           // Use compareAndSet to handle race conditions - only first initializer wins
                           if (cacheRef.compareAndSet(null, newCache)) {
                               if (logger.isDebugEnabled()) {
                                   logger.debug("Lazily initialized caching dead letter queue. "
                                                        + "Delegate started empty: [{}], cache max size: [{}].",
                                                startedEmpty, cacheMaxSize);
                               }
                               return newCache;
                           }
                           // Another thread won the race, use their cache
                           return cacheRef.get();
                       });
    }

    /**
     * Gets the cache if already initialized, or null if not yet initialized.
     *
     * @return the cache if initialized, null otherwise
     */
    private SequenceIdentifierCache getInitializedCacheOrNull() {
        return cacheRef.get();
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> enqueue(@Nonnull Object sequenceIdentifier,
                                           @Nonnull DeadLetter<? extends M> letter) {
        // Initialize cache before enqueue to ensure we track this sequence
        return getOrInitializeCache()
                .thenCompose(cache -> delegate.enqueue(sequenceIdentifier, letter)
                                              .whenComplete((result, error) -> {
                                                  if (error == null) {
                                                      cache.markEnqueued(sequenceIdentifier);
                                                  }
                                              }));
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> enqueueIfPresent(@Nonnull Object sequenceIdentifier,
                                                       @Nonnull Supplier<DeadLetter<? extends M>> letterBuilder) {
        SequenceIdentifierCache cache = getInitializedCacheOrNull();
        if (cache != null && !cache.mightBePresent(sequenceIdentifier)) {
            return CompletableFuture.completedFuture(false);
        }
        return delegate.enqueueIfPresent(sequenceIdentifier, letterBuilder)
                       .whenComplete((result, error) -> {
                           if (error == null) {
                               SequenceIdentifierCache c = getInitializedCacheOrNull();
                               if (c != null) {
                                   if (Boolean.TRUE.equals(result)) {
                                       c.markEnqueued(sequenceIdentifier);
                                   } else {
                                       c.markNotEnqueued(sequenceIdentifier);
                                   }
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
        // Check cache first - if initialized and we know it's not present, return false immediately
        SequenceIdentifierCache existingCache = getInitializedCacheOrNull();
        if (existingCache != null && !existingCache.mightBePresent(sequenceIdentifier)) {
            if (logger.isTraceEnabled()) {
                logger.trace("Cache indicates sequenceIdentifier [{}] is not present.", sequenceIdentifier);
            }
            return CompletableFuture.completedFuture(false);
        }

        // Cache not initialized or says it might be present - initialize cache and verify with delegate
        return getOrInitializeCache()
                .thenCompose(cache -> delegate.contains(sequenceIdentifier)
                                              .whenComplete((result, error) -> {
                                                  if (error == null) {
                                                      if (Boolean.TRUE.equals(result)) {
                                                          cache.markEnqueued(sequenceIdentifier);
                                                      } else {
                                                          cache.markNotEnqueued(sequenceIdentifier);
                                                      }
                                                  }
                                              }));
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

    /**
     * {@inheritDoc}
     * <p>
     * Clears both the local cache and the delegate queue. The cache is cleared first (synchronously) to ensure that any
     * concurrent {@link #contains(Object)} calls during the delegate clear operation will query the delegate directly,
     * avoiding stale cache hits. The cache will self-correct on subsequent operations if any inconsistency occurs.
     * <p>
     * This method is intended to be called during event processor reset when processing has been stopped. This cache
     * instance should not be shared across multiple event processors to avoid interference during clear operations.
     */
    @Nonnull
    @Override
    public CompletableFuture<Void> clear() {
        // Clear cache first to avoid stale cache hits during delegate clear.
        // Any concurrent contains() calls will go to the delegate directly.
        SequenceIdentifierCache cache = getInitializedCacheOrNull();
        if (cache != null) {
            cache.clear();
        }
        return delegate.clear();
    }

    /**
     * Invalidates the sequence identifier cache.
     * <p>
     * Call this method when processing ownership changes (e.g., segment release) to ensure cache consistency. When
     * ownership changes, another processor instance may have modified the queue, making cached information potentially
     * stale.
     */
    public void invalidateCache() {
        SequenceIdentifierCache cache = getInitializedCacheOrNull();
        if (cache != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Segment released. Clearing sequence identifier cache.");
            }
            cache.clear();
        }
    }

    /**
     * Returns the current size of identifiers cached as enqueued.
     *
     * @return the count of enqueued identifiers in the cache, or 0 if cache is not yet initialized
     */
    public int cacheEnqueuedSize() {
        SequenceIdentifierCache cache = getInitializedCacheOrNull();
        return cache != null ? cache.enqueuedSize() : 0;
    }

    /**
     * Returns the current size of identifiers cached as not enqueued.
     *
     * @return the count of non-enqueued identifiers in the cache, or 0 if cache is not yet initialized
     */
    public int cacheNonEnqueuedSize() {
        SequenceIdentifierCache cache = getInitializedCacheOrNull();
        return cache != null ? cache.nonEnqueuedSize() : 0;
    }
}
