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
import org.axonframework.common.TypeReference;
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
 * Tracking tokens and aggregate data (only if legacy Aggregate approach is used: aggregate identifier, type, sequence
 * number) are stored as {@link Context} resources. This converter extracts these resources from the context during
 * serialization and restores them to the context when deserializing.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class EventMessageDeadLetterJpaConverter implements DeadLetterJpaConverter<EventMessage> {

    private static final TypeReference<Map<String, String>> METADATA_MAP_TYPE_REF = new TypeReference<>() {
    };

    @Override
    public @Nonnull DeadLetterEventEntry convert(@Nonnull EventMessage message,
                                                 @Nullable Context context,
                                                 @Nonnull EventConverter eventConverter,
                                                 @Nonnull Converter genericConverter) {
        Context effectiveContext = context != null ? context : Context.empty();
        TrackingToken token = effectiveContext.getResource(TrackingToken.RESOURCE_KEY);

        return new DeadLetterEventEntry(
                message.type().toString(),
                message.identifier(),
                message.timestamp().toString(),
                eventConverter.convert(message.payload(), byte[].class),
                eventConverter.convert(message.metadata(), byte[].class),
                effectiveContext.getResource(LegacyResources.AGGREGATE_TYPE_KEY),
                effectiveContext.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY),
                effectiveContext.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY),
                token != null ? token.getClass().getName() : null,
                token != null ? genericConverter.convert(token, byte[].class) : null
        );
    }

    @Nonnull
    @Override
    public MessageStream.Entry<EventMessage> convert(@Nonnull DeadLetterEventEntry entry,
                                                     @Nonnull EventConverter eventConverter,
                                                     @Nonnull Converter genericConverter) {
        return new SimpleEntry<>(deserializeMessage(entry, eventConverter),
                                 restoreContext(entry, genericConverter));
    }

    private EventMessage deserializeMessage(DeadLetterEventEntry entry, EventConverter eventConverter) {
        Map<String, String> metadataMap = eventConverter.convert(entry.getMetadata(), METADATA_MAP_TYPE_REF.getType());

        return new GenericEventMessage(
                entry.getIdentifier(),
                MessageType.fromString(entry.getType()),
                entry.getPayload(),
                Metadata.from(metadataMap),
                Instant.parse(entry.getTimestamp())
        );
    }

    private Context restoreContext(DeadLetterEventEntry entry, Converter genericConverter) {
        Context context = Context.empty();
        if (entry.getToken() != null && entry.getTokenType() != null) {
            TrackingToken token = genericConverter.convert(entry.getToken(),
                                                           ClassUtils.loadClass(entry.getTokenType()));
            if (token != null) {
                context = context.withResource(TrackingToken.RESOURCE_KEY, token);
            }
        }
        if (entry.getAggregateIdentifier() != null) {
            context = context.withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY,
                                           entry.getAggregateIdentifier());
        }
        if (entry.getAggregateType() != null) {
            context = context.withResource(LegacyResources.AGGREGATE_TYPE_KEY, entry.getAggregateType());
        }
        if (entry.getAggregateSequenceNumber() != null) {
            context = context.withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY,
                                           entry.getAggregateSequenceNumber());
        }
        return context;
    }
}
