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
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.json.JacksonConverter;
import org.junit.jupiter.api.*;
import org.mockito.*;

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

    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final Long EVENT_TIMESTAMP = Instant.now().toEpochMilli();
    private static final String EVENT_NAME = "event-name";
    private static final String EVENT_VERSION = "event-version";
    private static final MessageType EVENT_TYPE = new MessageType(EVENT_NAME, EVENT_VERSION);
    private static final Map<String, String> EVENT_METADATA = Map.of("String", "Lorem Ipsum");

    private Converter converter;

    private EventConverter testSubject;

    private TestEvent eventPayload;
    private byte[] eventPayloadByteArray;

    @BeforeEach
    void setUp() {
        converter = spy(new JacksonConverter());

        testSubject = new EventConverter(converter);

        eventPayload = new TestEvent("Lorem Ipsum", 42, List.of(true, false));
        eventPayloadByteArray = converter.convert(eventPayload, byte[].class);

        Mockito.clearInvocations(converter);
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
    void convertTaggedEventMessageWorksAsExpected() {
        // given...
        EventMessage<TestEvent> eventMessage = new GenericEventMessage<>(
                EVENT_ID, EVENT_TYPE, eventPayload, EVENT_METADATA, Instant.ofEpochMilli(EVENT_TIMESTAMP)
        );
        Set<Tag> tags = Set.of(Tag.of("key", "value"), Tag.of("key2", "value2"), Tag.of("key3", "value3"));
        TaggedEventMessage<EventMessage<TestEvent>> taggedEventMessage =
                new GenericTaggedEventMessage<>(eventMessage, tags);
        // when...
        TaggedEvent result = testSubject.convertTaggedEventMessage(taggedEventMessage);
        // then...
        Event resultEvent = result.getEvent();
        assertEquals(EVENT_ID, resultEvent.getIdentifier());
        assertEquals(EVENT_TIMESTAMP, resultEvent.getTimestamp());
        assertEquals(EVENT_NAME, resultEvent.getName());
        assertEquals(EVENT_VERSION, resultEvent.getVersion());
        verify(converter).convert(eventPayload, byte[].class);
        assertArrayEquals(eventPayloadByteArray, resultEvent.getPayload().toByteArray());
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
    void convertTaggedEventMessageConvertsAnyTypeOfMetaData() {
        // given...
        MetaData metaData = MetaData.from(Map.of(
                "String", "Lorem Ipsum",
                "Double", "3.53d",
                "Float", "3.53f",
                "Long", "42L",
                "Integer", "42",
                "Short", "42",
                "Byte", "4",
                "Boolean", "false"
        ));
        EventMessage<TestEvent> eventMessage = new GenericEventMessage<>(EVENT_TYPE, eventPayload, metaData);
        TaggedEventMessage<EventMessage<TestEvent>> taggedEventMessage =
                new GenericTaggedEventMessage<>(eventMessage, Set.of(Tag.of("key", "value")));
        // when...
        Map<String, String> result = testSubject.convertTaggedEventMessage(taggedEventMessage)
                                                .getEvent()
                                                .getMetadataMap();
        // then...
        assertEquals(8, result.size());
        assertEquals("Lorem Ipsum", result.get("String"));
        assertEquals("3.53d", result.get("Double"));
        assertEquals("3.53f", result.get("Float"));
        assertEquals("42L", result.get("Long"));
        assertEquals("42", result.get("Integer"));
        assertEquals("42", result.get("Short"));
        assertEquals("4", result.get("Byte"));
        assertEquals("false", result.get("Boolean"));
    }

    @Test
    void convertEventThrowsNullPointerExceptionForNullEvent() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.convertEvent(null));
    }

    @Test
    void convertEventWorksAsExpected() {
        // given...
        Event testEvent = Event.newBuilder()
                               .setIdentifier(EVENT_ID)
                               .setTimestamp(EVENT_TIMESTAMP)
                               .setName(EVENT_NAME)
                               .setVersion(EVENT_VERSION)
                               .setPayload(ByteString.copyFrom(eventPayloadByteArray))
                               .putAllMetadata(EVENT_METADATA)
                               .build();
        // when...
        EventMessage<byte[]> result = testSubject.convertEvent(testEvent);
        // then...
        assertEquals(EVENT_ID, result.getIdentifier());
        assertEquals(EVENT_TYPE, result.type());
        assertArrayEquals(eventPayloadByteArray, result.getPayload());
        assertEquals(EVENT_METADATA, result.getMetaData());
        assertEquals(EVENT_TIMESTAMP, result.getTimestamp().toEpochMilli());
    }

    private record TestEvent(String stringState, Integer intState, List<Boolean> booleanState) {

    }

    private record TestMetaDataValue(Integer integerState, String stringState) {

    }
}