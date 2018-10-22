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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
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
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.upcasting.Upcaster;
import org.axonframework.utils.SecondStubEvent;
import org.axonframework.utils.StubDomainEvent;
import org.axonframework.utils.TestDomainEventEntry;
import org.axonframework.utils.ThirdStubEvent;
import org.junit.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static junit.framework.TestCase.*;
import static org.mockito.Mockito.*;

public class AbstractEventMultiUpcasterTest {

    private String expectedNewString;
    private Integer expectedNewInteger;
    private List<Boolean> expectedNewBooleans;

    private Serializer serializer;
    private Upcaster<IntermediateEventRepresentation> upcaster;

    @Before
    public void setUp() {
        expectedNewString = "newNameValue";
        expectedNewInteger = 42;
        expectedNewBooleans = new ArrayList<>();
        expectedNewBooleans.add(true);
        expectedNewBooleans.add(false);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));
        serializer = JacksonSerializer.builder().objectMapper(objectMapper).build();
        upcaster = new StubEventMultiUpcaster(expectedNewString, expectedNewInteger, expectedNewBooleans);
    }

    @Test
    public void testUpcasterIgnoresWrongEventType() {
        GenericDomainEventMessage<String> testEventMessage =
                new GenericDomainEventMessage<>("test", "aggregateId", 0, "someString");
        EventData<?> testEventData = new TestDomainEventEntry(testEventMessage, serializer);
        IntermediateEventRepresentation testRepresentation =
                spy(new InitialEventRepresentation(testEventData, serializer));

        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(testRepresentation))
                .collect(toList());

        assertEquals(1, result.size());
        IntermediateEventRepresentation resultRepresentation = result.get(0);
        assertSame(testRepresentation, resultRepresentation);
        verify(testRepresentation, never()).getData();
    }

    @Test
    public void testUpcasterIgnoresWrongEventRevision() {
        String expectedRevisionNumber = "1";

        GenericDomainEventMessage<StubDomainEvent> testEventMessage =
                new GenericDomainEventMessage<>("test", "aggregateId", 0, new StubDomainEvent("oldName"));
        EventData<?> testEventData = new TestDomainEventEntry(testEventMessage, serializer);
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(testRepresentation))
                .collect(toList());

        testRepresentation = spy(result.get(0));
        assertEquals(expectedRevisionNumber, testRepresentation.getType().getRevision()); //initial upcast was successful

        result = upcaster.upcast(Stream.of(testRepresentation))
                .collect(toList());

        assertFalse(result.isEmpty());
        IntermediateEventRepresentation resultRepresentation = result.get(0);
        assertSame(testRepresentation, resultRepresentation);
        verify(testRepresentation, never()).getData();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testUpcastingDomainEventData() {
        String testAggregateType = "test";
        String testAggregateId = "aggregateId";
        GlobalSequenceTrackingToken testTrackingToken = new GlobalSequenceTrackingToken(10);
        long testSequenceNumber = 100;
        SerializedObject<String> testPayload = serializer.serialize(new StubDomainEvent("oldName"), String.class);
        EventData<?> testEventData = new TrackedDomainEventData<>(
                testTrackingToken,
                new GenericDomainEventEntry<>(testAggregateType, testAggregateId, testSequenceNumber, "eventId", Instant.now(),
                                              testPayload.getType().getName(), testPayload.getType().getRevision(), testPayload,
                                              serializer.serialize(MetaData.emptyInstance(), String.class))
        );
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(testRepresentation))
                .collect(toList());

        assertFalse(result.isEmpty());

        IntermediateEventRepresentation firstEventResult = result.get(0);
        assertEquals(testAggregateType, firstEventResult.getAggregateType().get());
        assertEquals(testAggregateId, firstEventResult.getAggregateIdentifier().get());
        assertEquals(testTrackingToken, firstEventResult.getTrackingToken().get());
        assertEquals(Long.valueOf(testSequenceNumber), firstEventResult.getSequenceNumber().get());

        IntermediateEventRepresentation secondEventResult = result.get(1);
        assertEquals(testAggregateType, secondEventResult.getAggregateType().get());
        assertEquals(testAggregateId, secondEventResult.getAggregateIdentifier().get());
        assertEquals(testTrackingToken, secondEventResult.getTrackingToken().get());
        assertEquals(Long.valueOf(testSequenceNumber), secondEventResult.getSequenceNumber().get());

        IntermediateEventRepresentation thirdEventResult = result.get(2);
        assertEquals(testAggregateType, thirdEventResult.getAggregateType().get());
        assertEquals(testAggregateId, thirdEventResult.getAggregateIdentifier().get());
        assertEquals(testTrackingToken, thirdEventResult.getTrackingToken().get());
        assertEquals(Long.valueOf(testSequenceNumber), thirdEventResult.getSequenceNumber().get());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testUpcastsKnownType() {
        String expectedRevisionNumber = "1";
        String expectedSecondAndThirdRevisionNumber = null;

        MetaData testMetaData = MetaData.with("key", "value");
        GenericDomainEventMessage<StubDomainEvent> testEventMessage =
                new GenericDomainEventMessage<>("test", "aggregateId", 0, new StubDomainEvent("oldName"), testMetaData);
        EventData<?> testEventData = new TestDomainEventEntry(testEventMessage, serializer);
        InitialEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(testRepresentation))
                .collect(toList());

        assertFalse(result.isEmpty());

        IntermediateEventRepresentation firstResultRepresentation = result.get(0);
        assertEquals(expectedRevisionNumber, firstResultRepresentation.getType().getRevision());
        assertEquals(testEventData.getEventIdentifier(), firstResultRepresentation.getMessageIdentifier());
        assertEquals(testEventData.getTimestamp(), firstResultRepresentation.getTimestamp());
        assertEquals(testMetaData, firstResultRepresentation.getMetaData().getObject());
        StubDomainEvent firstUpcastedEvent = serializer.deserialize(firstResultRepresentation.getData());
        assertEquals(expectedNewString, firstUpcastedEvent.getName());

        IntermediateEventRepresentation secondResultRepresentation = result.get(1);
        assertEquals(expectedSecondAndThirdRevisionNumber, secondResultRepresentation.getType().getRevision());
        assertEquals(testEventData.getEventIdentifier(), secondResultRepresentation.getMessageIdentifier());
        assertEquals(testEventData.getTimestamp(), secondResultRepresentation.getTimestamp());
        assertEquals(testMetaData, secondResultRepresentation.getMetaData().getObject());
        SecondStubEvent secondUpcastedEvent = serializer.deserialize(secondResultRepresentation.getData());
        assertEquals(expectedNewString, secondUpcastedEvent.getName());
        assertEquals(expectedNewInteger, secondUpcastedEvent.getNumber());

        IntermediateEventRepresentation thirdResultRepresentation = result.get(2);
        assertEquals(expectedSecondAndThirdRevisionNumber, thirdResultRepresentation.getType().getRevision());
        assertEquals(testEventData.getEventIdentifier(), thirdResultRepresentation.getMessageIdentifier());
        assertEquals(testEventData.getTimestamp(), thirdResultRepresentation.getTimestamp());
        assertEquals(testMetaData, thirdResultRepresentation.getMetaData().getObject());
        ThirdStubEvent thirdUpcastedEvent = serializer.deserialize(thirdResultRepresentation.getData());
        assertEquals(expectedNewString, thirdUpcastedEvent.getName());
        assertEquals(expectedNewInteger, thirdUpcastedEvent.getNumber());
        assertEquals(expectedNewBooleans, thirdUpcastedEvent.getTruths());
    }

    private static class StubEventMultiUpcaster extends EventMultiUpcaster {

        private final SerializedType targetType = new SimpleSerializedType(StubDomainEvent.class.getName(), null);

        private final String newStringValue;
        private final Integer newIntegerValue;
        private final List<Boolean> newBooleanValues;

        private StubEventMultiUpcaster(String newStringValue, Integer newIntegerValue, List<Boolean> newBooleanValues) {
            this.newStringValue = newStringValue;
            this.newIntegerValue = newIntegerValue;
            this.newBooleanValues = newBooleanValues;
        }

        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return intermediateRepresentation.getType()
                    .equals(targetType);
        }

        @Override
        protected Stream<IntermediateEventRepresentation> doUpcast(IntermediateEventRepresentation ir) {
            return Stream.of(
                    ir.upcastPayload(new SimpleSerializedType(targetType.getName(), "1"),
                                     JsonNode.class,
                                     this::doUpcast),
                    ir.upcastPayload(new SimpleSerializedType(SecondStubEvent.class.getName(), null),
                                     JsonNode.class,
                                     this::doUpcastTwo),
                    ir.upcastPayload(new SimpleSerializedType(ThirdStubEvent.class.getName(), null),
                                     JsonNode.class,
                                     this::doUpcastThree)
            );
        }

        private JsonNode doUpcast(JsonNode eventJsonNode) {
            if (!eventJsonNode.isObject()) {
                return eventJsonNode;
            }
            ObjectNode eventObjectNode = (ObjectNode) eventJsonNode;

            eventObjectNode.set("name", new TextNode(newStringValue));

            return eventObjectNode;
        }

        private JsonNode doUpcastTwo(JsonNode eventJsonNode) {
            if (!eventJsonNode.isObject()) {
                return eventJsonNode;
            }
            ObjectNode eventObjectNode = (ObjectNode) eventJsonNode;

            eventObjectNode.set("name", new TextNode(newStringValue));
            eventObjectNode.set("number", new IntNode(newIntegerValue));

            return eventJsonNode;
        }

        private JsonNode doUpcastThree(JsonNode eventJsonNode) {
            if (!eventJsonNode.isObject()) {
                return eventJsonNode;
            }
            ObjectNode eventObjectNode = (ObjectNode) eventJsonNode;

            eventObjectNode.set("name", new TextNode(newStringValue));
            eventObjectNode.set("number", new IntNode(newIntegerValue));
            ArrayNode truthsArrayNode = eventObjectNode.withArray("truths");
            newBooleanValues.forEach(truthsArrayNode::add);

            return eventJsonNode;
        }

    }

}
