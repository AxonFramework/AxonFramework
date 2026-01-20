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

package org.axonframework.messaging.commandhandling;

import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageTestSuite;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericCommandMessage}.
 *
 * @author Allard Buijze
 */
class GenericCommandMessageTest extends MessageTestSuite<CommandMessage> {

    @Override
    protected CommandMessage buildDefaultMessage() {
        Message delegate =
                new GenericMessage(TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA);
        return new GenericCommandMessage(delegate);
    }

    @Override
    protected <P> CommandMessage buildMessage(@Nullable P payload) {
        return new GenericCommandMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)), payload);
    }

    @Test
    void toStringIsAsExpected() {
        String actual = new GenericCommandMessage(
                TEST_TYPE, "MyPayload", Metadata.with("key", "value").and("key2", "13"), "routingKey", 42
        ).toString();

        assertTrue(actual.startsWith("GenericCommandMessage{type={message#0.0.1}, payload={MyPayload}, metadata={"),
                   "Wrong output: " + actual);
        assertTrue(actual.contains("'key'->'value'"), "Wrong output: " + actual);
        assertTrue(actual.contains("'key2'->'13'"), "Wrong output: " + actual);
        assertTrue(actual.contains(", routingKey='routingKey'"), "Wrong output: " + actual);
        assertTrue(actual.contains(", priority='42'"), "Wrong output: " + actual);
        assertEquals(203, actual.length(), "Wrong output: " + actual);
    }
}
