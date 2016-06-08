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

package org.axonframework.serialization;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.metadata.MetaData;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SerializationAwareTest {

    private GenericEventMessage<String> testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new GenericEventMessage<>("payload", Collections.singletonMap("key", "value"));
    }

    @Test
    public void testIsSerializedAsGenericEventMessage() throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(testSubject);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object read = ois.readObject();

        assertEquals(GenericEventMessage.class, read.getClass());
    }

    @Test
    public void testSerializePayloadTwice() {
        Serializer serializer = mock(Serializer.class);
        final SimpleSerializedObject<byte[]> serializedObject =
                new SimpleSerializedObject<>("payload".getBytes(), byte[].class, "String", "0");
        when(serializer.serialize("payload", byte[].class)).thenReturn(serializedObject);
        SerializedObject<byte[]> actual1 = testSubject.serializePayload(serializer, byte[].class);
        SerializedObject<byte[]> actual2 = testSubject.serializePayload(serializer, byte[].class);
        assertSame(actual1, actual2);
        verify(serializer, times(1)).serialize("payload", byte[].class);
        verifyNoMoreInteractions(serializer);
    }

    @Test
    public void testSerializePayloadTwice_DifferentRepresentations() {
        Serializer serializer = mock(Serializer.class);
        final SimpleSerializedObject<byte[]> serializedObject =
                new SimpleSerializedObject<>("payload".getBytes(), byte[].class, "String", "0");
        when(serializer.serialize("payload", byte[].class)).thenReturn(serializedObject);
        SerializedObject<byte[]> actual1 = testSubject.serializePayload(serializer, byte[].class);
        SerializedObject<String> actual2 = testSubject.serializePayload(serializer, String.class);

        assertNotSame(actual1, actual2);
        assertEquals(String.class, actual2.getContentType());
        verify(serializer, times(1)).serialize("payload", byte[].class);
        verifyNoMoreInteractions(serializer);
    }

    @Test
    public void testSerializeMetaDataTwice() {
        Serializer serializer = mock(Serializer.class);
        final SimpleSerializedObject<byte[]> serializedObject =
                new SimpleSerializedObject<>("payload".getBytes(), byte[].class, "String", "0");
        when(serializer.serialize(isA(MetaData.class), eq(byte[].class))).thenReturn(serializedObject);
        testSubject.serializeMetaData(serializer, byte[].class);
        testSubject.serializeMetaData(serializer, byte[].class);
        verify(serializer, times(1)).serialize(isA(MetaData.class), eq(byte[].class));
        verifyNoMoreInteractions(serializer);
    }

    @Test
    public void testSerializeMetaDataTwice_DifferentRepresentations() {
        Serializer serializer = mock(Serializer.class);
        final SimpleSerializedObject<byte[]> serializedObject =
                new SimpleSerializedObject<>("payload".getBytes(), byte[].class, "String", "0");
        when(serializer.serialize(isA(MetaData.class), eq(byte[].class))).thenReturn(serializedObject);
        SerializedObject<byte[]> actual1 = testSubject.serializeMetaData(serializer, byte[].class);
        SerializedObject<String> actual2 = testSubject.serializeMetaData(serializer, String.class);

        assertNotSame(actual1, actual2);
        assertEquals(String.class, actual2.getContentType());
        verify(serializer, times(1)).serialize(isA(MetaData.class), eq(byte[].class));
        verifyNoMoreInteractions(serializer);
    }
}
