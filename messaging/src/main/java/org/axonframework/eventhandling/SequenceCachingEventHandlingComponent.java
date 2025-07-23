/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SequenceCachingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private final int cacheSize;
    private final Context.ResourceKey<SequenceIdentifiersCache> resourceKey;

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate The instance to delegate calls to.
     */
    public SequenceCachingEventHandlingComponent(@Nonnull EventHandlingComponent delegate) {
        this(delegate, 1000); // Default cache size
    }

    /**
     * Constructs the component with given {@code delegate} to receive calls and specified cache size.
     *
     * @param delegate  The instance to delegate calls to.
     * @param cacheSize The maximum number of entries to store in the cache.
     */
    public SequenceCachingEventHandlingComponent(@Nonnull EventHandlingComponent delegate, int cacheSize) {
        super(delegate);
        this.cacheSize = cacheSize;
        this.resourceKey = Context.ResourceKey.withLabel("sequenceIdentifiersCache");
    }

    @Override
    public EventHandlerRegistry subscribe(@Nonnull Set<QualifiedName> names, @Nonnull EventHandler eventHandler) {
        return super.subscribe(names, eventHandler);
    }

    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        String eventIdentifier = event.getIdentifier();

        SequenceIdentifiersCache cache = context.computeResourceIfAbsent(
                resourceKey,
                () -> new SequenceIdentifiersCache(cacheSize)
        );

        var cachedSequenceId = cache.get(eventIdentifier);
        if (cachedSequenceId.isPresent()) {
            return cachedSequenceId;
        }

        Object sequenceIdentifier = super.sequenceIdentifierFor(event, context);
        cache.put(eventIdentifier, sequenceIdentifier);
        return sequenceIdentifier;
    }

    /**
     * A size-limited cache for storing sequence identifiers per event handling component instance.
     * The cache uses a LRU (Least Recently Used) eviction policy when the maximum size is reached.
     */
    public static final class SequenceIdentifiersCache {

        private final Map<String, Object> cache;

        /**
         * Constructs a new sequence identifiers cache with the specified maximum size.
         *
         * @param maxSize The maximum number of entries to store in the cache.
         */
        public SequenceIdentifiersCache(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("Cache size must be positive");
            }

            // Use LinkedHashMap with access-order for LRU eviction
            this.cache = new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                    return size() > maxSize;
                }
            };
        }

        /**
         * Stores a sequence identifier in the cache.
         *
         * @param eventIdentifier    The event identifier to use as key.
         * @param sequenceIdentifier The sequence identifier to store.
         */
        public void put(@Nonnull String eventIdentifier, @Nonnull Object sequenceIdentifier) {
            cache.put(eventIdentifier, sequenceIdentifier);
        }

        /**
         * Retrieves a sequence identifier from the cache.
         *
         * @param eventIdentifier The event identifier to look up.
         * @return An {@link Optional} containing the sequence identifier if present, empty otherwise.
         */
        @Nonnull
        public Optional<Object> get(@Nonnull String eventIdentifier) {
            return Optional.ofNullable(cache.get(eventIdentifier));
        }

        /**
         * Returns the current number of entries in the cache.
         *
         * @return The current cache size.
         */
        public int size() {
            return cache.size();
        }
    }
}
