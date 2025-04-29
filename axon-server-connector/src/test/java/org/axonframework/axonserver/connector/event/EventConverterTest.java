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

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.event.dcb.Event;
import io.axoniq.axonserver.grpc.event.dcb.TaggedEvent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.GenericTaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


/**
 * Test class validating the {@link EventConverter}.
 *
 * @author Steven van Beelen
 */
class EventConverterTest {

    private Converter converter;

    private EventConverter testSubject;

    @BeforeEach
    void setUp() {
        converter = spy(new TestConverter());

        testSubject = new EventConverter(converter);
    }

    @Test
    void throwsNullPointerExceptionForNullConverter() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new EventConverter(null));
    }

    @Test
    void convertTaggedEventMessageThrowsNullPointerExceptionForNullTaggedEventMessage() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.convertTaggedEventMessage(null));
    }

    @Test
    void convertsTaggedEventMessageInTaggedEventAsExpected() {
        // given...
        String id = UUID.randomUUID().toString();
        MessageType type = new MessageType("event-name", "event-version");
        TestEvent payload = new TestEvent("Lorem Ipsum", 42, List.of(true, false));
        MetaData metaData = MetaData.with("String", "Lorem Ipsum");
        Instant timestamp = Instant.now();
        EventMessage<TestEvent> eventMessage = new GenericEventMessage<>(id, type, payload, metaData, timestamp);
        Set<Tag> tags = Set.of(Tag.of("key", "value"), Tag.of("key2", "value2"), Tag.of("key3", "value3"));
        TaggedEventMessage<EventMessage<TestEvent>> taggedEventMessage =
                new GenericTaggedEventMessage<>(eventMessage, tags);
        // when...
        TaggedEvent result = testSubject.convertTaggedEventMessage(taggedEventMessage);
        // then...
        Event resultEvent = result.getEvent();
        assertEquals(id, resultEvent.getIdentifier());
        assertEquals(timestamp.toEpochMilli(), resultEvent.getTimestamp());
        assertEquals(type.name(), resultEvent.getName());
        assertEquals(type.version(), resultEvent.getVersion());
        verify(converter).convert(payload, byte[].class);
        assertArrayEquals(converter.convert(payload, byte[].class), resultEvent.getPayload().toByteArray());
        Map<String, String> resultMetaData = resultEvent.getMetadataMap();
        assertEquals(1, resultMetaData.size());
        assertTrue(resultMetaData.containsKey("String"));
        assertEquals("Lorem Ipsum", resultMetaData.get("String"));
        List<io.axoniq.axonserver.grpc.event.dcb.Tag> tagList = result.getTagList();
        assertEquals(3, tagList.size());
        assertTrue(tagList.contains(
                io.axoniq.axonserver.grpc.event.dcb.Tag.newBuilder()
                                                       .setKey(ByteString.copyFrom("key", StandardCharsets.UTF_8))
                                                       .setValue(ByteString.copyFrom("value", StandardCharsets.UTF_8))
                                                       .build()
        ));
        assertTrue(tagList.contains(
                io.axoniq.axonserver.grpc.event.dcb.Tag.newBuilder()
                                                       .setKey(ByteString.copyFrom("key2", StandardCharsets.UTF_8))
                                                       .setValue(ByteString.copyFrom("value2", StandardCharsets.UTF_8))
                                                       .build()
        ));
        assertTrue(tagList.contains(
                io.axoniq.axonserver.grpc.event.dcb.Tag.newBuilder()
                                                       .setKey(ByteString.copyFrom("key3", StandardCharsets.UTF_8))
                                                       .setValue(ByteString.copyFrom("value3", StandardCharsets.UTF_8))
                                                       .build()
        ));
    }

    @Test
    void convertsAnyTypeOfMetaDataAsExpected() {
        // given...
        MessageType type = new MessageType("event-name", "event-version");
        TestEvent payload = new TestEvent("Lorem Ipsum", 42, List.of(true, false));
        TestMetaDataValue metaDataValue = new TestMetaDataValue(1337, "string");
        MetaData metaData = MetaData.from(Map.of(
                "String", "Lorem Ipsum",
                "Double", 3.53d,
                "Float", 3.53f,
                "Long", 42L,
                "Integer", 42,
                "Short", (short) 42,
                "Byte", (byte) 4,
                "Boolean", false,
                "Object", metaDataValue
        ));
        EventMessage<TestEvent> eventMessage = new GenericEventMessage<>(type, payload, metaData);
        TaggedEventMessage<EventMessage<TestEvent>> taggedEventMessage =
                new GenericTaggedEventMessage<>(eventMessage, Set.of(Tag.of("key", "value")));
        // when...
        Map<String, String> result = testSubject.convertTaggedEventMessage(taggedEventMessage)
                                                .getEvent()
                                                .getMetadataMap();
        // then...
        assertEquals(9, result.size());
        assertEquals("Lorem Ipsum", result.get("String"));
        assertEquals(3.53d, Double.parseDouble(result.get("Double")));
        assertEquals(3.53f, Float.parseFloat(result.get("Float")));
        assertEquals(42L, Long.parseLong(result.get("Long")));
        assertEquals(42, Integer.parseInt(result.get("Integer")));
        assertEquals((short) 42, Short.parseShort(result.get("Short")));
        assertEquals((byte) 4, Byte.parseByte(result.get("Byte")));
        assertFalse(Boolean.parseBoolean(result.get("Boolean")));
        verify(converter).convert(metaDataValue, String.class);
        assertEquals(converter.convert(metaDataValue, String.class), result.get("Object"));
    }

    private record TestEvent(String stringState, Integer intState, List<Boolean> booleanState) {

    }

    private record TestMetaDataValue(Integer integerState, String stringState) {

    }
}