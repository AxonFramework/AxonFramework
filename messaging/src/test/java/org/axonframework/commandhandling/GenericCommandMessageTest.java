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

package org.axonframework.commandhandling;

import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTestSuite;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericCommandMessage}.
 *
 * @author Allard Buijze
 */
class GenericCommandMessageTest extends MessageTestSuite {

    private static final MessageType TEST_TYPE = new MessageType("command");

    @Override
    protected <P, M extends Message<P>> M buildMessage(@Nullable P payload) {
        //noinspection unchecked
        return (M) new GenericCommandMessage<>(new MessageType(ObjectUtils.nullSafeTypeOf(payload)), payload);
    }

    @Test
    void constructor() {
        Object testPayload = new Object();
        Map<String, String> testMetaDataMap = Collections.singletonMap("key", "value");
        MetaData testMetaData = MetaData.from(testMetaDataMap);
        CommandMessage<Object> message1 = new GenericCommandMessage<>(TEST_TYPE, testPayload);
        CommandMessage<Object> message2 = new GenericCommandMessage<>(TEST_TYPE, testPayload, testMetaDataMap);
        CommandMessage<Object> message3 = new GenericCommandMessage<>(TEST_TYPE, testPayload, testMetaData);

        assertSame(MetaData.emptyInstance(), message1.metaData());
        assertEquals(TEST_TYPE, message1.type());
        assertEquals(Object.class, message1.payload().getClass());

        assertEquals(TEST_TYPE, message3.type());
        assertSame(testMetaData, message3.metaData());
        assertEquals(Object.class, message3.payload().getClass());

        assertEquals(TEST_TYPE, message2.type());
        assertNotSame(testMetaDataMap, message2.metaData());
        assertEquals(testMetaDataMap, message2.metaData());
        assertEquals(Object.class, message2.payload().getClass());

        assertNotEquals(message1.identifier(), message3.identifier());
        assertNotEquals(message1.identifier(), message2.identifier());
        assertNotEquals(message3.identifier(), message2.identifier());
    }

    @Test
    void withMetaData() {
        Object payload = new Object();
        Map<String, String> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message = new GenericCommandMessage<>(TEST_TYPE, payload, metaData);
        CommandMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        CommandMessage<Object> message2 =
                message.withMetaData(MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(0, message1.metaData().size());
        assertEquals(1, message2.metaData().size());
    }

    @Test
    void andMetaData() {
        Object payload = new Object();
        Map<String, String> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);

        CommandMessage<Object> command = new GenericCommandMessage<>(TEST_TYPE, payload, metaData);
        CommandMessage<Object> command1 = command.andMetaData(MetaData.emptyInstance());
        CommandMessage<Object> command2 =
                command.andMetaData(MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(1, command1.metaData().size());
        assertEquals("value", command1.metaData().get("key"));
        assertEquals(1, command2.metaData().size());
        assertEquals("otherValue", command2.metaData().get("key"));
    }

    @Test
    void toStringIsAsExpected() {
        String actual = new GenericCommandMessage<>(TEST_TYPE, "MyPayload")
                .andMetaData(MetaData.with("key", "value").and("key2", "13"))
                .toString();

        assertTrue(actual.startsWith("GenericCommandMessage{type={command#0.0.1}, payload={MyPayload}, metadata={"),
                   "Wrong output: " + actual);
        assertTrue(actual.contains("'key'->'value'"), "Wrong output: " + actual);
        assertTrue(actual.contains("'key2'->'13'"), "Wrong output: " + actual);
        assertEquals(163, actual.length(), "Wrong output: " + actual);
    }
}
