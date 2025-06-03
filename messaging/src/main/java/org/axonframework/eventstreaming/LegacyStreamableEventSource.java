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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SimpleEntry;
import org.axonframework.messaging.StreamableMessageSource;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

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

    /**
     * Creates a new {@code LegacyStreamableEventSource} that adapts the given {@code delegate}.
     *
     * @param delegate The {@link StreamableMessageSource} to adapt.
     */
    public LegacyStreamableEventSource(@Nonnull StreamableMessageSource<E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public MessageStream<E> open(@Nonnull StreamingCondition condition) {
        TrackingToken position = condition.position();
        try (var blockingStream = delegate.openStream(position)) {
            return MessageStream.fromStream(blockingStream.asStream(), this::createEntryForMessage);
        }
        //         return MessageStream.fromStream(delegate.openStream(position).asStream(), this::createEntryForMessage);
    }

    private MessageStream.Entry<E> createEntryForMessage(E message) {
        Context context = Context.empty();
        if (message instanceof TrackedEventMessage<?> trackedMessage) {
            context = TrackingToken.addToContext(context, trackedMessage.trackingToken());
        }
        return new SimpleEntry<>(message, context);
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
}