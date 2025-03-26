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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.TrackingToken;


/**
 * Interface describing the condition to {@link StreamableEventSource#open(String, StreamingCondition) stream} events
 * from an Event Store.
 * <p>
 * This condition has a mandatory {@link #position()} that dictates from what point streaming should commence.
 * Additionally, an {@link #criteria()} can be set to filter the stream of events.
 *
 * @author Michal Negacz
 * @author Milan Savić
 * @author Marco Amann
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public sealed interface StreamingCondition extends EventsCondition permits DefaultStreamingCondition, StartingFrom {

    /**
     * Constructs a simple {@code  StreamingCondition} that starts streaming from the given {@code position}. When the
     * {@code position} is {@code null} streaming will start from the beginning of the Event Store.
     *
     * @param position The {@link TrackingToken} describing the position to start streaming from.
     * @return A simple {@code  StreamingCondition} that starts streaming from the given {@code position}.
     */
    static StreamingCondition startingFrom(@Nullable TrackingToken position) {
        return new StartingFrom(position);
    }

    /**
     * The position as a {@link TrackingToken} to start streaming from.
     *
     * @return The position as a {@link TrackingToken} to start streaming from.
     */
    TrackingToken position();

    default EventCriteria criteria() {
        return EventCriteria.anyEvent();
    }

    /**
     * Combines the {@link #criteria()} of {@code this} {@code  StreamingCondition} with the given {@code criteria}.
     *
     * @param criteria The {@link EventCriteria} to combine with the {@link #criteria()} of {@code this}
     *                 {@code  StreamingCondition}.
     * @return A {@code  StreamingCondition} that combined the given {@code criteria} with the {@link #criteria()} of
     * {@code this} {@code  StreamingCondition}.
     */
    StreamingCondition or(@Nonnull EventCriteria criteria);
}
