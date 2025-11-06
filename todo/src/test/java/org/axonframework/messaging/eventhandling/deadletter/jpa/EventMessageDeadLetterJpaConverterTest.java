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

package org.axonframework.messaging.eventhandling.deadletter.jpa;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GapAwareTrackingToken;
import org.axonframework.messaging.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.GenericTrackedDomainEventMessage;
import org.axonframework.messaging.eventhandling.GenericTrackedEventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.conversion.SerializedMessage;
import org.axonframework.conversion.SerializedType;
import org.axonframework.conversion.Serializer;
import org.axonframework.conversion.SimpleSerializedObject;
import org.axonframework.conversion.SimpleSerializedType;
import org.axonframework.conversion.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("TODO - #3517 - Tests currently broken due to revision resolution")
class EventMessageDeadLetterJpaConverterTest {

    private static final String PAYLOAD_REVISION = "23.0";
    private final EventMessageDeadLetterJpaConverter converter = new EventMessageDeadLetterJpaConverter();
    private final Serializer eventSerializer = JacksonSerializer.builder()
                                                                .objectMapper(new ObjectMapper().disable(
                                                                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                                                                .build();
    private final Serializer genericSerializer = eventSerializer;
    private final ConverterTestEvent event = new ConverterTestEvent("myValue");
    private final MessageType type = new MessageType("event");
    private final Metadata metadata = Metadata.from(Collections.singletonMap("myMetadataKey", "myMetadataValue"));

    @Test
    void canConvertGenericEventMessageAndBackCorrectly() {
        testConversion(EventTestUtils.asEventMessage(event).andMetadata(metadata));
    }

    @Test
    void canConvertDomainEventMessageAndBackCorrectly() {
        testConversion(new GenericDomainEventMessage("MyType", "8239081092", 25L, type, event, metadata));
    }

    @Test
    void canConvertTrackedDomainEventMessageWithGlobalSequenceTokenAndBackCorrectly() {
        testConversion(new GenericTrackedDomainEventMessage(new GlobalSequenceTrackingToken(232323L),
                                                              "MyType",
                                                              "8239081092",
                                                              25L,
                                                              new GenericEventMessage(type, event, metadata),
                                                              Instant::now));
    }

    @Test
    void canConvertMessagesWithSerializationErrors() {
        SerializedType eventType = new SimpleSerializedType(
                "org.axonframework.messaging.jpa.deadletter.eventhandling.EventMessageDeadLetterJpaConverterTest$SerializationErrorClass",
                null
        );
        EventMessage message = new GenericEventMessage(new SerializedMessage(
                "my-identifier",
                new SimpleSerializedObject<>(
                        "{\"my-wrong-payload\":\"wadawd\"}".getBytes(StandardCharsets.UTF_8),
// TODO #3517 - Revert back incorrect format and validate that it still works once we use a Converter i.o. a Serializer.
//                        "{'my-wrong-payload':'wadawd'}".getBytes(StandardCharsets.UTF_8),
                        byte[].class,
                        eventType
                ),
                new SimpleSerializedObject<>(
                        "{}".getBytes(StandardCharsets.UTF_8),
                        byte[].class,
                        new SimpleSerializedType("org.axonframework.messaging.Metadata", null)
                ),
                eventSerializer
        ), Instant::now);
        DeadLetterEventEntry deadLetterEventEntry = converter.convert(message, eventSerializer, genericSerializer);
        assertNotNull(deadLetterEventEntry);
        assertEquals("{\"myValue\":null}", new String(deadLetterEventEntry.getPayload().getData()));
    }

    @Test
    void canConvertTrackedDomainEventMessageWithGapAwareTokenAndBackCorrectly() {
        TrackingToken testToken = new GapAwareTrackingToken(232323L, Arrays.asList(24L, 255L, 2225L));
        testConversion(new GenericTrackedDomainEventMessage(testToken,
                                                              "MyType",
                                                              "8239081092",
                                                              25L,
                                                              new GenericEventMessage(type, event, metadata),
                                                              Instant::now));
    }

    @Test
    void canConvertTrackedEventMessageWithGlobalSequenceTokenAndBackCorrectly() {
        testConversion(new GenericTrackedEventMessage(new GlobalSequenceTrackingToken(232323L),
                                                        new GenericEventMessage(type, event, metadata),
                                                        Instant::now));
    }


    @Test
    void canConvertTrackedEventMessageWithGapAwareTokenAndBackCorrectly() {
        TrackingToken testToken = new GapAwareTrackingToken(232323L, Arrays.asList(24L, 255L, 2225L));
        testConversion(new GenericTrackedEventMessage(testToken,
                                                        new GenericEventMessage(type, event, metadata),
                                                        Instant::now));
    }

    private void testConversion(EventMessage message) {
        assertTrue(converter.canConvert(message));
        DeadLetterEventEntry deadLetterEventEntry = converter.convert(message, eventSerializer, genericSerializer);

        assertCorrectlyMapped(message, deadLetterEventEntry);
        assertTrue(converter.canConvert(deadLetterEventEntry));

        EventMessage restoredEventMessage =
                converter.convert(deadLetterEventEntry, eventSerializer, genericSerializer);
        assertCorrectlyRestored(message, restoredEventMessage);
    }

    private void assertCorrectlyRestored(EventMessage expected, EventMessage actual) {
        assertEquals(expected.identifier(), actual.identifier());
        assertEquals(expected.timestamp(), actual.timestamp());
        assertEquals(expected.payload(), actual.payload());
        assertEquals(expected.payloadType(), actual.payloadType());
        assertEquals(expected.metadata(), actual.metadata());

        assertEquals(expected.getClass(), actual.getClass());
        if (expected instanceof DomainEventMessage domainExpected) {
            DomainEventMessage domainActual = (DomainEventMessage) actual;

            assertEquals(domainExpected.getType(), domainActual.getType());
            assertEquals(domainExpected.getAggregateIdentifier(), domainActual.getAggregateIdentifier());
            assertEquals(domainExpected.getSequenceNumber(), domainActual.getSequenceNumber());
        }
        if (expected instanceof TrackedEventMessage trackedExpected) {
            TrackedEventMessage trackedActual = (TrackedEventMessage) actual;

            assertEquals(trackedExpected.trackingToken(), trackedActual.trackingToken());
        }
    }

    private void assertCorrectlyMapped(EventMessage eventMessage, DeadLetterEventEntry deadLetterEventEntry) {
        assertEquals(eventMessage.identifier(), deadLetterEventEntry.getEventIdentifier());
        assertEquals(eventMessage.timestamp().toString(), deadLetterEventEntry.getTimeStamp());
        assertEquals(eventMessage.payload().getClass().getName(),
                     deadLetterEventEntry.getPayload().getType().getName());
        assertEquals(PAYLOAD_REVISION, deadLetterEventEntry.getPayload().getType().getRevision());
        assertEquals(eventSerializer.serialize(event, String.class).getData(),
                     new String(deadLetterEventEntry.getPayload().getData()));
        assertEquals(Metadata.class.getName(), deadLetterEventEntry.getMetadata().getType().getName());
        assertEquals(eventSerializer.serialize(metadata, String.class).getData(),
                     new String(deadLetterEventEntry.getMetadata().getData()));

        if (eventMessage instanceof DomainEventMessage domainEventMessage) {
            assertEquals(domainEventMessage.getType(), deadLetterEventEntry.getAggregateType());
            assertEquals(domainEventMessage.getAggregateIdentifier(), deadLetterEventEntry.getAggregateIdentifier());
            assertEquals(domainEventMessage.getSequenceNumber(), deadLetterEventEntry.getSequenceNumber());
        } else {
            assertNull(deadLetterEventEntry.getAggregateType());
            assertNull(deadLetterEventEntry.getAggregateIdentifier());
            assertNull(deadLetterEventEntry.getSequenceNumber());
        }
        if (eventMessage instanceof TrackedEventMessage trackedEventMessage) {
            assertEquals(trackedEventMessage.trackingToken().getClass().getName(),
                         deadLetterEventEntry.getTrackingToken().getType().getName());
            assertEquals(genericSerializer.serialize(trackedEventMessage.trackingToken(), String.class).getData(),
                         new String(deadLetterEventEntry.getTrackingToken().getData()));
        } else {
            assertNull(deadLetterEventEntry.getTrackingToken());
        }
    }

    @Event(version = EventMessageDeadLetterJpaConverterTest.PAYLOAD_REVISION)
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

    // Suppressed since it's used for test 'canConvertMessagesWithSerializationErrors'
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class SerializationErrorClass {

        String myValue;
    }
}
