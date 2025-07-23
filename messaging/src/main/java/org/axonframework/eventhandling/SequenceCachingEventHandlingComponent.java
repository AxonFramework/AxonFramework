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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SequenceCachingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private final SequenceIdentifiersCache cache;

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
        this.cache = new SequenceIdentifiersCache(cacheSize);
    }

    @Override
    public EventHandlerRegistry subscribe(@Nonnull Set<QualifiedName> names, @Nonnull EventHandler eventHandler) {
        return super.subscribe(names, eventHandler);
    }

    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        String eventIdentifier = event.getIdentifier();

        // Try to get cache from context
        Optional<Map<String, Object>> cacheFromContext = SequenceIdentifiersCache.fromContext(context);
        if (cacheFromContext.isPresent()) {
            Map<String, Object> contextCache = cacheFromContext.get();
            Object cachedSequenceId = contextCache.get(eventIdentifier);
            if (cachedSequenceId != null) {
                return cachedSequenceId;
            }
        }

        // Cache miss - get sequence identifier from delegate
        Object sequenceIdentifier = super.sequenceIdentifierFor(event, context);

        // Add to cache - either update existing context cache or use instance cache
        if (cacheFromContext.isPresent()) {
            cacheFromContext.get().put(eventIdentifier, sequenceIdentifier);
        } else {
            // No cache in context yet, add our instance cache to context
            cache.put(eventIdentifier, sequenceIdentifier);
        }

        return sequenceIdentifier;
    }

    /**
     * A size-limited cache for storing sequence identifiers per event handling component instance.
     * The cache uses a LRU (Least Recently Used) eviction policy when the maximum size is reached.
     */
    public static final class SequenceIdentifiersCache {

        /**
         * The {@link Context.ResourceKey} used whenever a {@link Context} would contain sequence identifiers.
         */
        private static final Context.ResourceKey<Map<String, Object>> RESOURCE_KEY =
            Context.ResourceKey.withLabel("sequenceIdentifiersCache");

        private final Map<String, Object> cache;
        private final int maxSize;

        /**
         * Constructs a new sequence identifiers cache with the specified maximum size.
         *
         * @param maxSize The maximum number of entries to store in the cache.
         */
        public SequenceIdentifiersCache(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("Cache size must be positive");
            }
            this.maxSize = maxSize;

            // Use LinkedHashMap with access-order for LRU eviction
            this.cache = new LinkedHashMap<>(16, 0.75f, true) {
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
         * Removes a sequence identifier from the cache.
         *
         * @param eventIdentifier The event identifier to remove.
         * @return An {@link Optional} containing the removed sequence identifier if it was present, empty otherwise.
         */
        @Nonnull
        public Optional<Object> remove(@Nonnull String eventIdentifier) {
            return Optional.ofNullable(cache.remove(eventIdentifier));
        }

        /**
         * Checks if the cache contains an entry for the given event identifier.
         *
         * @param eventIdentifier The event identifier to check.
         * @return {@code true} if the cache contains the identifier, {@code false} otherwise.
         */
        public boolean containsKey(@Nonnull String eventIdentifier) {
            return cache.containsKey(eventIdentifier);
        }

        /**
         * Adds the sequence identifiers from this cache to the given {@code context} using the {@link #RESOURCE_KEY}.
         *
         * @param context The {@link Context} to add the sequence identifiers to.
         * @return A new {@link Context} with the sequence identifiers added.
         */
        @Nonnull
        public Context addToContext(@Nonnull Context context) {
            return context.withResource(RESOURCE_KEY, cache);
        }

        /**
         * Returns an {@link Optional} of sequence identifiers {@link Map}, returning the resource keyed under the
         * {@link #RESOURCE_KEY} in the given {@code context}.
         *
         * @param context The {@link Context} to retrieve the sequence identifiers from.
         * @return An {@link Optional} of sequence identifiers {@link Map}, returning the resource keyed under the
         * {@link #RESOURCE_KEY} in the given {@code context}.
         */
        @Nonnull
        public static Optional<Map<String, Object>> fromContext(@Nonnull Context context) {
            return Optional.ofNullable(context.getResource(RESOURCE_KEY));
        }
    }
}
