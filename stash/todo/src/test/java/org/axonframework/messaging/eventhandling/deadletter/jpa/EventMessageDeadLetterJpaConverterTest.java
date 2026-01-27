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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GapAwareTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EventMessageDeadLetterJpaConverter}.
 * <p>
 * In AF5, tracking tokens and domain info (aggregate identifier, type, sequence number) are stored as context resources
 * rather than message subtypes. This test verifies that the converter correctly handles these resources.
 */
class EventMessageDeadLetterJpaConverterTest {

    private final EventMessageDeadLetterJpaConverter converter = new EventMessageDeadLetterJpaConverter();
    private final JacksonConverter jacksonConverter = new JacksonConverter();
    private final EventConverter eventConverter = new DelegatingEventConverter(jacksonConverter);
    private final Converter genericConverter = jacksonConverter;
    private final ConverterTestEvent event = new ConverterTestEvent("myValue");
    private final MessageType type = new MessageType("event");
    private final Metadata metadata = Metadata.from(Collections.singletonMap("myMetadataKey", "myMetadataValue"));

    @Test
    void canConvertGenericEventMessageAndBackCorrectly() {
        EventMessage message = EventTestUtils.asEventMessage(event).andMetadata(metadata);
        Context context = Context.empty();
        testConversion(message, context);
    }

    @Test
    void canConvertEventMessageWithTrackingTokenInContext() {
        EventMessage message = EventTestUtils.asEventMessage(event).andMetadata(metadata);
        TrackingToken token = new GlobalSequenceTrackingToken(232323L);
        Context context = Context.empty()
                .withResource(TrackingToken.RESOURCE_KEY, token);

        testConversionWithContext(message, context);
    }

    @Test
    void canConvertEventMessageWithGapAwareTrackingTokenInContext() {
        EventMessage message = EventTestUtils.asEventMessage(event).andMetadata(metadata);
        TrackingToken token = new GapAwareTrackingToken(232323L, Arrays.asList(24L, 255L, 2225L));
        Context context = Context.empty()
                .withResource(TrackingToken.RESOURCE_KEY, token);

        testConversionWithContext(message, context);
    }

