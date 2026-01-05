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

package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.streams.PersistentStreamEvent;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.event.AggregateEventConverter;
import org.axonframework.common.StringUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.SimpleEntry;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.util.Objects;
import java.util.function.Function;

/**
 * Converter for transforming Axon Server's {@link PersistentStreamEvent} objects into Axon Framework's
 * {@link MessageStream.Entry} containing an {@link EventMessage}.
 * <p>
 * This converter implements {@code Function<PersistentStreamEvent, MessageStream.Entry<EventMessage>>} for convenient reuse.
 * <p>
 * The converter handles:
 * <ul>
 *     <li>Event conversion via a configurable {@link Function} (defaults to {@link AggregateEventConverter})</li>
 *     <li>Tracking token creation with replay detection</li>
 *     <li>Context building with aggregate information when available</li>
 * </ul>
 * <p>
 * The resulting {@link Context} contains:
 * <ul>
 *   <li>{@link TrackingToken#RESOURCE_KEY} - the tracking token (or {@link ReplayToken} if replaying)</li>
 *   <li>{@link LegacyResources} keys for aggregate info (if present in the event)</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @see AggregateEventConverter
 * @since 5.0.0
 */
@Internal
public class PersistentStreamEventConverter implements Function<PersistentStreamEvent, MessageStream.Entry<EventMessage>> {

    private final Function<Event, EventMessage> messageConverter;

    /**
     * Constructs a {@code PersistentStreamEventConverter} with a custom message converter.
     *
     * @param messageConverter The function to convert gRPC {@link Event} to {@link EventMessage}.
     */
    public PersistentStreamEventConverter(Function<Event, EventMessage> messageConverter) {
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter must not be null");
    }

    /**
     * Converts a {@link PersistentStreamEvent} to a {@link MessageStream.Entry}.
     *
     * @param persistentStreamEvent The persistent stream event to convert.
     * @return A {@link MessageStream.Entry} containing the converted event message with context.
     */
    @Override
    @Nonnull
    public MessageStream.Entry<EventMessage> apply(PersistentStreamEvent persistentStreamEvent) {
        EventWithToken eventWithToken = persistentStreamEvent.getEvent();
        Event event = eventWithToken.getEvent();

        EventMessage message = messageConverter.apply(event);
        TrackingToken token = createTrackingToken(persistentStreamEvent);
        Context context = buildContext(token, event);

        return new SimpleEntry<>(message, context);
    }

    /**
     * Creates the appropriate {@link TrackingToken} for the given persistent stream event.
     * <p>
     * If the event is marked as a replay, a {@link ReplayToken} is created wrapping the global sequence token.
     *
     * @param persistentStreamEvent The persistent stream event.
     * @return The tracking token for this event.
     */
    private TrackingToken createTrackingToken(PersistentStreamEvent persistentStreamEvent) {
        long tokenValue = persistentStreamEvent.getEvent().getToken();
        GlobalSequenceTrackingToken globalToken = new GlobalSequenceTrackingToken(tokenValue);

        if (persistentStreamEvent.getReplay()) {
            return ReplayToken.createReplayToken(
                    new GlobalSequenceTrackingToken(tokenValue + 1),
                    globalToken
            );
        }
        return globalToken;
    }

    /**
     * Builds a {@link Context} containing the tracking token and aggregate information from the event.
     *
     * @param trackingToken The tracking token for this event.
     * @param event         The gRPC event containing aggregate information.
     * @return A context with tracking token and aggregate resources.
     */
    private Context buildContext(TrackingToken trackingToken, Event event) {
        Context context = Context.with(TrackingToken.RESOURCE_KEY, trackingToken);

        if (StringUtils.nonEmptyOrNull(event.getAggregateIdentifier())) {
            context = context
                    .withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, event.getAggregateIdentifier())
                    .withResource(LegacyResources.AGGREGATE_TYPE_KEY, event.getAggregateType())
                    .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, event.getAggregateSequenceNumber());
        }
        return context;
    }
}
