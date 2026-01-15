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

package org.axonframework.conversion.upcasting.event;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.EventData;
import org.axonframework.messaging.eventhandling.GenericDomainEventEntry;
import org.axonframework.messaging.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.TrackedDomainEventData;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.conversion.SerializedObject;
import org.axonframework.conversion.SerializedType;
import org.axonframework.conversion.Serializer;
import org.axonframework.conversion.SimpleSerializedType;
import org.axonframework.conversion.json.JacksonSerializer;
import org.axonframework.conversion.upcasting.Upcaster;
import org.axonframework.common.util.StubDomainEvent;
import org.axonframework.util.TestDomainEventEntry;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SingleEventUpcaster}.
 *
 * @author Rene de Waele
 */
class SingleEventUpcasterTest {

    private final Serializer serializer = JacksonSerializer.defaultSerializer();

    @Test
    @Disabled("TODO #3597")
    void upcastsKnownType() {
        String newValue = "newNameValue";
        Metadata metadata = Metadata.with("key", "value");
        DomainEventMessage testEvent = new GenericDomainEventMessage(
                "test", "aggregateId", 0, new MessageType("event"),
                new StubDomainEvent("oldName"), metadata
        );
        EventData<?> eventData = new TestDomainEventEntry(testEvent, serializer);
        Upcaster<IntermediateEventRepresentation> upcaster = new StubEventUpcaster(newValue);
        List<IntermediateEventRepresentation> result =
                upcaster.upcast(Stream.of(new InitialEventRepresentation(eventData, serializer))).toList();
        assertFalse(result.isEmpty());
        IntermediateEventRepresentation firstEvent = result.getFirst();
        assertEquals("1", firstEvent.getType().getRevision());
        StubDomainEvent upcastedEvent = serializer.deserialize(firstEvent.getData());
        assertEquals(newValue, upcastedEvent.getName());
        assertEquals(eventData.getEventIdentifier(), firstEvent.getMessageIdentifier());
        assertEquals(eventData.getTimestamp(), firstEvent.getTimestamp());
        assertEquals(metadata, firstEvent.getMetadata().getObject());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void upcastingDomainEventData() {
        String aggregateType = "test";
        String aggregateId = "aggregateId";
        GlobalSequenceTrackingToken trackingToken = new GlobalSequenceTrackingToken(10);
        long sequenceNumber = 100;
        Object payload = new StubDomainEvent("oldName");
        SerializedObject<String> serializedPayload = serializer.serialize(payload, String.class);
        EventData<?> eventData = new TrackedDomainEventData<>(
                trackingToken,
                new GenericDomainEventEntry<>(aggregateType, aggregateId, sequenceNumber,
                                              "eventId", Instant.now(),
                                              serializedPayload.getType().getName(),
                                              serializedPayload.getType().getRevision(),
                                              serializedPayload,
                                              serializer.serialize(Metadata.emptyInstance(), String.class))
        );
        Upcaster<IntermediateEventRepresentation> upcaster = new StubEventUpcaster("whatever");
        IntermediateEventRepresentation input = new InitialEventRepresentation(eventData, serializer);
        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(input)).toList();
        assertFalse(result.isEmpty());
        IntermediateEventRepresentation firstEvent = result.getFirst();
        assertEquals(aggregateType, firstEvent.getAggregateType().get());
        assertEquals(aggregateId, firstEvent.getAggregateIdentifier().get());
        assertEquals(trackingToken, firstEvent.getTrackingToken().get());
        assertEquals(Long.valueOf(sequenceNumber), firstEvent.getSequenceNumber().get());
    }

    @Test
    void ignoresUnknownType() {
        DomainEventMessage testEvent = new GenericDomainEventMessage(
                "test", "aggregateId", 0, new MessageType("event"), "someString"
        );
        EventData<?> eventData = new TestDomainEventEntry(
                testEvent, serializer
        );
        Upcaster<IntermediateEventRepresentation> upcaster = new StubEventUpcaster("whatever");
        IntermediateEventRepresentation input = spy(new InitialEventRepresentation(eventData, serializer));
        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(input)).toList();
        assertEquals(1, result.size());
        IntermediateEventRepresentation output = result.getFirst();
        assertSame(input, output);
        verify(input, never()).getData();
    }

    @Test
    @Disabled("TODO #3597")
    void ignoresWrongVersion() {
        DomainEventMessage testEvent = new GenericDomainEventMessage(
                "test", "aggregateId", 0, new MessageType("event"), new StubDomainEvent("oldName")
        );
        EventData<?> eventData = new TestDomainEventEntry(testEvent, serializer);
        Upcaster<IntermediateEventRepresentation> upcaster = new StubEventUpcaster("whatever");
        IntermediateEventRepresentation input = new InitialEventRepresentation(eventData, serializer);
        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(input)).collect(toList());
        input = spy(result.getFirst());
        assertEquals("1", input.getType().getRevision()); //initial upcast was successful
        result = upcaster.upcast(Stream.of(input)).toList();
        assertFalse(result.isEmpty());
        IntermediateEventRepresentation output = result.getFirst();
        assertSame(input, output);
        verify(input, never()).getData();
    }

    private static class StubEventUpcaster extends SingleEventUpcaster {

        private final SerializedType targetType = new SimpleSerializedType(StubDomainEvent.class.getName(), null);
        private final Class<ObjectNode> expectedType = ObjectNode.class;
        private final String newNameValue;

        private StubEventUpcaster(String newNameValue) {
            this.newNameValue = newNameValue;
        }

        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return intermediateRepresentation.getType().equals(targetType);
        }

        @Override
        protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation ir) {
            return ir.upcastPayload(new SimpleSerializedType(targetType.getName(), "1"), expectedType, eventPayload -> {
                eventPayload.put("name", newNameValue);
                return eventPayload;
            });
        }
    }
}
