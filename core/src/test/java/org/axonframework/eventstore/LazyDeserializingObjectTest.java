/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventstore;

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.Serializer;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class LazyDeserializingObjectTest {

    private Serializer mockSerializer;
    private SerializedObject mockSerializedObject;
    private SerializedType mockSerializedType;
    private List<Object> mockDeserializedObjects = Arrays.asList((Object)"I am a mock");

    @Before
    public void setUp() throws Exception {
        mockSerializer = mock(Serializer.class);
        mockSerializedObject = mock(SerializedObject.class);
        List<Class> mockDeserializedType = new ArrayList<Class>();
        mockDeserializedType.add(String.class);
        when(mockSerializer.classForType(mockSerializedType)).thenReturn(mockDeserializedType);
        when(mockSerializedObject.getType()).thenReturn(mockSerializedType);
        when(mockSerializer.deserialize(mockSerializedObject)).thenReturn(mockDeserializedObjects);
    }

    @Test
    public void testLazilyDeserialized() {
        LazyDeserializingObject<Object> testSubject = new LazyDeserializingObject<Object>(mockSerializedObject,
                                                                                          mockSerializer);
        verify(mockSerializer, never()).deserialize(any(SerializedObject.class));
        assertEquals(String.class, testSubject.getType().get(0));
        assertFalse(testSubject.isDeserialized());
        verify(mockSerializer, never()).deserialize(any(SerializedObject.class));
        assertSame(mockDeserializedObjects, testSubject.getObject());
        assertTrue(testSubject.isDeserialized());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLazilyDeserialized_NullObject() {
        new LazyDeserializingObject<Object>(null, mockSerializer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLazilyDeserialized_NullSerializer() {
        new LazyDeserializingObject<Object>(mockSerializedObject, null);
    }

    @Test
    public void testWithProvidedDeserializedInstance() {
        LazyDeserializingObject<Object> testSubject = new LazyDeserializingObject<Object>(mockDeserializedObjects);
        assertEquals(String.class, testSubject.getType().get(0));
        assertSame(mockDeserializedObjects, testSubject.getObject());
        assertTrue(testSubject.isDeserialized());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithProvidedDeserializedNullInstance() {
        new LazyDeserializingObject<Object>(null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSerializedProperly() throws IOException, ClassNotFoundException {
        LazyDeserializingObject<Object> testSubject = new LazyDeserializingObject<Object>(mockSerializedObject,
                                                                                          mockSerializer);
        verify(mockSerializer, never()).deserialize(any(SerializedObject.class));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(testSubject);
        oos.close();
        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        LazyDeserializingObject<Object> actual = (LazyDeserializingObject<Object>) new ObjectInputStream(in)
                .readObject();
        assertEquals(mockDeserializedObjects, actual.getObject());
        assertEquals(String.class, actual.getType().get(0));
        assertTrue(actual.isDeserialized());
    }
}
