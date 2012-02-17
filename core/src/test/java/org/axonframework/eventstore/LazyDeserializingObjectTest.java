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
import org.axonframework.serializer.SimpleSerializedObject;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class LazyDeserializingObjectTest {
    
    private final static int INDEX_ON_UPCASTED_OBJECT = 0;

    private Serializer mockSerializer;
    private LazyUpcastingObject mockSerializedObject;

    private SerializedType mockUpcastedType;
    private SerializedObject mockUpcastedObject;

    private String mockDeserializedObject = "I'm a mock";

    @Before
    public void setUp() throws Exception {
        mockSerializer = mock(Serializer.class);
        mockUpcastedType = mock(SerializedType.class);
        mockUpcastedObject = new SimpleSerializedObject(mockDeserializedObject, mockDeserializedObject.getClass(), "Mock", 0);

        mockSerializedObject = mock(LazyUpcastingObject.class);
        when(mockSerializedObject.getTypes()).thenReturn(Collections.singletonList(mockUpcastedType));
        when(mockSerializedObject.getObjects()).thenReturn(Collections.singletonList(mockUpcastedObject));

        when(mockSerializer.classForType(mockSerializedObject.getTypes().get(INDEX_ON_UPCASTED_OBJECT))).thenReturn(String.class);
        when(mockSerializer.deserialize(mockSerializedObject.getObjects().get(INDEX_ON_UPCASTED_OBJECT))).thenReturn(mockDeserializedObject);
    }

    @Test
    public void testLazilyDeserialized() {
        LazyDeserializingObject<Object> testSubject = new LazyDeserializingObject<Object>(mockSerializedObject,
                                                                                          mockSerializer, INDEX_ON_UPCASTED_OBJECT);
        verify(mockSerializer, never()).deserialize(any(SerializedObject.class));
        assertEquals(String.class, testSubject.getType());
        assertFalse(testSubject.isDeserialized());
        verify(mockSerializer, never()).deserialize(any(SerializedObject.class));
        assertSame(mockDeserializedObject, testSubject.getObject());
        assertTrue(testSubject.isDeserialized());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLazilyDeserialized_NullObject() {
        new LazyDeserializingObject<Object>(null, mockSerializer, INDEX_ON_UPCASTED_OBJECT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLazilyDeserialized_NullSerializer() {
        new LazyDeserializingObject<Object>(mockSerializedObject, null, INDEX_ON_UPCASTED_OBJECT);
    }

    @Test
    public void testWithProvidedDeserializedInstance() {
        LazyDeserializingObject<Object> testSubject = new LazyDeserializingObject<Object>(mockDeserializedObject);
        assertEquals(mockDeserializedObject.getClass(), testSubject.getType());
        assertSame(mockDeserializedObject, testSubject.getObject());
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
                                                                                          mockSerializer,
                                                                                          INDEX_ON_UPCASTED_OBJECT);
        verify(mockSerializer, never()).deserialize(any(SerializedObject.class));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(testSubject);
        oos.close();
        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        LazyDeserializingObject<Object> actual =
                (LazyDeserializingObject<Object>) new ObjectInputStream(in).readObject();
        assertEquals(mockDeserializedObject, actual.getObject());
        assertEquals(String.class, actual.getType());
        assertTrue(actual.isDeserialized());
    }
}
