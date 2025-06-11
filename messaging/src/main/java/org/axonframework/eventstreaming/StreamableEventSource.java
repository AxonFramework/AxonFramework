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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Interface towards a streamable event source.
 * <p>
 * Provides functionality to {@link #open(StreamingCondition) open} an {@link MessageStream event stream} and to
 * retrieve {@link TrackingToken TrackingTokens} for the head and tail of the stream, and at a given point in time in
 * the stream.
 *
 * @param <E> The type of {@link EventMessage} streamed by this source.
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0
 */
public interface StreamableEventSource<E extends EventMessage<?>> {

    /**
     * Open an {@link MessageStream event stream} containing all {@link EventMessage events} matching the given
     * {@code condition}.
     * <p>
     * To retrieve the {@link TrackingToken position} of the returned events, the
     * {@link TrackingToken#fromContext(Context)} operation should be used by providing the entire
     * {@link org.axonframework.messaging.MessageStream.Entry} wrapping the returned events.
     * <p>
     * Note that the returned stream is <em>infinite</em>, so beware of applying terminal operations to the returned
     * stream.
     * <p>
     * When all events are of interest during streaming, then use {@link EventCriteria#havingAnyTag()} as the condition
     * criteria.
     *
     * @param condition The {@link StreamingCondition} defining the
     *                  {@link StreamingCondition#position() starting position} of the stream and
     *                  {@link StreamingCondition#criteria() event criteria} to filter the stream with.
     * @return An {@link MessageStream event stream} matching the given {@code condition}.
     */
    MessageStream<E> open(@Nonnull StreamingCondition condition);

    /**
     * Creates a {@link TrackingToken} representing the <b>first</b> position of the
     * {@link MessageStream event stream}.
     * <p>
     * As the retrieved token represents the point from which to {@link #open(StreamingCondition) open} the event
     * stream, the first event to be streamed when opening is the one right after the returned token.
     * <p>
     * Subsequent invocation of this method will yield the same result, <em>unless</em> the stream's initial values are
     * deleted.
     *
     * @return A {@link CompletableFuture} of a {@link TrackingToken} representing the <b>first</b> event of the
     * {@link MessageStream event stream}.
     */
    CompletableFuture<TrackingToken> firstToken();

    /**
     * Creates a {@link TrackingToken} representing the <b>latest</b> position, thus pointing at the next event of the
     * {@link MessageStream event stream}.
     * <p>
     * As the retrieved token represents the point from which to {@link #open(StreamingCondition) open} the event
     * stream, the first event to be streamed when opening is the one right after the returned token.
     * <p>
     * Since the {@link MessageStream event stream} of this source is theoretically <em>infinite</em>, subsequent
     * invocation of this operation typically return a different token. Only if this {@code StreamableEventSource} is
     * idle, will several {@code latestToken()} invocations result in the same {@code TrackingToken}.
     *
     * @return A {@link CompletableFuture} of a {@link TrackingToken} representing the <b>latest</b> event, thus
     * pointing at the next event of the {@link MessageStream event stream}.
     */
    CompletableFuture<TrackingToken> latestToken();

    /**
     * Creates a {@link TrackingToken} tracking all {@link EventMessage events} after the given {@code at} from an
     * {@link MessageStream event stream}.
     * <p>
     * When there is an {@link EventMessage} exactly at the given {@code dateTime}, it will be tracked too.
     *
     * @param at The {@link Instant} determining how the {@link TrackingToken} should be created. The returned token
     *           points at very first event before this {@code Instant}.
     * @return A {@link CompletableFuture} of {@link TrackingToken} pointing at the very first event before the given
     * {@code at} of the {@link MessageStream event stream}.
     */
    CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at);
}
