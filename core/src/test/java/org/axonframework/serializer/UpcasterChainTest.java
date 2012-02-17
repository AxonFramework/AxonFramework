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

package org.axonframework.serializer;

import org.axonframework.upcasting.Upcaster;
import org.axonframework.upcasting.UpcasterChain;
import org.dom4j.Document;
import org.junit.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Frank Versnel
 */
public class UpcasterChainTest {

    private SerializedObject<byte[]> object1;
    private SerializedObject<byte[]> object2;
    private SerializedObject<byte[]> object3;
    private SerializedObject intermediate1;
    private SerializedObject intermediate2;
    private SerializedObject intermediate3;

    @Before
    public void setUp() throws Exception {
        object1 = new SimpleSerializedObject<byte[]>("object1".getBytes(), byte[].class, "type1", 0);
        object2 = new SimpleSerializedObject<byte[]>("object1".getBytes(), byte[].class, "type2", 1);
        object3 = new SimpleSerializedObject<byte[]>("object1".getBytes(), byte[].class, "type3", 2);
        intermediate1 = new MockIntermediateRepresentation(object1, byte[].class);
        intermediate2 = new MockIntermediateRepresentation(object2, byte[].class);
        intermediate3 = new MockIntermediateRepresentation(object3, byte[].class);
    }

    @Test
    public void testUpcastType() {
        Upcaster mockUpcaster12 = mock(Upcaster.class, "Type 1 to Type 2 upcaster");
        Upcaster mockUpcasterFake = mock(Upcaster.class, "Fake upcaster");
        Upcaster mockUpcaster23 = mock(Upcaster.class, "Type 2 to Type 3 upcaster");

        when(mockUpcaster12.canUpcast(any(SerializedType.class))).thenReturn(false);
        when(mockUpcaster23.canUpcast(any(SerializedType.class))).thenReturn(false);
        when(mockUpcaster12.canUpcast(object1.getType())).thenReturn(true);
        when(mockUpcaster12.upcast(object1.getType())).thenReturn(Arrays.asList(object2.getType()));

        when(mockUpcaster23.canUpcast(object2.getType())).thenReturn(true);
        when(mockUpcaster23.upcast(object2.getType())).thenReturn(Arrays.asList(object3.getType()));

        UpcasterChain chain = new UpcasterChain(null, Arrays.asList(mockUpcaster12, mockUpcasterFake, mockUpcaster23));

        List<SerializedType> actual1 = chain.upcast(object1.getType());
        verify(mockUpcaster12).upcast(object1.getType());
        verify(mockUpcaster23).upcast(object2.getType());
        verify(mockUpcasterFake, never()).upcast(any(SerializedType.class));
        assertEquals(object3.getType(), actual1.get(0));

        List<SerializedType> actual2 = chain.upcast(object2.getType());
        assertEquals(object3.getType(), actual2.get(0));
    }

    @Test
    public void testUpcastObject_NoTypeConversionRequired() {
        Upcaster mockUpcaster12 = new StubUpcaster(intermediate1.getType(), intermediate2, byte[].class);
        Upcaster mockUpcasterFake = mock(Upcaster.class, "Fake upcaster");
        Upcaster mockUpcaster23 = new StubUpcaster(intermediate2.getType(), intermediate3, byte[].class);

        UpcasterChain chain = new UpcasterChain(null, mockUpcaster12, mockUpcasterFake, mockUpcaster23);

        List<SerializedObject> actual1 = chain.upcast(object1);
        assertEquals(object3.getType(), actual1.get(0).getType());

        List<SerializedObject> actual2 = chain.upcast(object2);
        assertEquals(object3.getType(), actual2.get(0).getType());
    }

