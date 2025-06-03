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
import org.axonframework.messaging.MessageStream;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface TrackingTokenSource {

    /**
     * Creates a {@link TrackingToken} pointing at the end of the {@link MessageStream event stream}.
     * <p>
     * The end of an event stream represents the token of the very last event in the stream.
     *
     * @return A {@link CompletableFuture} of {@link TrackingToken} pointing at the end of the
     * {@link MessageStream event stream}.
     */
    CompletableFuture<TrackingToken> headToken();

    /**
     * Creates a {@link TrackingToken} pointing at the start of the {@link MessageStream event stream}.
     * <p>
     * The start of an event stream represents the token of the very first event in the stream.
     *
     * @return A {@link CompletableFuture} of {@link TrackingToken} pointing at the start of the
     * {@link MessageStream event stream}.
     */
    CompletableFuture<TrackingToken> tailToken();

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
