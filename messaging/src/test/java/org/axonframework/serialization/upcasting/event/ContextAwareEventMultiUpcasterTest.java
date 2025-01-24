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

package org.axonframework.serialization.upcasting.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventData;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.upcasting.Upcaster;
import org.axonframework.utils.SecondStubEvent;
import org.axonframework.utils.StubDomainEvent;
import org.axonframework.utils.TestDomainEventEntry;
import org.axonframework.utils.ThirdStubEvent;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test class only asserts whether the context map is created, filled with data and if that data is used to upcast
 * an event. The other upcaster regularities are already asserted by the {@link EventMultiUpcasterTest} and can thus be
 * skipped.
 *
 * @author Steven van Beelen
 */
class ContextAwareEventMultiUpcasterTest {

    private Upcaster<IntermediateEventRepresentation> upcaster;
    private Serializer serializer;

    private String expectedNewString;
    private Integer expectedNewInteger;
    private List<Boolean> expectedNewBooleans;

    @BeforeEach
    void setUp() {
        expectedNewString = "newNameValue";
        expectedNewInteger = 42;
        expectedNewBooleans = new ArrayList<>();
        expectedNewBooleans.add(true);
        expectedNewBooleans.add(false);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));
        serializer = JacksonSerializer.builder().objectMapper(objectMapper).build();
        upcaster = new StubContextAwareEventMultiUpcaster(expectedNewString, expectedNewInteger, expectedNewBooleans);
    }

    @Test
    void upcastsAddsContextValueFromFirstEvent() {
        int expectedNumberOfEvents = 4;
        String expectedContextEventString = "oldName";
        Integer expectedContextEventNumber = 1;
        String expectedRevisionNumber = "1";
        String expectedNewString = this.expectedNewString + StubContextAwareEventMultiUpcaster.CONTEXT_FIELD_VALUE;

        MetaData testMetaData = MetaData.with("key", "value");

        DomainEventMessage<SecondStubEvent> firstTestEventMessage = new GenericDomainEventMessage<>(
                "test", "aggregateId", 0, new MessageType("event"),
                new SecondStubEvent(expectedContextEventString, expectedContextEventNumber), testMetaData
        );
        EventData<?> firstTestEventData = new TestDomainEventEntry(firstTestEventMessage, serializer);
        InitialEventRepresentation firstTestRepresentation =
                new InitialEventRepresentation(firstTestEventData, serializer);

        DomainEventMessage<StubDomainEvent> secondTestEventMessage = new GenericDomainEventMessage<>(
                "test", "aggregateId", 0, new MessageType("event"),
                new StubDomainEvent("oldName"), testMetaData
        );
        EventData<?> secondTestEventData = new TestDomainEventEntry(secondTestEventMessage, serializer);
        InitialEventRepresentation secondTestRepresentation =
                new InitialEventRepresentation(secondTestEventData, serializer);

        Stream<IntermediateEventRepresentation> testEventRepresentationStream =
                Stream.of(firstTestRepresentation, secondTestRepresentation);
        List<IntermediateEventRepresentation> result = upcaster.upcast(testEventRepresentationStream)
                                                               .toList();

        assertEquals(expectedNumberOfEvents, result.size());

        IntermediateEventRepresentation firstEventResult = result.getFirst();
        assertNull(firstEventResult.getType().getRevision());
        assertEquals(firstTestEventData.getEventIdentifier(), firstEventResult.getMessageIdentifier());
        assertEquals(firstTestEventData.getTimestamp(), firstEventResult.getTimestamp());
        assertEquals(testMetaData, firstEventResult.getMetaData().getObject());
        SecondStubEvent contextEvent = serializer.deserialize(firstEventResult.getData());
        assertEquals(expectedContextEventString, contextEvent.getName());
        assertEquals(expectedContextEventNumber, contextEvent.getNumber());

        IntermediateEventRepresentation secondEventResult = result.get(1);
        assertEquals(expectedRevisionNumber, secondEventResult.getType().getRevision());
        assertEquals(secondTestEventData.getEventIdentifier(), secondEventResult.getMessageIdentifier());
        assertEquals(secondTestEventData.getTimestamp(), secondEventResult.getTimestamp());
        assertEquals(testMetaData, secondEventResult.getMetaData().getObject());
        StubDomainEvent firstUpcastedEvent = serializer.deserialize(secondEventResult.getData());
        assertEquals(expectedNewString, firstUpcastedEvent.getName());

        IntermediateEventRepresentation thirdEventResult = result.get(2);
        assertNull(thirdEventResult.getType().getRevision());
        assertEquals(secondTestEventData.getEventIdentifier(), thirdEventResult.getMessageIdentifier());
        assertEquals(secondTestEventData.getTimestamp(), thirdEventResult.getTimestamp());
        assertEquals(testMetaData, thirdEventResult.getMetaData().getObject());
        SecondStubEvent secondUpcastedEvent = serializer.deserialize(thirdEventResult.getData());
        assertEquals(expectedNewString, secondUpcastedEvent.getName());
        assertEquals(expectedNewInteger, secondUpcastedEvent.getNumber());

        IntermediateEventRepresentation fourthEventResult = result.get(3);
        assertNull(fourthEventResult.getType().getRevision());
        assertEquals(secondTestEventData.getEventIdentifier(), fourthEventResult.getMessageIdentifier());
        assertEquals(secondTestEventData.getTimestamp(), fourthEventResult.getTimestamp());
        assertEquals(testMetaData, fourthEventResult.getMetaData().getObject());
        ThirdStubEvent thirdUpcastedEvent = serializer.deserialize(fourthEventResult.getData());
        assertEquals(expectedNewString, thirdUpcastedEvent.getName());
        assertEquals(expectedNewInteger, thirdUpcastedEvent.getNumber());
        assertEquals(expectedNewBooleans, thirdUpcastedEvent.getTruths());
    }

    private static class StubContextAwareEventMultiUpcaster
            extends ContextAwareEventMultiUpcaster<Map<Object, Object>> {

        private static final String CONTEXT_FIELD_KEY = "ContextField";
        static final String CONTEXT_FIELD_VALUE = "ContextAdded";

        private final SerializedType contextType = new SimpleSerializedType(SecondStubEvent.class.getName(), null);
        private final SerializedType targetType = new SimpleSerializedType(StubDomainEvent.class.getName(), null);

        private final String newStringValue;
        private final Integer newIntegerValue;
        private final List<Boolean> newBooleanValues;

        private StubContextAwareEventMultiUpcaster(String newStringValue,
                                                   Integer newIntegerValue,
                                                   List<Boolean> newBooleanValues) {
            this.newStringValue = newStringValue;
            this.newIntegerValue = newIntegerValue;
            this.newBooleanValues = newBooleanValues;
        }

        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation,
                                    Map<Object, Object> context) {
            return isType(intermediateRepresentation.getType(), targetType) ||
                    isType(intermediateRepresentation.getType(), contextType);
        }

        private boolean isType(SerializedType foundType, SerializedType expectedType) {
            return foundType.equals(expectedType);
        }

        @Override
        protected Stream<IntermediateEventRepresentation> doUpcast(IntermediateEventRepresentation ir,
                                                                   Map<Object, Object> context) {
            if (isContextEvent(ir)) {
                context.put(CONTEXT_FIELD_KEY, CONTEXT_FIELD_VALUE);
                return Stream.of(ir);
            }

            return Stream.of(
                    ir.upcastPayload(new SimpleSerializedType(targetType.getName(), "1"),
                                     JsonNode.class,
                                     jsonNode -> doUpcast(jsonNode, context)),
                    ir.upcastPayload(new SimpleSerializedType(SecondStubEvent.class.getName(), null),
                                     JsonNode.class,
                                     jsonNode -> doUpcastTwo(jsonNode, context)),
                    ir.upcastPayload(new SimpleSerializedType(ThirdStubEvent.class.getName(), null),
                                     JsonNode.class,
                                     jsonNode -> doUpcastThree(jsonNode, context))
            );
        }

        private boolean isContextEvent(IntermediateEventRepresentation intermediateRepresentation) {
            return isType(intermediateRepresentation.getType(), contextType);
        }

        @Override
        protected Map<Object, Object> buildContext() {
            return new HashMap<>();
        }

        private JsonNode doUpcast(JsonNode eventJsonNode, Map<Object, Object> context) {
            if (!eventJsonNode.isObject()) {
                return eventJsonNode;
            }
            ObjectNode eventObjectNode = (ObjectNode) eventJsonNode;

            eventObjectNode.set("name", new TextNode(newStringValue + context.get(CONTEXT_FIELD_KEY)));

            return eventObjectNode;
        }

        private JsonNode doUpcastTwo(JsonNode eventJsonNode, Map<Object, Object> context) {
            if (!eventJsonNode.isObject()) {
                return eventJsonNode;
            }
            ObjectNode eventObjectNode = (ObjectNode) eventJsonNode;

            eventObjectNode.set("name", new TextNode(newStringValue + context.get(CONTEXT_FIELD_KEY)));
            eventObjectNode.set("number", new IntNode(newIntegerValue));

            return eventJsonNode;
        }

        private JsonNode doUpcastThree(JsonNode eventJsonNode, Map<Object, Object> context) {
            if (!eventJsonNode.isObject()) {
                return eventJsonNode;
            }
            ObjectNode eventObjectNode = (ObjectNode) eventJsonNode;

            eventObjectNode.set("name", new TextNode(newStringValue + context.get(CONTEXT_FIELD_KEY)));
            eventObjectNode.set("number", new IntNode(newIntegerValue));
            ArrayNode truthsArrayNode = eventObjectNode.withArray("truths");
            newBooleanValues.forEach(truthsArrayNode::add);

            return eventJsonNode;
        }
    }
}
