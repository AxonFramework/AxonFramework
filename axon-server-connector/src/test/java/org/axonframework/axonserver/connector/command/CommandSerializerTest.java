/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.command;

import io.axoniq.axonserver.grpc.command.Command;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import io.axoniq.axonserver.grpc.command.CommandProviderOutbound;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Author: marc
 */
public class CommandSerializerTest {

    private final Serializer jacksonSerializer = JacksonSerializer.builder().build();

    private final AxonServerConfiguration configuration = new AxonServerConfiguration() {{
        this.setClientId("client");
        this.setComponentName("component");
    }};

    private final CommandSerializer testSubject = new CommandSerializer(jacksonSerializer, configuration);

    @Test
    public void testSerializeRequest(){
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

    @Test
    public void testSerializeResponse() {
        CommandResultMessage response = new GenericCommandResultMessage<>("response",
                                                                          MetaData.with("test", "testValue"));
        CommandProviderOutbound outbound = testSubject.serialize(response, "requestIdentifier");
        CommandResultMessage deserialize = testSubject.deserialize(outbound.getCommandResponse());
        assertEquals(response.getPayload(), deserialize.getPayload());
        assertEquals(response.getMetaData(), deserialize.getMetaData());
        assertFalse(response.isExceptional());
        assertFalse(response.optionalExceptionResult().isPresent());
    }

    @Test
    public void testSerializeExceptionalResponse() {
        RuntimeException exception = new RuntimeException("oops");
        CommandResultMessage response = new GenericCommandResultMessage<>(exception,
                                                                          MetaData.with("test", "testValue"));
        CommandProviderOutbound outbound = testSubject.serialize(response, "requestIdentifier");
        CommandResultMessage deserialize = testSubject.deserialize(outbound.getCommandResponse());
        assertEquals(response.getMetaData(), deserialize.getMetaData());
        assertTrue(deserialize.isExceptional());
        assertTrue(deserialize.optionalExceptionResult().isPresent());
        assertEquals(exception.getMessage(), deserialize.exceptionResult().getMessage());
    }

}