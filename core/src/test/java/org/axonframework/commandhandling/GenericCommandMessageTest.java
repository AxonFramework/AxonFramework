/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling;

import org.axonframework.messaging.MetaData;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class GenericCommandMessageTest {

    @Test
    public void testConstructor() {
        Object payload = new Object();
        CommandMessage<Object> message1 = asCommandMessage(payload);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message2 = new GenericCommandMessage<>(payload, metaData);
        GenericCommandMessage<Object> message3 = new GenericCommandMessage<>(payload, metaDataMap);

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

        assertFalse(message1.getIdentifier().equals(message2.getIdentifier()));
        assertFalse(message1.getIdentifier().equals(message3.getIdentifier()));
        assertFalse(message2.getIdentifier().equals(message3.getIdentifier()));
    }

    @Test
    public void testWithMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message = new GenericCommandMessage<>(payload, metaData);
        GenericCommandMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericCommandMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    public void testAndMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message = new GenericCommandMessage<>(payload, metaData);
        GenericCommandMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericCommandMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }

    @Test
    public void testToString() {
        String actual = GenericCommandMessage.asCommandMessage("MyPayload").andMetaData(MetaData.with("key", "value").and("key2", 13)).toString();
        assertTrue("Wrong output: " + actual, actual.startsWith("GenericCommandMessage{payload={MyPayload}, metadata={"));
        assertTrue("Wrong output: " + actual, actual.contains("'key'->'value'"));
        assertTrue("Wrong output: " + actual, actual.contains("'key2'->'13'"));
        assertTrue("Wrong output: " + actual, actual.endsWith("', commandName='java.lang.String'}"));
    }
}
