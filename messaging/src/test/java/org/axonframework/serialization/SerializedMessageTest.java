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

package org.axonframework.serialization;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class SerializedMessageTest {

    private SerializedObject<String> serializedPayload = new SimpleSerializedObject<>("serializedPayload",
                                                                                            String.class,
                                                                                            "java.lang.Object",
                                                                                            "1");
    private SerializedObject<String> serializedMetaData = new SerializedMetaData<>("serializedMetaData",
                                                                                         String.class);

    private Object deserializedPayload = new Object();
    private MetaData deserializedMetaData = MetaData.emptyInstance();
    private Serializer serializer = mock(Serializer.class);
    private String eventId = "eventId";

    @BeforeEach
    void setUp() {
        when(serializer.deserialize(serializedMetaData)).thenReturn(deserializedMetaData);
        when(serializer.deserialize(serializedPayload)).thenReturn(deserializedPayload);
        when(serializer.classForType(isA(SerializedType.class))).thenReturn(Object.class);
        when(serializer.getConverter()).thenReturn(new ChainingConverter());
    }

    @Test
    void constructor() {
        SerializedMessage<Object> message1 = new SerializedMessage<>(eventId,
                                                                           serializedPayload,
                                                                           serializedMetaData, serializer);

        assertSame(MetaData.emptyInstance(), message1.getMetaData());
        assertEquals(Object.class, message1.getPayloadType());
        assertFalse(message1.isPayloadDeserialized());
        assertEquals(Object.class, message1.getPayload().getClass());
        assertTrue(message1.isPayloadDeserialized());
    }

    @Test
    void withMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);
        SerializedMessage<Object> message = new SerializedMessage<>(eventId, serializedPayload,
                                                                          serializedMetaData, serializer);
        Message<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        Message<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    void andMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);
        Message<Object> message = new SerializedMessage<>(eventId, serializedPayload,
                                                                serializedMetaData, serializer);
        Message<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        Message<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }

    @Test
    void serializePayloadImmediately() {
        SerializedMessage<Object> message = new SerializedMessage<>(eventId, serializedPayload,
                                                                          serializedMetaData, serializer);

        SerializedObject<byte[]> actual = message.serializePayload(serializer, byte[].class);
        assertArrayEquals("serializedPayload".getBytes(Charset.forName("UTF-8")), actual.getData());
        // this call is allowed
        verify(serializer, atLeast(0)).classForType(isA(SerializedType.class));
        verify(serializer, atLeast(0)).getConverter();
        verifyNoMoreInteractions(serializer);
    }

    @Test
    void serializeMetaDataImmediately() {
        SerializedMessage<Object> message = new SerializedMessage<>(eventId, serializedPayload,
                                                                          serializedMetaData, serializer);

        SerializedObject<byte[]> actual = message.serializeMetaData(serializer, byte[].class);
        assertArrayEquals("serializedMetaData".getBytes(Charset.forName("UTF-8")), actual.getData());
        // this call is allowed
        verify(serializer, atLeast(0)).classForType(isA(SerializedType.class));
        verify(serializer, atLeast(0)).getConverter();
        verifyNoMoreInteractions(serializer);
    }
}
