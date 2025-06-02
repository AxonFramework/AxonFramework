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

package org.axonframework.eventstreaming;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SimpleEntry;
import org.axonframework.messaging.StreamableMessageSource;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Adapter implementation that translates from the deprecated {@link StreamableMessageSource} interface to the new
 * {@link StreamableEventSource} interface.
 * <p>
 * This adapter allows legacy implementations to work with the new streaming API by:
 * <ul>
 *     <li>Converting synchronous token operations to asynchronous ones</li>
 *     <li>Mapping head/tail terminology (head=start, tail=end)</li>
 *     <li>Converting {@link BlockingStream} to {@link MessageStream}</li>
 *     <li>Handling {@link StreamingCondition} criteria filtering</li>
 * </ul>
 *
 * @param <E> The type of {@link EventMessage} streamed by this source.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class LegacyStreamableEventSource<E extends EventMessage<?>> implements StreamableEventSource<E> {

    private final StreamableMessageSource<E> delegate;
    private final Executor executor;

    /**
     * Creates a new {@code LegacyStreamableEventSource} that adapts the given {@code delegate}.
     * <p>
     * Uses a default executor optimized for I/O operations with virtual threads (Java 21+) or a cached thread pool for
     * earlier Java versions.
     *
     * @param delegate The {@link StreamableMessageSource} to adapt.
     */
    public LegacyStreamableEventSource(@Nonnull StreamableMessageSource<E> delegate) {
        this(delegate, null);
    }

    /**
     * Creates a new {@code LegacyStreamableEventSource} that adapts the given {@code delegate} using the specified
     * {@code executor} for asynchronous token operations.
     * <p>
     * It's recommended to use an executor suitable for potentially blocking I/O operations, such as a cached thread
     * pool or virtual thread executor.
     *
     * @param delegate The {@link StreamableMessageSource} to adapt.
     * @param executor The {@link Executor} to use for async operations. If {@code null}, a default I/O-optimized
     *                 executor will be created.
     */
    public LegacyStreamableEventSource(@Nonnull StreamableMessageSource<E> delegate,
                                       @Nullable Executor executor) {
        this.delegate = delegate;
        this.executor = executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public MessageStream<E> open(@Nonnull StreamingCondition condition) {
        TrackingToken position = condition.position();
        try (var blockingStream = delegate.openStream(position)) {
            return MessageStream.fromStream(blockingStream.asStream(), this::createEntryForMessage);
        }
    }

    // todo: is it needed?
    private MessageStream.Entry<E> createEntryForMessage(E message) {
        Context context = Context.empty();
        if (message instanceof TrackedEventMessage<?> trackedMessage) {
            context = TrackingToken.addToContext(context, trackedMessage.trackingToken());
        }
        return new SimpleEntry<>(message, context);
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.createHeadToken();
            } catch (UnsupportedOperationException e) {
                // Re-throw as the new interface expects this to be supported
                throw new UnsupportedOperationException("Delegate does not support head token creation", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.createTailToken();
            } catch (UnsupportedOperationException e) {
                // If the delegate doesn't support tail token creation, return null (default behavior)
                return null;
            }
        }, executor);
    }


    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.createTokenAt(at);
            } catch (UnsupportedOperationException e) {
                throw new UnsupportedOperationException("Delegate does not support time-based token creation", e);
            }
        }, executor);
    }
}