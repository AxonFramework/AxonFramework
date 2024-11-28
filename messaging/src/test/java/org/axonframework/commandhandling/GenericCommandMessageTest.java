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

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QualifiedNameUtils;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericCommandMessage}.
 *
 * @author Allard Buijze
 */
class GenericCommandMessageTest {

    @Test
    void constructor() {
        Object payload = new Object();
        CommandMessage<Object> message1 = asCommandMessage(payload);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message2 =
                new GenericCommandMessage<>(QualifiedNameUtils.fromDottedName("test.command"), payload, metaData);
        GenericCommandMessage<Object> message3 =
                new GenericCommandMessage<>(QualifiedNameUtils.fromDottedName("test.command"), payload, metaDataMap);

        assertSame(MetaData.emptyInstance(), message1.getMetaData());
        assertEquals(Object.class, message1.getPayload().getClass());
        assertEquals(Object.class, message1.getPayloadType());

        assertSame(metaData, message2.getMetaData());
        assertEquals(Object.class, message2.getPayload().getClass());
        assertEquals(Object.class, message2.getPayloadType());

        assertNotSame(metaDataMap, message3.getMetaData());
        assertEquals(metaDataMap, message3.getMetaData());
        assertEquals(Object.class, message3.getPayload().getClass());
        assertEquals(Object.class, message3.getPayloadType());

        assertNotEquals(message1.getIdentifier(), message2.getIdentifier());
        assertNotEquals(message1.getIdentifier(), message3.getIdentifier());
        assertNotEquals(message2.getIdentifier(), message3.getIdentifier());
    }

    @Test
    void withMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message = new GenericCommandMessage<>(QualifiedNameUtils.fromDottedName("test.command"),
                                                                            payload,
                                                                            metaData);
        GenericCommandMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericCommandMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    void andMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message = new GenericCommandMessage<>(QualifiedNameUtils.fromDottedName("test.command"),
                                                                            payload,
                                                                            metaData);
        GenericCommandMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericCommandMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }

    @Test
    void testToString() {
        String actual = GenericCommandMessage.asCommandMessage("MyPayload").andMetaData(MetaData.with("key", "value")
                                                                                                .and("key2", 13))
                                             .toString();
        assertTrue(actual.startsWith("GenericCommandMessage{payload={MyPayload}, metadata={"),
                   "Wrong output: " + actual);
        assertTrue(actual.contains("'key'->'value'"), "Wrong output: " + actual);
        assertTrue(actual.contains("'key2'->'13'"), "Wrong output: " + actual);
        assertTrue(actual.endsWith("', commandName='java.lang.String'}"), "Wrong output: " + actual);
        assertEquals(173, actual.length(), "Wrong output: " + actual);
    }

    @Test
    void asCommandMessageWrapsPayload() {
        String expectedPayload = "some-payload";

        CommandMessage<Object> result = asCommandMessage(expectedPayload);

        assertEquals(expectedPayload, result.getPayload());
        assertTrue(result.getMetaData().isEmpty());
        assertNotNull(result.getIdentifier());
        assertEquals(String.class, result.getPayloadType());
        assertEquals(String.class.getName(), result.getCommandName());
    }

    @Test
    void asCommandMessageReturnsCommandMessageAsIs() {
        CommandMessage<String> expected =
                new GenericCommandMessage<>(QualifiedNameUtils.fromDottedName("test.command"), "some-payload", MetaData.with("key", "value"));

        CommandMessage<Object> result = asCommandMessage(expected);

        assertEquals(expected, result);
    }

    @Test
    void asCommandMessageRetrievesPayloadAndMetaDataFromMessageImplementations() {
        QualifiedName expectedType = QualifiedNameUtils.fromDottedName("test.command");
        String expectedPayload = "some-payload";
        MetaData expectedMetaData = MetaData.with("key", "value");

        String testIdentifier = "some-identifier";
        Message<String> testMessage =
                new GenericMessage<>(testIdentifier, expectedType, expectedPayload, expectedMetaData);

        CommandMessage<Object> result = asCommandMessage(testMessage);

        assertEquals(expectedType, result.type());
        assertEquals(expectedPayload, result.getPayload());
        assertEquals(expectedMetaData, result.getMetaData());
        assertNotEquals(testIdentifier, result.getIdentifier());
        assertEquals(String.class, result.getPayloadType());
        assertEquals(String.class.getName(), result.getCommandName());
    }
}
