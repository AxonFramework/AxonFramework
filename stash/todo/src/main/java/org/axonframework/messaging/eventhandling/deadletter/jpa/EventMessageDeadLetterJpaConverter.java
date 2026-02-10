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

package org.axonframework.messaging.eventhandling.deadletter.jpa;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ClassUtils;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.SimpleEntry;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.time.Instant;
import java.util.Map;

/**
 * Converter responsible for converting to and from {@link EventMessage} implementations for storage in a
 * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue}.
 * <p>
 * In AF5, tracking tokens and domain info (aggregate identifier, type, sequence number) are stored as context resources
 * rather than message subtypes. This converter extracts these resources from the context during serialization and
 * restores them to the context when deserializing.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class EventMessageDeadLetterJpaConverter implements DeadLetterJpaConverter<EventMessage> {

    @Override
    public @Nonnull DeadLetterEventEntry convert(@Nonnull EventMessage message,
                                                 @Nullable Context context,
                                                 @Nonnull EventConverter eventConverter,
                                                 @Nonnull Converter genericConverter) {
        // Serialize payload and metadata
        byte[] serializedPayload = eventConverter.convert(message.payload(), byte[].class);
        byte[] serializedMetadata = eventConverter.convert(message.metadata(), byte[].class);

        // Extract tracking token from context (if present)
        // TODO: Maybe context should be Nonnull? Decide later.
        TrackingToken token = context.getResource(TrackingToken.RESOURCE_KEY);
        byte[] serializedToken = null;
        String tokenTypeName = null;
        if (token != null) {
            serializedToken = genericConverter.convert(token, byte[].class);
            tokenTypeName = token.getClass().getName();
        }

        // Extract domain info from context (if present)
        String aggregateIdentifier = context.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY);
        String aggregateType = context.getResource(LegacyResources.AGGREGATE_TYPE_KEY);
        Long sequenceNumber = context.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY);

        return new DeadLetterEventEntry(
                GenericEventMessage.class.getName(),
                message.identifier(),
                message.type().toString(),
                message.timestamp().toString(),
                message.payloadType().getName(),
                null, // revision not tracked separately in AF5
                serializedPayload,
                serializedMetadata,
                aggregateType,
                aggregateIdentifier,
                sequenceNumber,
                tokenTypeName,
                serializedToken
        );
    }

    @Nonnull
    @Override
    public MessageStream.Entry<EventMessage> convert(@Nonnull DeadLetterEventEntry entry,
                                                     @Nonnull EventConverter eventConverter,
                                                     @Nonnull Converter genericConverter) {
        // Deserialize payload and metadata
        Object payload = eventConverter.convert(entry.getPayload(), ClassUtils.loadClass(entry.getPayloadType()));
        @SuppressWarnings("unchecked")
        Map<String, String> metadataMap = eventConverter.convert(entry.getMetadata(), Map.class);
        Metadata metadata = Metadata.from(metadataMap);

        // Create GenericEventMessage
        EventMessage message = new GenericEventMessage(
                entry.getEventIdentifier(),
                MessageType.fromString(entry.getType()),
                payload,
                metadata,
                Instant.parse(entry.getTimeStamp())
        );

        // Build context with restored resources
        Context context = Context.empty();

        // Restore tracking token (if stored)
        if (entry.getToken() != null && entry.getTokenType() != null) {
            TrackingToken token = genericConverter.convert(entry.getToken(), ClassUtils.loadClass(entry.getTokenType()));
            context = context.withResource(TrackingToken.RESOURCE_KEY, token);
        }

        // Restore domain info (if stored)
        if (entry.getAggregateIdentifier() != null) {
            context = context.withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, entry.getAggregateIdentifier());
        }
        if (entry.getAggregateType() != null) {
            context = context.withResource(LegacyResources.AGGREGATE_TYPE_KEY, entry.getAggregateType());
        }
        if (entry.getSequenceNumber() != null) {
            context = context.withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, entry.getSequenceNumber());
        }

        return new SimpleEntry<>(message, context);
    }
}
