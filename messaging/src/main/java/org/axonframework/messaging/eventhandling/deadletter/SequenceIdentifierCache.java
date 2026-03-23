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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cache for sequence identifiers used to optimize {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue}
 * lookups. This cache reduces the number of calls to the underlying queue by tracking which sequence identifiers
 * are known to be enqueued or not enqueued.
 * <p>
 * The cache maintains two collections:
 * <ul>
 *     <li><b>enqueuedIdentifiers</b> - Set of identifiers known to be in the dead letter queue.</li>
 *     <li><b>nonEnqueuedIdentifiers</b> - Bounded {@link LinkedHashMap} (LRU) of identifiers known NOT to be in the queue.
 *         Only populated when the queue started non-empty. Uses insertion-order iteration with automatic eviction
 *         of the oldest entry when the maximum size is exceeded.</li>
 * </ul>
 * <p>
 * When the queue starts empty, the cache can optimize by only tracking enqueued identifiers.
 * If a sequence identifier is not in the enqueuedIdentifiers set, we know it's not in the queue.
 * When the queue starts non-empty, we need to also track non-enqueued identifiers to avoid
 * repeated lookups for the same identifiers.
 * <p>
 * The cache should be cleared when a segment is released to ensure consistency, as the state
 * of the queue may have changed while the segment was held by another processor.
 * <p>
 * <b>Thread Safety Note:</b> This class provides relaxed consistency guarantees suitable for its role
 * as a cache optimization. Individual operations on the underlying collections are thread-safe, but compound
 * operations (such as {@link #markEnqueued(Object)} and {@link #markNotEnqueued(Object)}) are not atomic.
 * During concurrent modifications, a sequence identifier may temporarily appear in both collections or neither.
 * This is acceptable because {@link #mightBePresent(Object)} is designed to return {@code true} when uncertain
 * (favoring false positives over false negatives), which simply triggers a lookup to the underlying queue.
 * The {@link #clear()} method also has relaxed atomicity - concurrent modifications during clear may result
 * in partial state until the clear completes.
 * <p>
 * This relaxed consistency approach is intentional - avoiding synchronization on compound operations
 * provides better performance and reduces contention in high-throughput scenarios, which is preferable
 * for a cache whose purpose is to optimize performance in the first place.
 *
 * @author Gerard Klijs
 * @author Mateusz Nowak
 * @see CachingSequencedDeadLetterQueue
 * @since 4.9.0
 */
@Internal
class SequenceIdentifierCache {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final Boolean PRESENT = Boolean.TRUE;

    /**
     * Default maximum size for the non-enqueued identifiers cache.
     */
    public static final int DEFAULT_MAX_SIZE = 1024;

    private final boolean startedEmpty;
    private final Map<Object, Boolean> nonEnqueuedIdentifiers;
    private final Set<Object> enqueuedIdentifiers = ConcurrentHashMap.newKeySet();

    /**
     * Constructs a {@link SequenceIdentifierCache} that started with an empty queue.
     * <p>
     * When the queue starts empty, the cache only tracks enqueued identifiers.
     * Any identifier not in the enqueued set is assumed to not be in the queue.
     */
    public SequenceIdentifierCache() {
        this(true, DEFAULT_MAX_SIZE);
    }

    /**
     * Constructs a {@link SequenceIdentifierCache} with the specified initial state and default max size.
     *
     * @param startedEmpty whether the dead letter queue was empty when this cache was created.
     *                     If {@code true}, only enqueued identifiers are tracked.
     *                     If {@code false}, non-enqueued identifiers are also tracked.
     */
    public SequenceIdentifierCache(boolean startedEmpty) {
        this(startedEmpty, DEFAULT_MAX_SIZE);
    }

    /**
     * Constructs a {@link SequenceIdentifierCache} with the specified initial state and max size.
     *
     * @param startedEmpty whether the dead letter queue was empty when this cache was created.
     *                     If {@code true}, only enqueued identifiers are tracked.
     *                     If {@code false}, non-enqueued identifiers are also tracked.
     * @param maxSize      the maximum size of the non-enqueued identifiers cache.
     *                     When exceeded, the oldest entry is removed (LRU eviction)
     */
    public SequenceIdentifierCache(boolean startedEmpty, int maxSize) {
        this.startedEmpty = startedEmpty;
        this.nonEnqueuedIdentifiers = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Boolean> eldest) {
                return size() > maxSize;
            }
        });
    }

    /**
     * Checks if a sequence identifier might be present in the dead letter queue.
     * <p>
     * Returns {@code true} if the identifier is in the enqueued set, or if the queue started
     * non-empty and the identifier is not in the non-enqueued set. Returns {@code false} only
     * when we are certain the identifier is not in the queue.
     *
     * @param sequenceIdentifier the sequence identifier to check
     * @return {@code true} if the identifier might be present, {@code false} if it's definitely not present
     */
    public boolean mightBePresent(Object sequenceIdentifier) {
        if (enqueuedIdentifiers.contains(sequenceIdentifier)) {
            return true;
        }
        if (startedEmpty) {
            return false;
        }
        return !nonEnqueuedIdentifiers.containsKey(sequenceIdentifier);
    }

    /**
     * Marks a sequence identifier as enqueued in the dead letter queue.
     * <p>
     * This removes the identifier from the non-enqueued set (if present) and adds it to the enqueued set.
     *
     * @param sequenceIdentifier the sequence identifier to mark as enqueued
     * @return this cache instance for method chaining
     */
    public SequenceIdentifierCache markEnqueued(Object sequenceIdentifier) {
        if (logger.isTraceEnabled()) {
            logger.trace("Marked sequenceIdentifier [{}] as enqueued in the cache.", sequenceIdentifier);
        }
        enqueuedIdentifiers.add(sequenceIdentifier);
        nonEnqueuedIdentifiers.remove(sequenceIdentifier);
        return this;
    }

    /**
     * Marks a sequence identifier as not enqueued in the dead letter queue.
     * <p>
     * This removes the identifier from the enqueued set (if present). If the queue started non-empty,
     * the identifier is also added to the non-enqueued set with LRU eviction when full.
     *
     * @param sequenceIdentifier the sequence identifier to mark as not enqueued
     * @return this cache instance for method chaining
     */
    public SequenceIdentifierCache markNotEnqueued(Object sequenceIdentifier) {
        if (logger.isTraceEnabled()) {
            logger.trace("Marked sequenceIdentifier [{}] as not enqueued in the cache.", sequenceIdentifier);
        }
        if (!startedEmpty) {
            nonEnqueuedIdentifiers.put(sequenceIdentifier, PRESENT);
        }
        enqueuedIdentifiers.remove(sequenceIdentifier);
        return this;
    }

    /**
     * Clears all cached identifiers.
     * <p>
     * This should be called when a segment is released to ensure the cache is refreshed
     * when the segment is claimed again. The cache state may be stale after a segment
     * is released because another processor instance may have modified the queue.
     *
     * @return this cache instance for method chaining
     */
    public SequenceIdentifierCache clear() {
        if (logger.isDebugEnabled()) {
            logger.debug("Clearing sequence identifier cache. Enqueued: [{}], Non-enqueued: [{}].",
                         enqueuedIdentifiers.size(), nonEnqueuedIdentifiers.size());
        }
        enqueuedIdentifiers.clear();
        nonEnqueuedIdentifiers.clear();
        return this;
    }

    /**
     * Returns the number of identifiers marked as enqueued.
     *
     * @return the count of enqueued identifiers
     */
    public int enqueuedSize() {
        return enqueuedIdentifiers.size();
    }

    /**
     * Returns the number of identifiers marked as not enqueued.
     *
     * @return the count of non-enqueued identifiers
     */
    public int nonEnqueuedSize() {
        return nonEnqueuedIdentifiers.size();
    }
}
