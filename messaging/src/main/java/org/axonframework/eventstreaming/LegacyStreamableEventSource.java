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
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.SimpleEntry;
import org.axonframework.messaging.StreamableMessageSource;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
public class LegacyStreamableEventSource<E extends EventMessage<?>> implements StreamableEventSource<E> {

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
    public MessageStream<E> open(@Nonnull StreamingCondition condition) {
        TrackingToken position = condition.position();
        return new BlockingMessageStream<>(delegate.openStream(position), condition.criteria());
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        return delegate.headToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        return delegate.tailToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        return delegate.tokenAt(at);
    }

    /**
     * A {@code MessageStream} implementation backed by a {@link BlockingStream}. This implementation directly wraps the
     * BlockingStream and handles filtering based on criteria.
     *
     * @param <E> The type of {@link EventMessage} in the stream.
     */
    private static class BlockingMessageStream<E extends EventMessage<?>> implements MessageStream<E> {

        private final BlockingStream<E> stream;
        private final EventCriteria criteria;
        private Entry<E> peeked;

        BlockingMessageStream(BlockingStream<E> stream, EventCriteria criteria) {
            this.stream = stream;
            this.criteria = criteria;
        }

        @Override
        public Optional<Entry<E>> next() {
            if (peeked != null) {
                Entry<E> result = peeked;
                peeked = null;
                return Optional.of(result);
            }
            if (!stream.hasNextAvailable()) {
                return Optional.empty();
            }
            try {
                while (stream.hasNextAvailable()) {
                    E message = stream.nextAvailable();
                    if (message == null) {
                        return Optional.empty();
                    }
                    if (!matchesCriteria(message)) {
                        continue;
                    }
                    Entry<E> entry = createEntryForMessage(message);
                    return Optional.of(entry);
                }
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        @Override
        public Optional<Entry<E>> peek() {
            if (peeked != null) {
                return Optional.of(peeked);
            }
            if (!stream.hasNextAvailable()) {
                return Optional.empty();
            }
            try {
                Optional<E> result = stream.peek();
                while (result.isPresent()) {
                    E message = result.get();
                    if (!matchesCriteria(message)) {
                        // Advance the stream to skip this message
                        stream.nextAvailable();
                        result = stream.peek();
                        continue;
                    }
                    peeked = createEntryForMessage(message);
                    return Optional.of(peeked);
                }
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        private boolean matchesCriteria(EventMessage<?> message) {
            QualifiedName qualifiedName = message.type().qualifiedName();
            return criteria.matches(qualifiedName, Set.of());
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
            if (message instanceof TrackedEventMessage<?> trackedMessage) {
                context = TrackingToken.addToContext(context, trackedMessage.trackingToken());
            }
            context = context.withResource(Message.RESOURCE_KEY, message);
            return new SimpleEntry<>(message, context);
        }
    }
}