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

package io.axoniq.axonhub.client.command;

import io.axoniq.axonhub.Command;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.grpc.CommandProviderOutbound;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Author: marc
 */
public class CommandSerializerTest {

    private final Serializer jacksonSerializer = new JacksonSerializer();

    private final AxonHubConfiguration configuration = new AxonHubConfiguration() {{
        this.setClientName("client");
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
    public void testSerializeResponse(){
        Object response = "response";
        CommandProviderOutbound outbound = testSubject.serialize(response, "requestIdentifier");
        Object deserialize = testSubject.deserialize(outbound.getCommandResponse());
        assertEquals(response, deserialize);
    }

}