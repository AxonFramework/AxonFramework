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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A decorator for {@link SequencedDeadLetterQueue} that adds per-segment caching of sequence identifiers to optimize
 * {@link #contains(Object, ProcessingContext)} lookups. This is particularly important for high-throughput event
 * processing where checking if an event's sequence is already dead-lettered should be as fast as possible.
 * <p>
 * Each {@link Segment} gets its own independent {@link SequenceIdentifierCache}, obtained from the
 * {@link ProcessingContext}. When a segment is released, only that segment's cache is removed via
 * {@link #invalidateCache(ProcessingContext)}, leaving other segments' caches intact.
 * <p>
 * If no segment is available in the {@link ProcessingContext} (or the context is {@code null}), operations delegate
 * directly to the underlying queue without caching.
 * <p>
 * <b>Thread-safety note:</b> This class is not thread-safe when performing operations for the same sequence identifier
 * concurrently. It is designed for internal use by {@link DeadLetteringEventHandlingComponent}, where operations on a
 * given sequence identifier are already serialized by the upstream
 * {@link org.axonframework.messaging.eventhandling.processing.streaming.segmenting.SequencingEventHandlingComponent}.
 * Different segments can operate concurrently without interference. External synchronization must be provided if this
 * class is used in other contexts where concurrent access to the same sequence identifier is possible.
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
    private final ConcurrentHashMap<Segment, SequenceIdentifierCache> segmentCaches = new ConcurrentHashMap<>();

    /**
     * Constructs a caching decorator with the given delegate and default cache settings.
     * <p>
     * Each segment's cache is lazily initialized on first use by checking if the delegate queue is empty. If empty, the
     * cache operates in an optimized mode where unknown identifiers are assumed to not be present.
     *
     * @param delegate the underlying {@link SequencedDeadLetterQueue} to delegate to
     */
    public CachingSequencedDeadLetterQueue(SequencedDeadLetterQueue<M> delegate) {
        this(delegate, SequenceIdentifierCache.DEFAULT_MAX_SIZE);
    }

    /**
     * Constructs a caching decorator with the given delegate and cache max size.
     * <p>
     * Each segment's cache is lazily initialized on first use by checking if the delegate queue is empty. If empty, the
     * cache operates in an optimized mode where unknown identifiers are assumed to not be present.
     *
     * @param delegate     the underlying {@link SequencedDeadLetterQueue} to delegate to
     * @param cacheMaxSize the maximum size of the non-enqueued identifiers cache
     */
    public CachingSequencedDeadLetterQueue(SequencedDeadLetterQueue<M> delegate, int cacheMaxSize) {
        this.delegate = delegate;
        this.cacheMaxSize = cacheMaxSize;
    }

    /**
     * Extracts the {@link Segment} from the given {@link ProcessingContext}, if available.
     *
     * @param context the processing context, may be {@code null}
     * @return an {@link Optional} containing the segment if present in the context, or empty otherwise
     */
    private static Optional<Segment> extractSegment(@Nullable ProcessingContext context) {
        return context != null ? Segment.fromContext(context) : Optional.empty();
    }

    /**
     * Gets or lazily initializes the cache for the given segment.
     * <p>
     * The initialization checks if the delegate queue is empty. This check is performed asynchronously and the returned
     * future completes when the cache is ready.
     *
     * @param segment the segment to get or initialize the cache for
     * @param context the processing context in which the dead letters are processed
     * @return a future that completes with the initialized cache for the given segment
     */
    private CompletableFuture<SequenceIdentifierCache> getOrInitializeCache(
            @Nonnull Segment segment, @Nullable ProcessingContext context) {
        SequenceIdentifierCache existing = segmentCaches.get(segment);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        return delegate.amountOfSequences(context)
                       // computeIfAbsent handles race conditions — only the first initializer wins
                       .thenApply(count -> segmentCaches.computeIfAbsent(segment, k -> {
                           boolean startedEmpty = count == 0L;
                           if (logger.isDebugEnabled()) {
                               logger.debug("Lazily initialized caching dead letter queue for segment [{}]. "
                                                    + "Delegate started empty: [{}], cache max size: [{}].",
                                            segment, startedEmpty, cacheMaxSize);
                           }
                           return new SequenceIdentifierCache(startedEmpty, cacheMaxSize);
                       }));
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> enqueue(@Nonnull Object sequenceIdentifier,
                                           @Nonnull DeadLetter<? extends M> letter,
                                           @Nullable ProcessingContext context) {
        Optional<Segment> segmentOpt = extractSegment(context);
        if (segmentOpt.isEmpty()) {
            return delegate.enqueue(sequenceIdentifier, letter, context);
        }
        Segment segment = segmentOpt.get();
        return getOrInitializeCache(segment, context)
                .thenCompose(cache -> delegate.enqueue(sequenceIdentifier, letter, context)
                                              .whenComplete((result, error) -> {
                                                  if (error == null) {
                                                      cache.markEnqueued(sequenceIdentifier);
                                                  }
                                              }));
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> enqueueIfPresent(@Nonnull Object sequenceIdentifier,
                                                       @Nonnull Supplier<DeadLetter<? extends M>> letterBuilder,
                                                       @Nullable ProcessingContext context) {
        Optional<Segment> segmentOpt = extractSegment(context);
        if (segmentOpt.isEmpty()) {
            return delegate.enqueueIfPresent(sequenceIdentifier, letterBuilder, context);
        }
        Segment segment = segmentOpt.get();
        SequenceIdentifierCache existingCache = segmentCaches.get(segment);
        if (existingCache != null && !existingCache.mightBePresent(sequenceIdentifier)) {
            return CompletableFuture.completedFuture(false);
        }
        return getOrInitializeCache(segment, context)
                .thenCompose(cache -> delegate.enqueueIfPresent(sequenceIdentifier, letterBuilder, context)
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
    public CompletableFuture<Void> evict(@Nonnull DeadLetter<? extends M> letter,
                                         @Nullable ProcessingContext context) {
        // We don't have the sequence identifier here, so we cannot update the cache.
        // The cache will self-correct on the next contains() call.
        return delegate.evict(letter, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> requeue(@Nonnull DeadLetter<? extends M> letter,
                                           @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater,
                                           @Nullable ProcessingContext context) {
        // Requeue doesn't change the presence status, so no cache update needed.
        return delegate.requeue(letter, letterUpdater, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> contains(@Nonnull Object sequenceIdentifier,
                                               @Nullable ProcessingContext context) {
        Optional<Segment> segmentOpt = extractSegment(context);
        if (segmentOpt.isEmpty()) {
            return delegate.contains(sequenceIdentifier, context);
        }
        Segment segment = segmentOpt.get();
        // Check cache first - if initialized and we know it's not present, return false immediately
        SequenceIdentifierCache existingCache = segmentCaches.get(segment);
        if (existingCache != null && !existingCache.mightBePresent(sequenceIdentifier)) {
            if (logger.isTraceEnabled()) {
                logger.trace("Cache for segment [{}] indicates sequenceIdentifier [{}] is not present.",
                             segment, sequenceIdentifier);
            }
            return CompletableFuture.completedFuture(false);
        }

        // Cache not initialized or says it might be present - initialize cache and verify with delegate
        return getOrInitializeCache(segment, context)
                .thenCompose(cache -> delegate.contains(sequenceIdentifier, context)
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
    public CompletableFuture<Iterable<DeadLetter<? extends M>>> deadLetterSequence(
            @Nonnull Object sequenceIdentifier,
            @Nullable ProcessingContext context) {
        return delegate.deadLetterSequence(sequenceIdentifier, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Iterable<Iterable<DeadLetter<? extends M>>>> deadLetters(
            @Nullable ProcessingContext context) {
        return delegate.deadLetters(context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> isFull(@Nonnull Object sequenceIdentifier,
                                             @Nullable ProcessingContext context) {
        return delegate.isFull(sequenceIdentifier, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> size(@Nullable ProcessingContext context) {
        return delegate.size(context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> sequenceSize(@Nonnull Object sequenceIdentifier,
                                                @Nullable ProcessingContext context) {
        return delegate.sequenceSize(sequenceIdentifier, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> amountOfSequences(@Nullable ProcessingContext context) {
        return delegate.amountOfSequences(context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> process(
            @Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
            @Nonnull Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> processingTask,
            @Nullable ProcessingContext context) {
        // Processing may evict letters, but we don't know which sequences.
        // The cache will self-correct on subsequent contains() calls.
        return delegate.process(sequenceFilter, processingTask, context);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Clears all per-segment caches and the delegate queue.
     */
    @Nonnull
    @Override
    public CompletableFuture<Void> clear(@Nullable ProcessingContext context) {
        // Clear all segment caches to avoid stale cache hits during delegate clear.
        // Any concurrent contains() calls will go to the delegate directly.
        segmentCaches.clear();
        return delegate.clear(context);
    }

    /**
     * Invalidates the sequence identifier cache for the segment found in the given {@link ProcessingContext}.
     * <p>
     * The segment is extracted from the context internally. If no segment is present in the context, this method is a
     * no-op.
     * <p>
     * Call this method when processing ownership changes (e.g., segment release) to ensure cache consistency. When
     * ownership changes, another processor instance may have modified the queue, making cached information potentially
     * stale.
     *
     * @param context the processing context containing the {@link Segment} to invalidate the cache for, may be
     *                {@code null}
     */
    public void invalidateCache(@Nullable ProcessingContext context) {
        // We remove (not clear) the cache so that startedEmpty is re-evaluated when the segment is re-claimed.
        extractSegment(context).ifPresent(segment -> {
            SequenceIdentifierCache removed = segmentCaches.remove(segment);
            if (removed != null && logger.isDebugEnabled()) {
                logger.debug("Segment [{}] released. Removed sequence identifier cache.", segment);
            }
        });
    }

    /**
     * Returns the total size of identifiers cached as enqueued, aggregated across all segments.
     *
     * @return the total count of enqueued identifiers across all segment caches
     */
    public int cacheEnqueuedSize() {
        return segmentCaches.values().stream().mapToInt(SequenceIdentifierCache::enqueuedSize).sum();
    }

    /**
     * Returns the total size of identifiers cached as not enqueued, aggregated across all segments.
     *
     * @return the total count of non-enqueued identifiers across all segment caches
     */
    public int cacheNonEnqueuedSize() {
        return segmentCaches.values().stream().mapToInt(SequenceIdentifierCache::nonEnqueuedSize).sum();
    }
}
