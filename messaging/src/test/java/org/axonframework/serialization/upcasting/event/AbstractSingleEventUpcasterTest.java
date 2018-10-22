/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.serialization.upcasting.event;

import org.axonframework.eventhandling.EventData;
import org.axonframework.eventhandling.GenericDomainEventEntry;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedDomainEventData;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.upcasting.Upcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.utils.StubDomainEvent;
import org.axonframework.utils.TestDomainEventEntry;
import org.dom4j.Document;
import org.junit.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.*;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class AbstractSingleEventUpcasterTest {

    @Test
    public void testUpcastsKnownType() {
        String newValue = "newNameValue";
        Serializer serializer = XStreamSerializer.builder().build();
        MetaData metaData = MetaData.with("key", "value");
        EventData<?> eventData = new TestDomainEventEntry(
                new GenericDomainEventMessage<>("test", "aggregateId", 0, new StubDomainEvent("oldName"), metaData),
                serializer);
        Upcaster<IntermediateEventRepresentation> upcaster = new StubEventUpcaster(newValue);
        List<IntermediateEventRepresentation> result =
                upcaster.upcast(Stream.of(new InitialEventRepresentation(eventData, serializer))).collect(toList());
        assertFalse(result.isEmpty());
        IntermediateEventRepresentation firstEvent = result.get(0);
        assertEquals("1", firstEvent.getType().getRevision());
        StubDomainEvent upcastedEvent = serializer.deserialize(firstEvent.getData());
        assertEquals(newValue, upcastedEvent.getName());
        assertEquals(eventData.getEventIdentifier(), firstEvent.getMessageIdentifier());
        assertEquals(eventData.getTimestamp(), firstEvent.getTimestamp());
        assertEquals(metaData, firstEvent.getMetaData().getObject());
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testUpcastingDomainEventData() {
        String aggregateType = "test";
        String aggregateId = "aggregateId";
        GlobalSequenceTrackingToken trackingToken = new GlobalSequenceTrackingToken(10);
        long sequenceNumber = 100;
        Serializer serializer = XStreamSerializer.builder().build();
        Object payload = new StubDomainEvent("oldName");
        SerializedObject<String> serializedPayload = serializer.serialize(payload, String.class);
        EventData<?> eventData = new TrackedDomainEventData<>(
                trackingToken,
                new GenericDomainEventEntry<>(aggregateType, aggregateId, sequenceNumber,
                                              "eventId", Instant.now(),
                                              serializedPayload.getType().getName(),
                                              serializedPayload.getType().getRevision(),
                                              serializedPayload,
                                              serializer.serialize(MetaData.emptyInstance(), String.class))
        );
        Upcaster<IntermediateEventRepresentation> upcaster = new StubEventUpcaster("whatever");
        IntermediateEventRepresentation input = new InitialEventRepresentation(eventData, serializer);
        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(input)).collect(toList());
        assertFalse(result.isEmpty());
        IntermediateEventRepresentation firstEvent = result.get(0);
        assertEquals(aggregateType, firstEvent.getAggregateType().get());
        assertEquals(aggregateId, firstEvent.getAggregateIdentifier().get());
        assertEquals(trackingToken, firstEvent.getTrackingToken().get());
        assertEquals(Long.valueOf(sequenceNumber), firstEvent.getSequenceNumber().get());
    }

    @Test
    public void testIgnoresUnknownType() {
        Serializer serializer = XStreamSerializer.builder().build();
        EventData<?> eventData = new TestDomainEventEntry(
                new GenericDomainEventMessage<>("test", "aggregateId", 0, "someString"), serializer
        );
        Upcaster<IntermediateEventRepresentation> upcaster = new StubEventUpcaster("whatever");
        IntermediateEventRepresentation input = spy(new InitialEventRepresentation(eventData, serializer));
        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(input)).collect(toList());
        assertEquals(1, result.size());
        IntermediateEventRepresentation output = result.get(0);
        assertSame(input, output);
        verify(input, never()).getData();
    }

    @Test
    public void testIgnoresWrongVersion() {
        Serializer serializer = XStreamSerializer.builder().build();
        EventData<?> eventData = new TestDomainEventEntry(
                new GenericDomainEventMessage<>("test", "aggregateId", 0, new StubDomainEvent("oldName")), serializer
        );
        Upcaster<IntermediateEventRepresentation> upcaster = new StubEventUpcaster("whatever");
        IntermediateEventRepresentation input = new InitialEventRepresentation(eventData, serializer);
        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(input)).collect(toList());
        input = spy(result.get(0));
        assertEquals("1", input.getType().getRevision()); //initial upcast was successful
        result = upcaster.upcast(Stream.of(input)).collect(toList());
        assertFalse(result.isEmpty());
        IntermediateEventRepresentation output = result.get(0);
        assertSame(input, output);
        verify(input, never()).getData();
    }

    private static class StubEventUpcaster extends SingleEventUpcaster {

        private final SerializedType targetType = new SimpleSerializedType(StubDomainEvent.class.getName(), null);
        private final Class<Document> expectedType = Document.class;
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
            return ir.upcastPayload(new SimpleSerializedType(targetType.getName(), "1"), expectedType, doc -> {
                doc.getRootElement().element("name").setText(newNameValue);
                return doc;
            });
        }
    }
}
