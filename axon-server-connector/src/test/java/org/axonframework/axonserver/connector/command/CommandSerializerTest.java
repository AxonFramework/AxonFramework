/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializationException;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link CommandSerializer}.
 *
 * @author Marc Gathier
 */
class CommandSerializerTest {

    public static Stream<CommandSerializer> data() {
        AxonServerConfiguration configuration = new AxonServerConfiguration() {{
            this.setClientId("client");
            this.setComponentName("component");
        }};
        return Stream.of(JacksonSerializer.defaultSerializer(),
                         TestSerializer.xStreamSerializer())
                     .map(serializer -> new CommandSerializer(serializer, configuration));
    }

    @MethodSource("data")
    @ParameterizedTest
    void testSerializeRequest(CommandSerializer testSubject) {
        Map<String, ?> metadata = new HashMap<String, Object>() {{
            this.put("firstKey", "firstValue");
            this.put("secondKey", "secondValue");
        }};
        CommandMessage message = new GenericCommandMessage<>("payload", metadata);
        Command command = testSubject.serialize(message, "routingKey", 1);
        CommandMessage<?> deserialize = testSubject.deserialize(command);

        assertEquals(message.getIdentifier(), deserialize.getIdentifier());
        assertEquals(message.getCommandName(), deserialize.getCommandName());
        assertEquals(message.getMetaData(), deserialize.getMetaData());
        assertEquals(message.getPayloadType(), deserialize.getPayloadType());
        assertEquals(message.getPayload(), deserialize.getPayload());
    }

    @MethodSource("data")
    @ParameterizedTest
    void testSerializeResponse(CommandSerializer testSubject) {
        CommandResultMessage response = new GenericCommandResultMessage<>("response",
                                                                          MetaData.with("test", "testValue"));
        CommandResponse outbound = testSubject.serialize(response, "requestIdentifier");
        CommandResultMessage deserialize = testSubject.deserialize(outbound);

        assertEquals(response.getIdentifier(), deserialize.getIdentifier());
        assertEquals(response.getPayload(), deserialize.getPayload());
        assertEquals(response.getMetaData(), deserialize.getMetaData());
        assertFalse(response.isExceptional());
        assertFalse(response.optionalExceptionResult().isPresent());
    }

    @MethodSource("data")
    @ParameterizedTest
    void testSerializeExceptionalResponse(CommandSerializer testSubject) {
        RuntimeException exception = new RuntimeException("oops");
        CommandResultMessage response = new GenericCommandResultMessage<>(exception,
                                                                          MetaData.with("test", "testValue"));
        CommandResponse outbound = testSubject.serialize(response, "requestIdentifier");
        CommandResultMessage deserialize = testSubject.deserialize(outbound);

        assertEquals(response.getIdentifier(), deserialize.getIdentifier());
        assertEquals(response.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
    }

    @MethodSource("data")
    @ParameterizedTest
    void testSerializeExceptionalResponseWithDetails(CommandSerializer testSubject) {
        Exception exception = new CommandExecutionException("oops", null, "Details");
        CommandResultMessage<?> response = new GenericCommandResultMessage<>(exception,
                                                                             MetaData.with("test", "testValue"));
        CommandResponse outbound = testSubject.serialize(response, "requestIdentifier");
        assertEquals(response.getIdentifier(), outbound.getMessageIdentifier());
        CommandResultMessage<?> deserialize = testSubject.deserialize(outbound);

        assertEquals(response.getIdentifier(), deserialize.getIdentifier());
        assertEquals(response.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
        Throwable actual = deserialize.optionalExceptionResult().get();
        assertTrue(actual instanceof CommandExecutionException);
        assertEquals("Details", ((CommandExecutionException) actual).getDetails().orElse("None"));
    }

    @MethodSource("data")
    @ParameterizedTest
    void testSerializeNonTransientExceptionalResponse(CommandSerializer testSubject) {
        SerializationException nonTransientExceptionCause = new SerializationException(
                "Serialization non recoverable problem");
        Exception exception = new CommandExecutionException("oops", nonTransientExceptionCause, null);
        CommandResultMessage<?> response = new GenericCommandResultMessage<>(exception,
                                                                             MetaData.with("test", "testValue"));
        CommandResponse outbound = testSubject.serialize(response, "requestIdentifier");
        assertEquals(response.getIdentifier(), outbound.getMessageIdentifier());

        assertEquals(ErrorCode.COMMAND_EXECUTION_NON_TRANSIENT_ERROR.errorCode(), outbound.getErrorCode());
    }

    @MethodSource("data")
    @ParameterizedTest
    void testSerializeDeserializeNonTransientExceptionalResponseWithDetails(CommandSerializer testSubject) {
        SerializationException nonTransientExceptionCause = new SerializationException(
                "Serialization non recoverable problem");
        Exception exception = new CommandExecutionException("oops", nonTransientExceptionCause, "Details");
        CommandResultMessage<?> response = new GenericCommandResultMessage<>(exception,
                                                                             MetaData.with("test", "testValue"));
        CommandResponse outbound = testSubject.serialize(response, "requestIdentifier");
        assertEquals(response.getIdentifier(), outbound.getMessageIdentifier());
        CommandResultMessage<?> deserialize = testSubject.deserialize(outbound);

        assertEquals(response.getIdentifier(), deserialize.getIdentifier());
        assertEquals(response.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
        Throwable actual = deserialize.optionalExceptionResult().get();
        assertTrue(actual instanceof CommandExecutionException);
        assertTrue(actual.getCause() instanceof AxonServerNonTransientRemoteCommandHandlingException);
        assertTrue(actual.getCause().getMessage().contains("Serialization non recoverable problem"));
    }

    @MethodSource("data")
    @ParameterizedTest
    void testDeserializeResponseWithoutPayload(CommandSerializer testSubject) {
        CommandResponse response = CommandResponse.newBuilder()
                                                  .setRequestIdentifier("requestId")
                                                  .putAllMetaData(Collections.singletonMap("meta-key",
                                                                                           MetaDataValue.newBuilder()
                                                                                                        .setTextValue(
                                                                                                                "meta-value")
                                                                                                        .build()))
                                                  .build();

        CommandResultMessage<Object> actual = testSubject.deserialize(response);
        assertEquals(Void.class, actual.getPayloadType());
        assertNull(actual.getPayload());
        assertEquals("meta-value", actual.getMetaData().get("meta-key"));
    }
}
