/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.serializer;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.MetaData;

import org.junit.*;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SerializedEventMessageTest {

    private SerializedObject<String> serializedPayload = new SimpleSerializedObject<>("serialized",
                                                                                            String.class,
                                                                                            "java.lang.Object",
                                                                                            "1");
    private SerializedObject<String> serializedMetaData = new SerializedMetaData<>("serialized",
                                                                                         String.class);

    private Object deserializedPayload = new Object();
    private MetaData deserializedMetaData = MetaData.emptyInstance();
    private Serializer serializer = mock(Serializer.class);
    private String eventId = "eventId";
    private Instant timestamp = Instant.now();

    @Before
    public void setUp() {
        when(serializer.deserialize(serializedMetaData)).thenReturn(deserializedMetaData);
        when(serializer.deserialize(serializedPayload)).thenReturn(deserializedPayload);
        when(serializer.classForType(isA(SerializedType.class))).thenReturn(Object.class);
    }

    @Test
    public void testConstructor() {
        SerializedEventMessage<Object> message1 = new SerializedEventMessage<>(eventId, timestamp,
                                                                                     serializedPayload,
                                                                                     serializedMetaData, serializer);

        assertSame(MetaData.emptyInstance(), message1.getMetaData());
        assertEquals(Object.class, message1.getPayloadType());
        assertFalse(message1.isPayloadDeserialized());
        assertEquals(Object.class, message1.getPayload().getClass());
        assertTrue(message1.isPayloadDeserialized());
    }

    @Test
    public void testWithMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);
        SerializedEventMessage<Object> message = new SerializedEventMessage<>(eventId, timestamp,
                                                                                    serializedPayload,
                                                                                    serializedMetaData, serializer);
        EventMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        EventMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    public void testAndMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);
        EventMessage<Object> message = new SerializedEventMessage<>(eventId, timestamp, serializedPayload,
                                                                          serializedMetaData, serializer);
        EventMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        EventMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }
}