    @Test
    public void testUpcastObject_WithTypeConversion() {

        SerializedObject intermediate1_stream = new MockIntermediateRepresentation(object1,
                                                                                   InputStream.class);
        SerializedObject intermediate2_bytes = new MockIntermediateRepresentation(object2, byte[].class);
        SerializedObject intermediate2_stream = new MockIntermediateRepresentation(object2,
                                                                                   InputStream.class);
        Upcaster mockUpcaster12 = new StubUpcaster(intermediate1.getType(), intermediate2_stream, InputStream.class);

        ConverterFactory mockConverterFactory = mock(ConverterFactory.class);
        ContentTypeConverter mockByteToStreamConverter = mock(ContentTypeConverter.class);
        ContentTypeConverter mockStreamToByteConverter = mock(ContentTypeConverter.class);
        when(mockConverterFactory.getConverter(byte[].class, InputStream.class)).thenReturn(mockByteToStreamConverter);
        when(mockConverterFactory.getConverter(InputStream.class, byte[].class)).thenReturn(mockStreamToByteConverter);

        when(mockByteToStreamConverter.expectedSourceType()).thenReturn(byte[].class);
        when(mockStreamToByteConverter.targetType()).thenReturn(InputStream.class);
        when(mockByteToStreamConverter.convert(isA(SerializedObject.class)))
                .thenReturn(intermediate1_stream);
        when(mockStreamToByteConverter.convert(intermediate2_stream))
                .thenReturn(intermediate2_bytes);

        UpcasterChain chain = new UpcasterChain(mockConverterFactory, mockUpcaster12);

        List<SerializedObject> actual1 = chain.upcast(object1);
        verify(mockConverterFactory).getConverter(byte[].class, InputStream.class);
        verify(mockConverterFactory, never()).getConverter(InputStream.class, byte[].class);
        verify(mockStreamToByteConverter, never()).convert(isA(SerializedObject.class));
        verify(mockByteToStreamConverter).convert(isA(SerializedObject.class));
        assertEquals(object2.getType(), actual1.get(0).getType());
        assertArrayEquals(object2.getData(), (byte[]) actual1.get(0).getData());
    }

    @Test
    public void testUpcastObject_UnavailableTypeConversion() {
        Upcaster mockUpcaster12 = new StubUpcaster(intermediate1.getType(), intermediate2, Document.class);
        ConverterFactory mockConverterFactory = mock(ConverterFactory.class);
        CannotConvertBetweenTypesException mockException = new CannotConvertBetweenTypesException("Mock");
        when(mockConverterFactory.getConverter(isA(Class.class), isA(Class.class))).thenThrow(
                mockException);

        UpcasterChain chain = new UpcasterChain(mockConverterFactory, mockUpcaster12);
        try {
            chain.upcast(object1);
        } catch (CannotConvertBetweenTypesException e) {
            assertSame(mockException, e);
            verify(mockConverterFactory).getConverter(intermediate1.getContentType(), Document.class);
        }
    }

    @Test
    public void testUpcastObjectToMultipleObjects() {
        Upcaster mockUpcaster = new StubUpcaster(intermediate1.getType(), byte[].class, intermediate2, intermediate3);

        UpcasterChain chain = new UpcasterChain(null, mockUpcaster);
        List<SerializedObject> upcastedObjects = chain.upcast(object1);

        assertEquals(2, upcastedObjects.size());
        assertEquals(intermediate2, upcastedObjects.get(0));
        assertEquals(intermediate3, upcastedObjects.get(1));
    }

    private class MockIntermediateRepresentation implements SerializedObject {

        private final Class<?> contentType;
        private final SerializedObject serializedObject;

        public MockIntermediateRepresentation(SerializedObject serializedObject, Class<?> contentType) {
            this.serializedObject = serializedObject;
            this.contentType = contentType;
        }

        @Override
        public Class<?> getContentType() {
            return contentType;
        }

        @Override
        public SerializedType getType() {
            return serializedObject.getType();
        }

        @Override
        public Object getData() {
            return serializedObject.getData();
        }
    }

    private class StubUpcaster implements Upcaster {

        private List<SerializedObject> upcastResult;
        private SerializedType expectedType;
        private Class<?> contentType;

        public StubUpcaster(SerializedType expectedType, Class<?> contentType,
                            SerializedObject... upcastResult) {
            this.expectedType = expectedType;
            this.contentType = contentType;
            this.upcastResult = Arrays.asList(upcastResult);
        }

        public StubUpcaster(SerializedType expectedType, SerializedObject upcastResult,
                            Class<?> contentType) {
            this(expectedType, contentType, upcastResult);
        }

        @Override
        public boolean canUpcast(SerializedType serializedType) {
            return expectedType.equals(serializedType);
        }

        @Override
        public Class<?> expectedRepresentationType() {
            return contentType;
        }

        @Override
        public List<SerializedObject> upcast(SerializedObject intermediateRepresentation) {
            assertEquals(expectedType, intermediateRepresentation.getType());
            return upcastResult;
        }

        @Override
        public List<SerializedType> upcast(SerializedType serializedType) {
            return new ArrayList<SerializedType>(
                    Arrays.asList(new SimpleSerializedType(serializedType.getName(),
                                                           serializedType.getRevision() + 1)));
        }
    }
}
