/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.serialization;

import com.thoughtworks.xstream.XStream;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedDomainEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Testcase that checks whether every message can be serialized using the Jackson and Xstream serializer. That is, every
 * messages that can be dispatched using Axon Framework by default.
 */
class MessageSerializationTest {

    @ParameterizedTest
    @ArgumentsSource(ArgumentsProvider.class)
    void shouldSerialize_GenericEventMessage(Serializer serializer) {
        EventMessage<Object> message = GenericEventMessage
                .asEventMessage(new MessageSerializationPayload("one", "Two"))
                .andMetaData(createAdditionalMetadata());

        SerializedObject<String> serialize = serializer.serialize(message, String.class);
        EventMessage<Object> deserialize = serializer.deserialize(serialize);
        assertEquals(message, deserialize);
    }


    @ParameterizedTest
    @ArgumentsSource(ArgumentsProvider.class)
    void shouldSerialize_GenericDomainEventMessage(Serializer serializer) {
        EventMessage<Object> message = new GenericDomainEventMessage<>("Room",
                                                                       UUID.randomUUID().toString(),
                                                                       5L,
                                                                       new MessageSerializationPayload("one", "Two"),
                                                                       createAdditionalMetadata());

        SerializedObject<String> serialize = serializer.serialize(message, String.class);
        EventMessage<Object> deserialize = serializer.deserialize(serialize);
        assertEquals(message, deserialize);
    }


    @ParameterizedTest
    @ArgumentsSource(ArgumentsProvider.class)
    void shouldSerialize_GenericTrackedDomainEventMessage(Serializer serializer) {
        GapAwareTrackingToken gapAwareTrackingToken = new GapAwareTrackingToken(231972091,
                                                                                Arrays.asList(5L, 6L, 7L, 8L));
        EventMessage<Object> message = new GenericTrackedDomainEventMessage(
                gapAwareTrackingToken,
                "Room",
                UUID.randomUUID().toString(),
                231972091,
                GenericTrackedDomainEventMessage.asEventMessage(
                        new MessageSerializationPayload(
                                "one",
                                "Two")),
                () -> Instant.now())
                .andMetaData(createAdditionalMetadata());

        SerializedObject<String> serialize = serializer.serialize(message, String.class);
        EventMessage<Object> deserialize = serializer.deserialize(serialize);
        assertEquals(deserialize, message);
    }


    @ParameterizedTest
    @ArgumentsSource(ArgumentsProvider.class)
    void shouldSerialize_DeadlineMessage_Full(Serializer serializer) {
        DeadlineMessage<Object> message = new GenericDeadlineMessage<>(
                "myDeadline",
                "identifier",
                new MessageSerializationPayload("one", "Two"),
                createAdditionalMetadata(),
                Instant.now());

        SerializedObject<String> serialize = serializer.serialize(message, String.class);
        DeadlineMessage<Object> deserialize = serializer.deserialize(serialize);
        assertEquals(message, deserialize);
    }

    @ParameterizedTest
    @ArgumentsSource(ArgumentsProvider.class)
    void shouldSerialize_DeadlineMessage_Small(Serializer serializer) {
        DeadlineMessage<Object> message = new GenericDeadlineMessage<>("myDeadline");

        SerializedObject<String> serialize = serializer.serialize(message, String.class);
        EventMessage<Object> deserialize = serializer.deserialize(serialize);
        assertEquals(message, deserialize);
    }

    @ParameterizedTest
    @ArgumentsSource(ArgumentsProvider.class)
    void shouldSerialize_CommandMessage(Serializer serializer) {
        CommandMessage<Object> message = new GenericCommandMessage<>(new MessageSerializationPayload("one", "Two"),
                                                                     createAdditionalMetadata());

        SerializedObject<String> serialize = serializer.serialize(message, String.class);
        CommandMessage<Object> deserialize = serializer.deserialize(serialize);
        assertEquals(message, deserialize);
    }

