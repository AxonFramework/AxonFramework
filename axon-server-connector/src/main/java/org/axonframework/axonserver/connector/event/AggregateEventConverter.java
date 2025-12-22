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

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import org.axonframework.axonserver.connector.MetadataConverter;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;

import java.time.Instant;
import java.util.function.Function;

/**
 * Converter for transforming Axon Server's gRPC {@link Event} objects (aggregate-based) into Axon Framework's
 * {@link EventMessage}.
 * <p>
 * This converter implements {@code Function<Event, EventMessage>} and provides a singleton {@link #INSTANCE} for
 * convenient reuse across components that need to convert aggregate-based events from Axon Server.
 * <p>
 * The converter handles:
 * <ul>
 *     <li>Event payload conversion (kept as raw bytes for lazy deserialization)</li>
 *     <li>Metadata conversion via {@link MetadataConverter}</li>
 *     <li>Message type with version/revision handling (defaults to {@link MessageType#DEFAULT_VERSION} if empty)</li>
 * </ul>
 * <p>
 * This converter is used by:
 * <ul>
 *     <li>{@link AggregateBasedAxonServerEventStorageEngine} - for streaming events from the event store</li>
 *     <li>{@link org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSource} - for persistent
 *     stream subscriptions</li>
 *     <li>{@link AxonServerMessageStream} - for event streaming</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @see TaggedEventConverter
 * @since 5.0.0
 */
public class AggregateEventConverter implements Function<Event, EventMessage> {

    /**
     * Singleton instance of the converter for convenient reuse.
     */
    public static final AggregateEventConverter INSTANCE = new AggregateEventConverter();

    /**
     * Constructs an {@code AggregateEventConverter}.
     */
    public AggregateEventConverter() {
    }

    /**
     * Converts the gRPC {@link Event} into an Axon Framework {@link EventMessage}.
     * <p>
     * The event payload is kept as raw bytes and will be deserialized lazily when accessed. If the payload revision is
     * {@code null} or empty, {@link MessageType#DEFAULT_VERSION} is used.
     *
     * @param event The gRPC event to convert.
     * @return An {@link EventMessage} containing the converted event data.
     */
    @Override
    public EventMessage apply(Event event) {
        return convertToMessage(event);
    }

    private EventMessage convertToMessage(Event event) {
        SerializedObject payload = event.getPayload();
        String revision = payload.getRevision();

        // TODO #3520: is it possible for revision to be empty? It's in the test
        String version = (revision.isEmpty()) ? MessageType.DEFAULT_VERSION : revision;
        return new GenericEventMessage(
                event.getMessageIdentifier(),
                new MessageType(payload.getType(), version),
                payload.getData().toByteArray(),
                new Metadata(MetadataConverter.convertMetadataValuesToGrpc(event.getMetaDataMap())),
                Instant.ofEpochMilli(event.getTimestamp())
        );
    }
}
