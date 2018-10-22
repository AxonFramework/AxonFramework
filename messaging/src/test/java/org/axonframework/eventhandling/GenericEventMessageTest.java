/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.junit.Test;

import java.io.*;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class GenericEventMessageTest {

    @Test
    public void testConstructor() {
        Object payload = new Object();
        GenericEventMessage<Object> message1 = new GenericEventMessage<>(payload);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericEventMessage<Object> message2 = new GenericEventMessage<>(payload, metaData);
        GenericEventMessage<Object> message3 = new GenericEventMessage<>(payload, metaDataMap);

        assertSame(MetaData.emptyInstance(), message1.getMetaData());
        assertEquals(Object.class, message1.getPayload().getClass());
        assertEquals(Object.class, message1.getPayloadType());

        assertEquals(metaData, message2.getMetaData());
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
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericEventMessage<Object> message = new GenericEventMessage<>(payload, metaData);
        GenericEventMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericEventMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    public void testAndMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericEventMessage<Object> message = new GenericEventMessage<>(payload, metaData);
        GenericEventMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericEventMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }

    @Test
    public void testTimestampInEventMessageIsAlwaysSerialized() throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        GenericEventMessage<String> testSubject =
                new GenericEventMessage<>(new GenericMessage<>("payload", Collections.singletonMap("key", "value")),
                                          Instant::now);
        oos.writeObject(testSubject);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object read = ois.readObject();

        assertEquals(GenericEventMessage.class, read.getClass());
        assertNotNull(((GenericEventMessage<?>) read).getTimestamp());
    }

    @Test
    public void testToString() {
        String actual = GenericEventMessage.asEventMessage("MyPayload").andMetaData(MetaData.with("key", "value").and("key2", 13)).toString();
        assertTrue("Wrong output: " + actual, actual.startsWith("GenericEventMessage{payload={MyPayload}, metadata={"));
        assertTrue("Wrong output: " + actual, actual.contains("'key'->'value'"));
        assertTrue("Wrong output: " + actual, actual.contains("'key2'->'13'"));
        assertTrue("Wrong output: " + actual, actual.contains("', timestamp='"));
        assertTrue("Wrong output: " + actual, actual.endsWith("}"));
    }
}
