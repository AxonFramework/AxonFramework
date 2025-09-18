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
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SimpleEntry;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter implementation that translates from the deprecated {@link StreamableMessageSource} interface to the new
 * {@link StreamableEventSource} interface.
 * <p>
 * This adapter allows legacy implementations to work with the new streaming API by:
 * <ul>
 *     <li>Converting {@link BlockingStream} to {@link MessageStream}</li>
 *     <li>Handling {@link StreamingCondition} criteria filtering</li>
 * </ul>
 *
 * @param <E> The type of {@link EventMessage} streamed by this source.
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Deprecated(since = "5.0.0")
public class LegacyStreamableEventSource<E extends EventMessage> implements StreamableEventSource<E> {

    private final StreamableMessageSource<E> delegate;

    /**
     * Creates a new {@code LegacyStreamableEventSource} that adapts the given {@code delegate}.
     *
     * @param delegate The {@link StreamableMessageSource} to adapt.
     */
    public LegacyStreamableEventSource(@Nonnull StreamableMessageSource<E> delegate) {
        Objects.requireNonNull(delegate, "Delegate is required");
        this.delegate = delegate;
    }

    @Override
    public MessageStream<E> open(@Nonnull StreamingCondition condition, @Nullable ProcessingContext context) {
        TrackingToken position = condition.position();
        return new BlockingMessageStream<>(delegate.openStream(position), condition.criteria());
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext context) {
        return delegate.firstToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext context) {
        return delegate.latestToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at, @Nullable ProcessingContext context) {
        return delegate.tokenAt(at, context);
    }

    /**
     * A {@code MessageStream} implementation backed by a {@link BlockingStream}. This implementation directly wraps the
     * BlockingStream and handles filtering based on criteria.
     *
     * @param <E> The type of {@link EventMessage} in the stream.
     */
    private static class BlockingMessageStream<E extends EventMessage> implements MessageStream<E> {

        private final BlockingStream<E> stream;

        BlockingMessageStream(BlockingStream<E> stream, EventCriteria criteria) {
            this.stream = stream;
            if (criteria != AnyEvent.INSTANCE) {
                throw new IllegalArgumentException(
                        "Only AnyEvent criteria is supported in this legacy adapter, but received: " + criteria);
            }
        }

        @Override
        public Optional<Entry<E>> next() {
            try {
                if (!stream.hasNextAvailable()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(stream.nextAvailable()).map(this::createEntryForMessage);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        @Override
        public Optional<Entry<E>> peek() {
            return stream.peek().map(this::createEntryForMessage);
        }

        @Override
        public void onAvailable(@Nonnull Runnable callback) {
            stream.setOnAvailableCallback(callback);
        }

        @Override
        public Optional<Throwable> error() {
            // BlockingStream doesn't have an error reporting, so we return empty
            return Optional.empty();
        }

        @Override
        public boolean isCompleted() {
            // A BlockingStream doesn't have a completion concept in the same way
            // We can consider it completed if it has no more available messages
            // This is a best-effort implementation
            return !stream.hasNextAvailable() && stream.peek().isEmpty();
        }

        @Override
        public boolean hasNextAvailable() {
            return stream.hasNextAvailable();
        }

        @Override
        public void close() {
            stream.close();
        }

        private Entry<E> createEntryForMessage(E message) {
            Context context = Context.empty();
            if (message instanceof TrackedEventMessage trackedMessage) {
                context = TrackingToken.addToContext(context, trackedMessage.trackingToken());
            }
            context = context.withResource(Message.RESOURCE_KEY, message);
            return new SimpleEntry<>(message, context);
        }
    }
}