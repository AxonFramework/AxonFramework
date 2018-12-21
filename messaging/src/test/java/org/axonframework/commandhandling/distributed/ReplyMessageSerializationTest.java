/*
 * Copyright (c) 2010-2018. Axon Framework
 *
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.serialization.JavaSerializer;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.Assert.assertEquals;

/**
 * Tests serialization/deserialization of {@link ReplyMessage}.
 *
 * @author Milan Savic
 */
@RunWith(Parameterized.class)
public class ReplyMessageSerializationTest {

    private final Serializer serializer;

    @SuppressWarnings("unused") // Test name used to give sensible name to parameterized test
    public ReplyMessageSerializationTest(String testName, Serializer serializer) {
        this.serializer = serializer;
    }

    @Parameterized.Parameters(name = "Using {0}")
    public static Collection serializerImplementations() {
        return Arrays.asList(new Object[][]{
                {
                        "JavaSerializer",
                        JavaSerializer.builder().build()
                },
                {
                        "XStreamSerializer",
                        XStreamSerializer.builder().build()
                },
                {
                        "JacksonSerializer",
                        JacksonSerializer.builder().build()
                }
        });
    }

    @Test
    public void testSerializationDeserializationOfSuccessfulMessage() {
        String commandId = "commandId";
        CommandResultMessage<String> success = asCommandResultMessage("success");
        DummyReplyMessage message = new DummyReplyMessage(commandId, success, serializer);

        SerializedObject<byte[]> serialized = serializer.serialize(message, byte[].class);
        DummyReplyMessage deserialized = serializer.deserialize(serialized);

        assertEquals(message, deserialized);
    }

    @Test
    public void testSerializationDeserializationOfUnsuccessfulMessage() {
        String commandId = "commandId";
        CommandResultMessage<String> failure = asCommandResultMessage(new RuntimeException("oops"));
        DummyReplyMessage message = new DummyReplyMessage(commandId, failure, serializer);

        SerializedObject<byte[]> serialized = serializer.serialize(message, byte[].class);
        DummyReplyMessage deserialized = serializer.deserialize(serialized);

        assertEquals(message, deserialized);
    }

    private static class DummyReplyMessage extends ReplyMessage implements Serializable {

        private static final long serialVersionUID = -6583822511843818492L;

        public DummyReplyMessage() {
            super();
        }

        public DummyReplyMessage(String commandIdentifier, CommandResultMessage<?> commandResultMessage,
                                 Serializer serializer) {
            super(commandIdentifier, commandResultMessage, serializer);
        }
    }
}
