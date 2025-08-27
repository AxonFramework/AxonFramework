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

/**
 * Interface towards a streamable event source.
 * <p>
 * Provides functionality to {@link #open(StreamingCondition) open} an {@link MessageStream event stream}.
 *
 * @param <E> The type of {@link EventMessage} streamed by this source.
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0
 */
public interface StreamableEventSource<E extends EventMessage> extends TrackingTokenSource {

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

}