    @Test
    void canConvertEventMessageWithDomainInfoInContext() {
        EventMessage message = EventTestUtils.asEventMessage(event).andMetadata(metadata);
        Context context = Context.empty()
                .withResource(LegacyResources.AGGREGATE_TYPE_KEY, "MyAggregateType")
                .withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, "aggregate-123")
                .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, 42L);

        testConversionWithContext(message, context);
    }

    @Test
    void canConvertEventMessageWithTrackingTokenAndDomainInfoInContext() {
        EventMessage message = EventTestUtils.asEventMessage(event).andMetadata(metadata);
        TrackingToken token = new GlobalSequenceTrackingToken(999L);
        Context context = Context.empty()
                .withResource(TrackingToken.RESOURCE_KEY, token)
                .withResource(LegacyResources.AGGREGATE_TYPE_KEY, "OrderAggregate")
                .withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, "order-456")
                .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, 10L);

        testConversionWithContext(message, context);
    }

    @Test
    void canConvertGenericEventMessage() {
        EventMessage message = new GenericEventMessage(type, event, metadata);
        assertTrue(converter.canConvert(message));
    }

    @Test
    void canConvertDeadLetterEntryWithGenericEventMessageType() {
        EventMessage message = EventTestUtils.asEventMessage(event);
        DeadLetterEventEntry entry = converter.convert(message, Context.empty(), eventConverter, genericConverter);

        assertTrue(converter.canConvert(entry));
        assertEquals(GenericEventMessage.class.getName(), entry.getEventType());
    }

    @Test
    void canConvertLegacyTrackedDomainEventMessageType() {
        // Simulate a legacy entry stored with old event type
        DeadLetterEventEntry legacyEntry = new DeadLetterEventEntry(
                "org.axonframework.messaging.eventhandling.GenericTrackedDomainEventMessage",
                "msg-id-123",
                type.toString(),
                "2024-01-01T00:00:00Z",
                ConverterTestEvent.class.getName(),
                null,
                eventConverter.convert(event, byte[].class),
                eventConverter.convert(metadata, byte[].class),
                "AggregateType",
                "agg-123",
                5L,
                GlobalSequenceTrackingToken.class.getName(),
                genericConverter.convert(new GlobalSequenceTrackingToken(100L), byte[].class)
        );

        assertTrue(converter.canConvert(legacyEntry));
    }

    @Test
    void canConvertLegacyTrackedEventMessageType() {
        DeadLetterEventEntry legacyEntry = new DeadLetterEventEntry(
                "org.axonframework.messaging.eventhandling.GenericTrackedEventMessage",
                "msg-id-456",
                type.toString(),
                "2024-01-01T00:00:00Z",
                ConverterTestEvent.class.getName(),
                null,
                eventConverter.convert(event, byte[].class),
                eventConverter.convert(metadata, byte[].class),
                null,
                null,
                null,
                GlobalSequenceTrackingToken.class.getName(),
                genericConverter.convert(new GlobalSequenceTrackingToken(200L), byte[].class)
        );

        assertTrue(converter.canConvert(legacyEntry));
    }

    @Test
    void canConvertLegacyDomainEventMessageType() {
        DeadLetterEventEntry legacyEntry = new DeadLetterEventEntry(
                "org.axonframework.messaging.eventhandling.GenericDomainEventMessage",
                "msg-id-789",
                type.toString(),
                "2024-01-01T00:00:00Z",
                ConverterTestEvent.class.getName(),
                null,
                eventConverter.convert(event, byte[].class),
                eventConverter.convert(metadata, byte[].class),
                "SomeAggregate",
                "agg-789",
                15L,
                null,
                null
        );

        assertTrue(converter.canConvert(legacyEntry));
    }

    private void testConversion(EventMessage message, Context context) {
        assertTrue(converter.canConvert(message));
        DeadLetterEventEntry deadLetterEventEntry = converter.convert(message, context, eventConverter, genericConverter);

        assertCorrectlyMapped(message, context, deadLetterEventEntry);
        assertTrue(converter.canConvert(deadLetterEventEntry));

        MessageStream.Entry<EventMessage> restoredEntry =
                converter.convert(deadLetterEventEntry, eventConverter, genericConverter);
        assertCorrectlyRestored(message, restoredEntry.message());
    }

    private void testConversionWithContext(EventMessage message, Context context) {
        assertTrue(converter.canConvert(message));
        DeadLetterEventEntry deadLetterEventEntry = converter.convert(message, context, eventConverter, genericConverter);

        assertCorrectlyMapped(message, context, deadLetterEventEntry);
        assertTrue(converter.canConvert(deadLetterEventEntry));

        MessageStream.Entry<EventMessage> restoredEntry =
                converter.convert(deadLetterEventEntry, eventConverter, genericConverter);

        assertCorrectlyRestored(message, restoredEntry.message());
        assertContextRestored(context, restoredEntry);
    }

    private void assertCorrectlyRestored(EventMessage expected, EventMessage actual) {
        assertEquals(expected.identifier(), actual.identifier());
        assertEquals(expected.timestamp(), actual.timestamp());
        assertEquals(expected.payload(), actual.payload());
        assertEquals(expected.payloadType(), actual.payloadType());
        assertEquals(expected.metadata(), actual.metadata());

        // In AF5, all restored messages are GenericEventMessage
        assertTrue(actual instanceof GenericEventMessage);
    }

    private void assertContextRestored(Context originalContext, MessageStream.Entry<EventMessage> restoredEntry) {
        // Check tracking token restoration
        if (originalContext.containsResource(TrackingToken.RESOURCE_KEY)) {
            assertTrue(restoredEntry.containsResource(TrackingToken.RESOURCE_KEY));
            assertEquals(
                    originalContext.getResource(TrackingToken.RESOURCE_KEY),
                    restoredEntry.getResource(TrackingToken.RESOURCE_KEY)
            );
        }

        // Check domain info restoration
        if (originalContext.containsResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY)) {
            assertTrue(restoredEntry.containsResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY));
            assertEquals(
                    originalContext.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY),
                    restoredEntry.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY)
            );
        }
        if (originalContext.containsResource(LegacyResources.AGGREGATE_TYPE_KEY)) {
            assertTrue(restoredEntry.containsResource(LegacyResources.AGGREGATE_TYPE_KEY));
            assertEquals(
                    originalContext.getResource(LegacyResources.AGGREGATE_TYPE_KEY),
                    restoredEntry.getResource(LegacyResources.AGGREGATE_TYPE_KEY)
            );
        }
        if (originalContext.containsResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY)) {
            assertTrue(restoredEntry.containsResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY));
            assertEquals(
                    originalContext.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY),
                    restoredEntry.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY)
            );
        }
    }

    private void assertCorrectlyMapped(EventMessage eventMessage, Context context, DeadLetterEventEntry entry) {
        assertEquals(eventMessage.identifier(), entry.getEventIdentifier());
        assertEquals(eventMessage.timestamp().toString(), entry.getTimeStamp());
        assertEquals(eventMessage.payload().getClass().getName(), entry.getPayloadType());

        // Check tracking token storage from context
        if (context.containsResource(TrackingToken.RESOURCE_KEY)) {
            assertNotNull(entry.getToken());
            assertNotNull(entry.getTokenType());
            TrackingToken expectedToken = context.getResource(TrackingToken.RESOURCE_KEY);
            assertEquals(expectedToken.getClass().getName(), entry.getTokenType());
        } else {
            assertNull(entry.getToken());
            assertNull(entry.getTokenType());
        }

        // Check domain info storage from context
        if (context.containsResource(LegacyResources.AGGREGATE_TYPE_KEY)) {
            assertEquals(context.getResource(LegacyResources.AGGREGATE_TYPE_KEY), entry.getAggregateType());
        } else {
            assertNull(entry.getAggregateType());
        }
        if (context.containsResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY)) {
            assertEquals(context.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY), entry.getAggregateIdentifier());
        } else {
            assertNull(entry.getAggregateIdentifier());
        }
        if (context.containsResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY)) {
            assertEquals(context.getResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY), entry.getSequenceNumber());
        } else {
            assertNull(entry.getSequenceNumber());
        }
    }

    @Event
    public static class ConverterTestEvent {

        private final String myProperty;

        @JsonCreator
        public ConverterTestEvent(@JsonProperty("myProperty") String myProperty) {
            this.myProperty = myProperty;
        }

        @SuppressWarnings("unused")
        public String getMyProperty() {
            return myProperty;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ConverterTestEvent that = (ConverterTestEvent) o;

            return Objects.equals(myProperty, that.myProperty);
        }

        @Override
        public int hashCode() {
            return myProperty != null ? myProperty.hashCode() : 0;
        }
    }

    // Suppressed since it's used for testing serialization error scenarios
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class SerializationErrorClass {

        String myValue;
    }
}
