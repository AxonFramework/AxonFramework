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

package org.axonframework.messaging.eventstreaming;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Interface towards a streamable event source.
 * <p>
 * Provides functionality to {@link #open(StreamingCondition, ProcessingContext) open} an
 * {@link MessageStream event stream}.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0
 */
public interface StreamableEventSource extends TrackingTokenSource {

    /**
     * Open an {@link MessageStream event stream} containing all {@link EventMessage events} matching the given
     * {@code condition}.
     * <p>
     * To retrieve the {@link TrackingToken position} of the returned events, the
     * {@link TrackingToken#fromContext(Context)} operation should be used by providing the entire
     * {@link org.axonframework.messaging.core.MessageStream.Entry} wrapping the returned events.
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
     * @param context   The current {@link ProcessingContext}, if any.
     * @return An {@link MessageStream event stream} matching the given {@code condition}.
     */
    MessageStream<EventMessage> open(@Nonnull StreamingCondition condition, @Nullable ProcessingContext context);

}