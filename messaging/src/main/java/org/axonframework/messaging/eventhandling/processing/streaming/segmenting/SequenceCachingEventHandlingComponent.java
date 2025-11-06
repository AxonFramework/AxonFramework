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

package org.axonframework.messaging.eventhandling.processing.streaming.segmenting;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decorator for {@link EventHandlingComponent}. This implementation acts as a performance optimization layer by caching
 * the results of sequence identifier calculations. Since sequence identifier computation can be expensive, this caching
 * component reduces redundant calculations for events which sequence identifier has already been calculated within the
 * same {@link ProcessingContext}.
 * <p>
 * The cache is stored as a resource in the {@link ProcessingContext}, ensuring that the cache is scoped to the current
 * processing unit of work. This means the cache is automatically cleaned up when the processing context is completed.
 * <p>
 * Example usage within framework configuration:
 * <pre>{@code
 * EventHandlingComponent component = // ... create base component
 * EventHandlingComponent cachingComponent = new SequenceCachingEventHandlingComponent(component);
 * }</pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class SequenceCachingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private final Context.ResourceKey<SequenceIdentifiersCache> resourceKey;

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate The {@link EventHandlingComponent} instance to delegate calls to.
     */
    @Internal
    public SequenceCachingEventHandlingComponent(@Nonnull EventHandlingComponent delegate) {
        super(delegate);
        this.resourceKey = Context.ResourceKey.withLabel("sequenceIdentifiersCache");
    }

    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        String eventIdentifier = event.identifier();

        SequenceIdentifiersCache cache = context.computeResourceIfAbsent(
                resourceKey,
                SequenceIdentifiersCache::new
        );

        var cachedSequenceId = cache.get(eventIdentifier);
        if (cachedSequenceId.isPresent()) {
            return cachedSequenceId.get();
        }

        Object sequenceIdentifier = super.sequenceIdentifierFor(event, context);
        cache.put(eventIdentifier, sequenceIdentifier);
        return sequenceIdentifier;
    }

    /**
     * A cache for storing sequence identifiers by event identifier within a {@link ProcessingContext}.
     * <p>
     * This cache provides a simple key-value mapping between event identifiers and their corresponding sequence
     * identifiers. The cache is scoped to a single processing context and is automatically cleaned up when the context
     * completes, ensuring memory efficiency and isolation between processing operations.
     */
    private static final class SequenceIdentifiersCache {

        private final Map<String, Object> cache;

        /**
         * Constructs a new sequence identifiers cache.
         * <p>
         * The cache is initialized as an empty {@link HashMap} and is intended to be used within the scope of a single
         * {@link ProcessingContext}.
         */
        SequenceIdentifiersCache() {
            this.cache = new HashMap<>();
        }

        /**
         * Stores a sequence identifier in the cache using the event identifier as the key.
         *
         * @param eventIdentifier    The event identifier to use as the cache key.
         * @param sequenceIdentifier The sequence identifier to store in the cache.
         */
        void put(@Nonnull String eventIdentifier, @Nonnull Object sequenceIdentifier) {
            cache.put(eventIdentifier, sequenceIdentifier);
        }

        /**
         * Retrieves a sequence identifier from the cache for the given event identifier.
         *
         * @param eventIdentifier The event identifier to look up in the cache.
         * @return An {@link Optional} containing the sequence identifier if present, or empty if not found.
         */
        @Nonnull
        Optional<Object> get(@Nonnull String eventIdentifier) {
            return Optional.ofNullable(cache.get(eventIdentifier));
        }
    }
}
