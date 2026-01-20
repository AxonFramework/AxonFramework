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

package org.axonframework.axonserver.connector.event;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.event.dcb.Event;
import io.axoniq.axonserver.grpc.event.dcb.TaggedEvent;
import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wrapper around the Axon Framework {@link EventConverter} that can convert
 * {@link TaggedEventMessage TaggedEventMessages} (Axon Framework representation) to {@link TaggedEvent TaggedEvents}
 * (Axon Server representation).
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class TaggedEventConverter implements DescribableComponent {

    private final EventConverter converter;

    /**
     * Constructs a {@code TaggedEventConverter} using the given {@code converter} to convert the
     * {@link EventMessage#payload() event payload}.
     *
     * @param converter The converter used to {@link EventConverter#convert(Object, Class)} the
     *                  {@link EventMessage#payload()} for the {@link Event}.
     */
    public TaggedEventConverter(@Nonnull EventConverter converter) {
        this.converter = Objects.requireNonNull(converter, "The EventConverter cannot be null.");
    }

    /**
     * Convert the given {@code taggedEvent} to a {@link TaggedEvent}.
     * <p>
     * Used to map Axon Framework events to Axon Server events while appending.
     *
     * @param taggedEvent The tagged event message to convert into a {@link TaggedEvent}.
     * @return A {@code TaggedEvent} based on the given {@code taggedEvent}.
     */
    public TaggedEvent convertTaggedEventMessage(@Nonnull TaggedEventMessage<?> taggedEvent) {
        return TaggedEvent.newBuilder()
                          .setEvent(convertEventMessage(taggedEvent.event()))
                          .addAllTag(convertTags(taggedEvent.tags()))
                          .build();
    }

    private Event convertEventMessage(EventMessage eventMessage) {
        return Event.newBuilder()
                    .setIdentifier(eventMessage.identifier())
                    .setTimestamp(eventMessage.timestamp().toEpochMilli())
                    .setName(eventMessage.type().name())
                    .setVersion(eventMessage.type().version())
                    .setPayload(convertPayload(eventMessage))
                    .putAllMetadata(convertMetadata(eventMessage.metadata()))
                    .build();
    }

    private ByteString convertPayload(EventMessage payload) {
        byte[] bytes = payload.payloadAs(byte[].class, converter);
        return bytes == null || bytes.length == 0 ? ByteString.EMPTY : ByteString.copyFrom(bytes);
    }

    private Map<String, String> convertMetadata(Metadata metadata) {
        return metadata.entrySet()
                       .stream()
                       .collect(Collectors.toUnmodifiableMap(
                               Map.Entry::getKey,
                               Map.Entry::getValue
                       ));
    }

    private static List<io.axoniq.axonserver.grpc.event.dcb.Tag> convertTags(Set<Tag> tags) {
        return tags.stream()
                   .map(TaggedEventConverter::convertTag)
                   .collect(Collectors.toList());
    }

    private static io.axoniq.axonserver.grpc.event.dcb.Tag convertTag(Tag tag) {
        return io.axoniq.axonserver.grpc.event.dcb.Tag.newBuilder()
                                                      .setKey(convertString(tag.key()))
                                                      .setValue(convertString(tag.value()))
                                                      .build();
    }

    private static ByteString convertString(String s) {
        return ByteString.copyFrom(s, StandardCharsets.UTF_8);
    }

    /**
     * Convert the given {@code event} to an {@link EventMessage}.
     * <p>
     * Used to map Axon Server events to Axon Framework events while sourcing and streaming.
     *
     * @param event The event to convert into an {@link EventMessage}.
     * @return An {@code EventMessage} based on the given {@code event}.
     */
    public EventMessage convertEvent(@Nonnull Event event) {
        return new GenericEventMessage(event.getIdentifier(),
                                       new MessageType(event.getName(), event.getVersion()),
                                       event.getPayload().toByteArray(),
                                       event.getMetadataMap(),
                                       Instant.ofEpochMilli(event.getTimestamp()));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("converter", converter);
    }
}
