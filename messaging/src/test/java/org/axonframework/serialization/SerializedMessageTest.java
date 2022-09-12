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
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SerializedMessage}.
 *
 * @author Allard Buijze
 */
class SerializedMessageTest {

    private final SerializedObject<String> serializedPayload =
            new SimpleSerializedObject<>("serializedPayload", String.class, "java.lang.Object", "1");
    private final SerializedObject<String> serializedMetaData =
            new SerializedMetaData<>("serializedMetaData", String.class);
    private final Object deserializedPayload = new Object();
    private final MetaData deserializedMetaData = MetaData.emptyInstance();
    private final Serializer serializer = mock(Serializer.class);
    private final String eventId = "eventId";

    @BeforeEach
    void setUp() {
        when(serializer.deserialize(serializedMetaData)).thenReturn(deserializedMetaData);
        when(serializer.deserialize(serializedPayload)).thenReturn(deserializedPayload);
        when(serializer.classForType(isA(SerializedType.class))).thenReturn(Object.class);
        when(serializer.getConverter()).thenReturn(new ChainingConverter());
    }

    @Test
    void constructorLeavesSerializedObjectsSerialized() {
        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        assertEquals(Object.class, testSubject.getPayloadType());
        assertFalse(testSubject.isPayloadDeserialized());
        assertEquals(Object.class, testSubject.getPayload().getClass());
        assertTrue(testSubject.isPayloadDeserialized());
        assertFalse(testSubject.isMetaDataDeserialized());
        assertSame(MetaData.emptyInstance(), testSubject.getMetaData());
        assertTrue(testSubject.isMetaDataDeserialized());
    }

    @Test
    void withMetaDataReplacesOriginalMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);

        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        Message<Object> resultOne = testSubject.withMetaData(MetaData.emptyInstance());
        Message<Object> resultTwo =
                testSubject.withMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, resultOne.getMetaData().size());
        assertEquals(1, resultTwo.getMetaData().size());
    }

    @Test
    void andMetaDataAppendsToOriginalMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);

        Message<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        Message<Object> resultOne = testSubject.andMetaData(MetaData.emptyInstance());
        assertEquals(1, resultOne.getMetaData().size());
        assertEquals("value", resultOne.getMetaData().get("key"));

        Message<Object> resultTwo =
                testSubject.andMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));
        assertEquals(1, resultTwo.getMetaData().size());
        assertEquals("otherValue", resultTwo.getMetaData().get("key"));
    }

    @Test
    void serializePayloadImmediatelyAfterConstructionReturnsOriginalPayload() {
        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        SerializedObject<byte[]> result = testSubject.serializePayload(serializer, byte[].class);
        assertArrayEquals("serializedPayload".getBytes(StandardCharsets.UTF_8), result.getData());
        // this call is allowed
        verify(serializer, atLeast(0)).classForType(isA(SerializedType.class));
        verify(serializer, atLeast(0)).getConverter();
        verifyNoMoreInteractions(serializer);
    }

    @Test
    void serializeMetaDataImmediatelyAfterConstructionReturnsOriginalMetaData() {
        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        SerializedObject<byte[]> result = testSubject.serializeMetaData(serializer, byte[].class);
        assertArrayEquals("serializedMetaData".getBytes(StandardCharsets.UTF_8), result.getData());
        // this call is allowed
        verify(serializer, atLeast(0)).classForType(isA(SerializedType.class));
        verify(serializer, atLeast(0)).getConverter();
        verifyNoMoreInteractions(serializer);
    }

    @Test
    void rethrowSerializationExceptionOnGetPayload() {
        SerializationException serializationException = new SerializationException("test message");
        when(serializer.deserialize(serializedMetaData)).thenThrow(serializationException);
        when(serializer.deserialize(serializedPayload)).thenThrow(serializationException);

        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        SerializationException result = assertThrows(SerializationException.class, testSubject::getPayload);
        assertEquals("Error while deserializing payload of message " + eventId, result.getMessage());
        assertSame(serializationException, result.getCause());
    }

    @Test
    void rethrowSerializationExceptionOnGetMetaData() {
        SerializationException serializationException = new SerializationException("test message");
        when(serializer.deserialize(serializedMetaData)).thenThrow(serializationException);
        when(serializer.deserialize(serializedPayload)).thenThrow(serializationException);

        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        SerializationException result = assertThrows(SerializationException.class, testSubject::getMetaData);
        assertEquals("Error while deserializing meta data of message " + eventId, result.getMessage());
        assertSame(serializationException, result.getCause());
    }
}
