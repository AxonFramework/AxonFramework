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

import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.streams.PersistentStreamEvent;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.MetadataConverter;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.SimpleEntry;

import java.time.Instant;
import java.util.Objects;

/**
 * Converter for transforming Axon Server's {@link PersistentStreamEvent} objects into Axon Framework's
 * {@link MessageStream.Entry} objects containing {@link EventMessage EventMessages}.
 * <p>
 * This converter handles the conversion of gRPC-based persistent stream events received from Axon Server into
 * Axon Framework's domain model. It properly handles:
 * <ul>
 *     <li>Event payload conversion (as raw bytes)</li>
 *     <li>Metadata conversion</li>
 *     <li>Tracking token creation, including {@link ReplayToken} when events are marked as replay</li>
 *     <li>Pairing the {@link TrackingToken} with the event via {@link Context}</li>
 * </ul>
 * <p>
 * The converter follows the AF5 pattern of pairing {@link TrackingToken} through {@link MessageStream.Entry}'s
 * {@link Context} rather than using the deprecated {@code TrackedEventMessage}.
 * <p>
 * This class is intended for internal use within the persistent stream infrastructure.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 * @see PersistentStreamConnection
 * @see MessageStream.Entry
 */
@Internal
public class PersistentStreamEventConverter implements DescribableComponent {

    /**
     * Constructs a {@code PersistentStreamEventConverter}.
     */
    public PersistentStreamEventConverter() {
        // Default constructor
    }

    /**
     * Converts a {@link PersistentStreamEvent} received from Axon Server into a {@link MessageStream.Entry}
     * containing an {@link EventMessage}.
     * <p>
     * The conversion process:
     * <ol>
     *     <li>Extracts the {@link EventWithToken} from the persistent stream event</li>
     *     <li>Creates an appropriate {@link TrackingToken} - either a {@link GlobalSequenceTrackingToken}
     *         or a {@link ReplayToken} if the event is marked as a replay</li>
     *     <li>Converts the event payload, metadata, and other properties into an {@link EventMessage}</li>
     *     <li>Wraps everything into a {@link SimpleEntry} with the token in the {@link Context}</li>
     * </ol>
     *
     * @param persistentStreamEvent The persistent stream event from Axon Server to convert.
     * @return A {@link MessageStream.Entry} containing the converted event and tracking token in context.
     * @throws NullPointerException if {@code persistentStreamEvent} is {@code null}
     */
    public MessageStream.Entry<EventMessage> convert(@Nonnull PersistentStreamEvent persistentStreamEvent) {
        Objects.requireNonNull(persistentStreamEvent, "PersistentStreamEvent must not be null");

        EventWithToken eventWithToken = persistentStreamEvent.getEvent();
        TrackingToken trackingToken = createTrackingToken(persistentStreamEvent);
        EventMessage eventMessage = convertEventMessage(eventWithToken.getEvent());
        Context context = Context.with(TrackingToken.RESOURCE_KEY, trackingToken);

        return new SimpleEntry<>(eventMessage, context);
    }

    /**
     * Extracts just the {@link EventMessage} from a {@link PersistentStreamEvent} without the tracking token.
     * <p>
     * This method is useful when only the event message is needed without the associated tracking context.
     *
     * @param persistentStreamEvent The persistent stream event from Axon Server to convert.
     * @return An {@link EventMessage} containing the converted event data.
     * @throws NullPointerException if {@code persistentStreamEvent} is {@code null}
     */
    public EventMessage convertToEventMessage(@Nonnull PersistentStreamEvent persistentStreamEvent) {
        Objects.requireNonNull(persistentStreamEvent, "PersistentStreamEvent must not be null");
        return convertEventMessage(persistentStreamEvent.getEvent().getEvent());
    }

    /**
     * Creates the appropriate {@link TrackingToken} for the given persistent stream event.
     * <p>
     * If the event is marked as a replay, a {@link ReplayToken} is created that wraps the current position
     * and indicates the replay context. Otherwise, a simple {@link GlobalSequenceTrackingToken} is returned.
     *
     * @param persistentStreamEvent The persistent stream event to create a token for.
     * @return The appropriate tracking token for the event.
     */
    public TrackingToken createTrackingToken(@Nonnull PersistentStreamEvent persistentStreamEvent) {
        Objects.requireNonNull(persistentStreamEvent, "PersistentStreamEvent must not be null");

        long token = persistentStreamEvent.getEvent().getToken();
        GlobalSequenceTrackingToken globalToken = new GlobalSequenceTrackingToken(token);

        if (persistentStreamEvent.getReplay()) {
            // For replay events, create a ReplayToken that indicates we're in replay mode
            // The "upper bound" is set to token + 1 to indicate where the replay ends
            return ReplayToken.createReplayToken(
                    new GlobalSequenceTrackingToken(token + 1),
                    globalToken
            );
        }

        return globalToken;
    }

    /**
     * Converts the gRPC {@link Event} into an Axon Framework {@link EventMessage}.
     * <p>
     * The event payload is kept as raw bytes and will be deserialized lazily when accessed.
     *
     * @param event The gRPC event to convert.
     * @return An {@link EventMessage} containing the converted event data.
     */
    private EventMessage convertEventMessage(Event event) {
        SerializedObject payload = event.getPayload();
        String revision = payload.getRevision();
        // MessageType requires non-empty version, use default if revision is empty/null
        String version = (revision == null || revision.isEmpty()) ? MessageType.DEFAULT_VERSION : revision;
        return new GenericEventMessage(
                event.getMessageIdentifier(),
                new MessageType(payload.getType(), version),
                payload.getData().toByteArray(),
                new Metadata(MetadataConverter.convertMetadataValuesToGrpc(event.getMetaDataMap())),
                Instant.ofEpochMilli(event.getTimestamp())
        );
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("type", "PersistentStreamEventConverter");
    }
}
