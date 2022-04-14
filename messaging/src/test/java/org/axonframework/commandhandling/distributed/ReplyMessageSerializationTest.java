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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.RemoteHandlingException;
import org.axonframework.messaging.RemoteNonTransientHandlingException;
import org.axonframework.serialization.SerializationException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.Serializable;
import java.util.Collection;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests serialization/deserialization of {@link ReplyMessage}.
 *
 * @author Milan Savic
 */
class ReplyMessageSerializationTest {

    public static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void testSerializationDeserializationOfSuccessfulMessage(TestSerializer serializer) {
        String commandId = "commandId";
        CommandResultMessage<String> success = asCommandResultMessage("success");
        DummyReplyMessage message = new DummyReplyMessage(commandId, success, serializer.getSerializer());

        assertEquals(message, serializer.serializeDeserialize(message));
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void testSerializationDeserializationOfUnsuccessfulMessage(TestSerializer serializer) {
        String commandId = "commandId";
        CommandResultMessage<String> failure = asCommandResultMessage(new RuntimeException("oops"));
        DummyReplyMessage message = new DummyReplyMessage(commandId, failure, serializer.getSerializer());

        assertEquals(message, serializer.serializeDeserialize(message));
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void testDeserializingOfPersistentExceptions(TestSerializer serializer) {
        String commandId = "commandId";
        CommandResultMessage<String> failure = asCommandResultMessage(new SerializationException("oops"));
        DummyReplyMessage message = new DummyReplyMessage(commandId, failure, serializer.getSerializer());

        CommandResultMessage<?> commandResultMessage = message.getCommandResultMessage(serializer.getSerializer());
        assertTrue(commandResultMessage.exceptionResult() instanceof CommandExecutionException);
        assertTrue(commandResultMessage.exceptionResult().getCause() instanceof RemoteNonTransientHandlingException);
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void testDeserializingOfTransientExceptions(TestSerializer serializer) {
        String commandId = "commandId";
        CommandResultMessage<String> failure = asCommandResultMessage(new RuntimeException("oops"));
        DummyReplyMessage message = new DummyReplyMessage(commandId, failure, serializer.getSerializer());

        CommandResultMessage<?> commandResultMessage = message.getCommandResultMessage(serializer.getSerializer());
        assertTrue(commandResultMessage.exceptionResult() instanceof CommandExecutionException);
        assertTrue(commandResultMessage.exceptionResult().getCause() instanceof RemoteHandlingException);
    }

    private static class DummyReplyMessage extends ReplyMessage implements Serializable {

        private static final long serialVersionUID = -6583822511843818492L;

        @SuppressWarnings("unused") //used for deserialization with jackson
        public DummyReplyMessage() {
            super();
        }

        public DummyReplyMessage(String commandIdentifier, CommandResultMessage<?> commandResultMessage,
                                 Serializer serializer) {
            super(commandIdentifier, commandResultMessage, serializer);
        }
    }
}
