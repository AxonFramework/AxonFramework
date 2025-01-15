/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericCommandMessage}.
 *
 * @author Allard Buijze
 */
class GenericCommandMessageTest {

    private static final QualifiedName TEST_NAME = new QualifiedName("test", "command", "0.0.1");

    @Test
    void constructor() {
        Object testPayload = new Object();
        Map<String, Object> testMetaDataMap = Collections.singletonMap("key", "value");
        MetaData testMetaData = MetaData.from(testMetaDataMap);
        CommandMessage<Object> message1 = new GenericCommandMessage<>(TEST_NAME, testPayload);
        CommandMessage<Object> message2 = new GenericCommandMessage<>(TEST_NAME, testPayload, testMetaDataMap);
        CommandMessage<Object> message3 = new GenericCommandMessage<>(TEST_NAME, testPayload, testMetaData);

        assertSame(MetaData.emptyInstance(), message1.getMetaData());
        assertEquals(TEST_NAME, message1.name());
        assertEquals(Object.class, message1.getPayload().getClass());

        assertEquals(TEST_NAME, message3.name());
        assertSame(testMetaData, message3.getMetaData());
        assertEquals(Object.class, message3.getPayload().getClass());

        assertEquals(TEST_NAME, message2.name());
        assertNotSame(testMetaDataMap, message2.getMetaData());
        assertEquals(testMetaDataMap, message2.getMetaData());
        assertEquals(Object.class, message2.getPayload().getClass());

        assertNotEquals(message1.getIdentifier(), message3.getIdentifier());
        assertNotEquals(message1.getIdentifier(), message2.getIdentifier());
        assertNotEquals(message3.getIdentifier(), message2.getIdentifier());
    }

    @Test
    void withMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message = new GenericCommandMessage<>(TEST_NAME, payload, metaData);
        GenericCommandMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericCommandMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue"))
        );

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    void andMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);

        CommandMessage<Object> command = new GenericCommandMessage<>(TEST_NAME, payload, metaData);
        CommandMessage<Object> command1 = command.andMetaData(MetaData.emptyInstance());
        CommandMessage<Object> command2 =
                command.andMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, command1.getMetaData().size());
        assertEquals("value", command1.getMetaData().get("key"));
        assertEquals(1, command2.getMetaData().size());
        assertEquals("otherValue", command2.getMetaData().get("key"));
    }

    @Test
    void toStringIsAsExpected() {
        String actual = new GenericCommandMessage<>(TEST_NAME, "MyPayload")
                .andMetaData(MetaData.with("key", "value").and("key2", 13))
                .toString();

        assertTrue(actual.startsWith("GenericCommandMessage{payload={MyPayload}, metadata={"),
                   "Wrong output: " + actual);
        assertTrue(actual.contains("'key'->'value'"), "Wrong output: " + actual);
        assertTrue(actual.contains("'key2'->'13'"), "Wrong output: " + actual);
        assertTrue(actual.endsWith("', commandName='java.lang.String'}"), "Wrong output: " + actual);
        assertEquals(173, actual.length(), "Wrong output: " + actual);
    }
}
