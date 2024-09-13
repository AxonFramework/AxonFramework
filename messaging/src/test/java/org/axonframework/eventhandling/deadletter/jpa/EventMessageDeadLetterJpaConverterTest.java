/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.eventhandling.deadletter.jpa;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.eventhandling.*;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class EventMessageDeadLetterJpaConverterTest {

    private static final String PAYLOAD_REVISION = "23.0";
    private final EventMessageDeadLetterJpaConverter converter = new EventMessageDeadLetterJpaConverter();
    private final Serializer eventSerializer = TestSerializer.JACKSON.getSerializer();
    private final Serializer genericSerializer = TestSerializer.XSTREAM.getSerializer();
    private final ConverterTestEvent event = new ConverterTestEvent("myValue");
    private final MetaData metaData = MetaData.from(Collections.singletonMap("myMetadataKey", "myMetadataValue"));

    @Test
    void canConvertGenericEventMessageAndBackCorrectly() {
        testConversion(GenericEventMessage.asEventMessage(event).andMetaData(metaData));
    }

    @Test
    void canConvertDomainEventMessageAndBackCorrectly() {
        testConversion(new GenericDomainEventMessage<>("MyType", "8239081092", 25L, event, metaData));
    }

    @Test
    void canConvertTrackedDomainEventMessageWithGlobalSequenceTokenAndBackCorrectly() {
        testConversion(new GenericTrackedDomainEventMessage<>(new GlobalSequenceTrackingToken(232323L),
                "MyType",
                "8239081092",
                25L,
                new GenericEventMessage<>(event, metaData),
                Instant::now));
    }

    @Test
    void canConvertMessagesWithSerializationErrors() {
        GenericEventMessage<Object> message = new GenericEventMessage<>(new SerializedMessage<>(
                "my-identifier",
                new SimpleSerializedObject<>(
                        "{'my-wrong-payload':'wadawd'}".getBytes(StandardCharsets.UTF_8),
                        byte[].class,
                        new SimpleSerializedType("org.axonframework.eventhandling.deadletter.jpa.EventMessageDeadLetterJpaConverterTest$SerializationErrorClass", null)
                ),
                new SimpleSerializedObject<>(
                        "{}".getBytes(StandardCharsets.UTF_8),
                        byte[].class,
                        new SimpleSerializedType("org.axonframework.messaging.MetaData", null)
                ),
                eventSerializer
        ), Instant::now);
        DeadLetterEventEntry deadLetterEventEntry = converter.convert(message, eventSerializer, genericSerializer);
        assertNotNull(deadLetterEventEntry);
        assertEquals("{'my-wrong-payload':'wadawd'}", new String(deadLetterEventEntry.getPayload().getData()));
    }

    @Test
    void canConvertTrackedDomainEventMessageWithGapAwareTokenAndBackCorrectly() {
        testConversion(new GenericTrackedDomainEventMessage<>(new GapAwareTrackingToken(232323L, Arrays.asList(24L, 255L, 2225L)),
                "MyType",
                "8239081092",
                25L,
                new GenericEventMessage<>(event, metaData),
                Instant::now));
    }

    @Test
    void canConvertTrackedEventMessageWithGlobalSequenceTokenAndBackCorrectly() {
        testConversion(new GenericTrackedEventMessage<>(new GlobalSequenceTrackingToken(232323L),
                new GenericEventMessage<>(event, metaData),
                Instant::now));
    }


    @Test
    void canConvertTrackedEventMessageWithGapAwareTokenAndBackCorrectly() {
        testConversion(new GenericTrackedEventMessage<>(new GapAwareTrackingToken(232323L, Arrays.asList(24L, 255L, 2225L)),
                new GenericEventMessage<>(event, metaData),
                Instant::now));
    }

    private void testConversion(EventMessage<?> message) {
        assertTrue(converter.canConvert(message));
        DeadLetterEventEntry deadLetterEventEntry = converter.convert(message, eventSerializer, genericSerializer);

        assertCorrectlyMapped(message, deadLetterEventEntry);
        assertTrue(converter.canConvert(deadLetterEventEntry));

        EventMessage<?> restoredEventMessage = converter.convert(deadLetterEventEntry, eventSerializer, genericSerializer);
        assertCorrectlyRestored(message, restoredEventMessage);
    }

    private void assertCorrectlyRestored(EventMessage<?> expected, EventMessage<?> actual) {
        assertEquals(expected.getIdentifier(), actual.getIdentifier());
        assertEquals(expected.getTimestamp(), actual.getTimestamp());
        assertEquals(expected.getPayload(), actual.getPayload());
        assertEquals(expected.getPayloadType(), actual.getPayloadType());
        assertEquals(expected.getMetaData(), actual.getMetaData());

        assertEquals(expected.getClass(), actual.getClass());
        if (expected instanceof DomainEventMessage) {
            DomainEventMessage<?> domainExpected = (DomainEventMessage<?>) expected;
            DomainEventMessage<?> domainActual = (DomainEventMessage<?>) actual;

            assertEquals(domainExpected.getType(), domainActual.getType());
            assertEquals(domainExpected.getAggregateIdentifier(), domainActual.getAggregateIdentifier());
            assertEquals(domainExpected.getSequenceNumber(), domainActual.getSequenceNumber());
        }
        if (expected instanceof TrackedEventMessage) {
            TrackedEventMessage<?> trackedExpected = (TrackedEventMessage<?>) expected;
            TrackedEventMessage<?> trackedActual = (TrackedEventMessage<?>) actual;

            assertEquals(trackedExpected.trackingToken(), trackedActual.trackingToken());
        }
    }

    private void assertCorrectlyMapped(EventMessage<?> eventMessage, DeadLetterEventEntry deadLetterEventEntry) {
        assertEquals(eventMessage.getIdentifier(), deadLetterEventEntry.getEventIdentifier());
        assertEquals(eventMessage.getTimestamp().toString(), deadLetterEventEntry.getTimeStamp());
        assertEquals(eventMessage.getPayload().getClass().getName(),
                deadLetterEventEntry.getPayload().getType().getName());
        assertEquals(PAYLOAD_REVISION, deadLetterEventEntry.getPayload().getType().getRevision());
        assertEquals(eventSerializer.serialize(event, String.class).getData(),
                new String(deadLetterEventEntry.getPayload().getData()));
        assertEquals(MetaData.class.getName(), deadLetterEventEntry.getMetaData().getType().getName());
        assertEquals(eventSerializer.serialize(metaData, String.class).getData(),
                new String(deadLetterEventEntry.getMetaData().getData()));

        if (eventMessage instanceof DomainEventMessage) {
            DomainEventMessage<?> domainEventMessage = (DomainEventMessage<?>) eventMessage;
            assertEquals(domainEventMessage.getType(), deadLetterEventEntry.getType());
            assertEquals(domainEventMessage.getAggregateIdentifier(), deadLetterEventEntry.getAggregateIdentifier());
            assertEquals(domainEventMessage.getSequenceNumber(), deadLetterEventEntry.getSequenceNumber());
        } else {
            assertNull(deadLetterEventEntry.getType());
            assertNull(deadLetterEventEntry.getAggregateIdentifier());
            assertNull(deadLetterEventEntry.getSequenceNumber());
        }
        if (eventMessage instanceof TrackedEventMessage) {
            TrackedEventMessage<?> trackedEventMessage = (TrackedEventMessage<?>) eventMessage;
            assertEquals(trackedEventMessage.trackingToken().getClass().getName(),
                    deadLetterEventEntry.getTrackingToken().getType().getName());
            assertEquals(genericSerializer.serialize(trackedEventMessage.trackingToken(), String.class).getData(),
                    new String(deadLetterEventEntry.getTrackingToken().getData()));
        } else {
            assertNull(deadLetterEventEntry.getTrackingToken());
        }
    }

    @Revision(EventMessageDeadLetterJpaConverterTest.PAYLOAD_REVISION)
    public static class ConverterTestEvent {

        private final String myProperty;

        @JsonCreator
        public ConverterTestEvent(@JsonProperty("myProperty") String myProperty) {
            this.myProperty = myProperty;
        }

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

    class SerializationErrorClass {
        String myValue;
    }
}
