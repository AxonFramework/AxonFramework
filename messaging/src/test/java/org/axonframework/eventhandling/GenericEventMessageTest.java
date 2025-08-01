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

package org.axonframework.eventhandling;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTestSuite;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericEventMessage}.
 *
 * @author Allard Buijze
 */
class GenericEventMessageTest extends MessageTestSuite {

    @Override
    protected <P, M extends Message<P>> M buildMessage(P payload) {
        //noinspection unchecked
        return (M) new GenericEventMessage<>(new MessageType(payload.getClass()), payload);
    }

    @Test
    void constructor() {
        Object payload = new Object();
        EventMessage<Object> message1 = new GenericEventMessage<>(new MessageType("event"), payload);
        Map<String, String> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        EventMessage<Object> message2 =
                new GenericEventMessage<>(new MessageType("event"), payload, metaData);
        EventMessage<Object> message3 =
                new GenericEventMessage<>(new MessageType("event"), payload, metaDataMap);

        assertSame(MetaData.emptyInstance(), message1.getMetaData());
        assertEquals(Object.class, message1.payload().getClass());
        assertEquals(Object.class, message1.getPayloadType());

        assertEquals(metaData, message2.getMetaData());
        assertEquals(Object.class, message2.payload().getClass());
        assertEquals(Object.class, message2.getPayloadType());

        assertNotSame(metaDataMap, message3.getMetaData());
        assertEquals(metaDataMap, message3.getMetaData());
        assertEquals(Object.class, message3.payload().getClass());
        assertEquals(Object.class, message3.getPayloadType());

        assertNotEquals(message1.identifier(), message2.identifier());
        assertNotEquals(message1.identifier(), message3.identifier());
        assertNotEquals(message2.identifier(), message3.identifier());
    }

    @Test
    void withMetaData() {
        Object payload = new Object();
        Map<String, String> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericEventMessage<Object> message =
                new GenericEventMessage<>(new MessageType("event"), payload, metaData);
        GenericEventMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericEventMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    void andMetaData() {
        Object payload = new Object();
        Map<String, String> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericEventMessage<Object> message =
                new GenericEventMessage<>(new MessageType("event"), payload, metaData);
        GenericEventMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericEventMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }

    @Test
    void toStringIsAsExpected() {
        String actual = EventTestUtils.asEventMessage("MyPayload")
                                      .andMetaData(MetaData.with("key", "value").and("key2", "13"))
                                      .toString();
        assertTrue(actual.startsWith(
                           "GenericEventMessage{type={java.lang.String#0.0.1}, payload={MyPayload}, metadata={"
                   ),
                   "Wrong output: " + actual);
        assertTrue(actual.contains("'key'->'value'"), "Wrong output: " + actual);
        assertTrue(actual.contains("'key2'->'13'"), "Wrong output: " + actual);
        assertTrue(actual.contains("', timestamp='"), "Wrong output: " + actual);
        assertTrue(actual.endsWith("}"), "Wrong output: " + actual);
    }
}