    @ParameterizedTest
    @ArgumentsSource(ArgumentsProvider.class)
    void shouldSerialize_QueryMessage(Serializer serializer) {
        GenericQueryMessage message = new GenericQueryMessage<>(
                new MessageSerializationPayload("one", "Two"),
                ResponseTypes.multipleInstancesOf(
                        String.class));

        SerializedObject<String> serialize = serializer.serialize(message, String.class);
        QueryMessage deserialize = serializer.deserialize(serialize);
        assertEquals(message, deserialize);
    }

    private void assertEquals(DeadlineMessage<Object> deserialized, DeadlineMessage<Object> original) {
        Assertions.assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        Assertions.assertEquals(original.getIdentifier(), deserialized.getIdentifier());
        Assertions.assertEquals(original.getMetaData(), deserialized.getMetaData());
        Assertions.assertEquals(original.getPayload(), deserialized.getPayload());
        Assertions.assertEquals(original.getPayloadType(), deserialized.getPayloadType());
        Assertions.assertEquals(original.getClass(), deserialized.getClass());
        Assertions.assertEquals(original.getDeadlineName(), deserialized.getDeadlineName());
    }

    private void assertEquals(EventMessage<Object> deserialized, EventMessage<Object> original) {
        Assertions.assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        Assertions.assertEquals(original.getIdentifier(), deserialized.getIdentifier());
        Assertions.assertEquals(original.getMetaData(), deserialized.getMetaData());
        Assertions.assertEquals(original.getPayload(), deserialized.getPayload());
        Assertions.assertEquals(original.getPayloadType(), deserialized.getPayloadType());
        Assertions.assertEquals(original.getClass(), deserialized.getClass());
        if (deserialized instanceof DomainEventMessage && original instanceof DomainEventMessage) {
            Assertions.assertEquals(((DomainEventMessage<Object>) original).getSequenceNumber(),
                                    ((DomainEventMessage<Object>) deserialized).getSequenceNumber());
            Assertions.assertEquals(((DomainEventMessage<Object>) original).getType(),
                                    ((DomainEventMessage<Object>) deserialized).getType());
        }
        if (deserialized instanceof TrackedEventMessage && original instanceof TrackedEventMessage) {
            Assertions.assertEquals(((TrackedEventMessage<Object>) original).trackingToken(),
                                    ((TrackedEventMessage<Object>) deserialized).trackingToken());
        }
    }

    private void assertEquals(CommandMessage<Object> deserialized, CommandMessage<Object> original) {
        Assertions.assertEquals(original.getIdentifier(), deserialized.getIdentifier());
        Assertions.assertEquals(original.getMetaData(), deserialized.getMetaData());
        Assertions.assertEquals(original.getPayload(), deserialized.getPayload());
        Assertions.assertEquals(original.getPayloadType(), deserialized.getPayloadType());
        Assertions.assertEquals(original.getClass(), deserialized.getClass());
    }

    private void assertEquals(QueryMessage deserialized, QueryMessage original) {
        Assertions.assertEquals(original.getIdentifier(), deserialized.getIdentifier());
        Assertions.assertEquals(original.getQueryName(), deserialized.getQueryName());
        Assertions.assertEquals(original.getMetaData(), deserialized.getMetaData());
        Assertions.assertEquals(original.getPayload(), deserialized.getPayload());
        Assertions.assertEquals(original.getPayloadType(), deserialized.getPayloadType());
        Assertions.assertEquals(original.getClass(), deserialized.getClass());
    }

    private static class ArgumentsProvider implements org.junit.jupiter.params.provider.ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) throws Exception {
            Serializer jackson = JacksonSerializer.defaultSerializer();
            Serializer xStream = XStreamSerializer.builder().xStream(new XStream()).build();

            return Stream.of(
                    Arguments.of(jackson),
                    Arguments.of(xStream)
            );
        }
    }

    private MetaData createAdditionalMetadata() {
        HashMap<String, String> map = new HashMap<>();
        map.put("Marco", "Polo");
        map.put("Steven", "Programmer");
        return MetaData.from(map);
    }
}
