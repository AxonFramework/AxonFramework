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

import org.dom4j.Document;
import org.junit.*;

import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class UpcasterChainTest {

    private SerializedObject object1;
    private SerializedObject object2;
    private SerializedObject object3;
    private IntermediateRepresentation intermediate1;
    private IntermediateRepresentation intermediate2;
    private IntermediateRepresentation intermediate3;

    @Before
    public void setUp() throws Exception {
        object1 = new SimpleSerializedObject("object1".getBytes(), "type1", 0);
        object2 = new SimpleSerializedObject("object1".getBytes(), "type2", 1);
        object3 = new SimpleSerializedObject("object1".getBytes(), "type3", 2);
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
        when(mockUpcaster12.upcast(object1.getType())).thenReturn(object2.getType());

        when(mockUpcaster23.canUpcast(object2.getType())).thenReturn(true);
        when(mockUpcaster23.upcast(object2.getType())).thenReturn(object3.getType());

        UpcasterChain chain = new UpcasterChain(null, Arrays.asList(mockUpcaster12, mockUpcasterFake, mockUpcaster23));

        SerializedType actual1 = chain.upcast(object1.getType());
        verify(mockUpcaster12).upcast(object1.getType());
        verify(mockUpcaster23).upcast(object2.getType());
        verify(mockUpcasterFake, never()).upcast(any(SerializedType.class));
        assertEquals(object3.getType(), actual1);

        SerializedType actual2 = chain.upcast(object2.getType());
        assertEquals(object3.getType(), actual2);
    }

    @Test
    public void testUpcastObject_NoTypeConversionRequired() {
        Upcaster mockUpcaster12 = new StubUpcaster(intermediate1.getType(), intermediate2, byte[].class);
        Upcaster mockUpcasterFake = mock(Upcaster.class, "Fake upcaster");
        Upcaster mockUpcaster23 = new StubUpcaster(intermediate2.getType(), intermediate3, byte[].class);

        UpcasterChain chain = new UpcasterChain(null, mockUpcaster12, mockUpcasterFake, mockUpcaster23);

        IntermediateRepresentation actual1 = chain.upcast(object1);
        assertEquals(object3.getType(), actual1.getType());

        IntermediateRepresentation actual2 = chain.upcast(object2);
        assertEquals(object3.getType(), actual2.getType());
    }

    @Test
    public void testUpcastObject_WithTypeConversion() {

        IntermediateRepresentation intermediate1_stream = new MockIntermediateRepresentation(object1,
                                                                                             InputStream.class);
        IntermediateRepresentation intermediate2_bytes = new MockIntermediateRepresentation(object2, byte[].class);
        IntermediateRepresentation intermediate2_stream = new MockIntermediateRepresentation(object2,
                                                                                             InputStream.class);
        Upcaster mockUpcaster12 = new StubUpcaster(intermediate1.getType(), intermediate2_stream, InputStream.class);

        ConverterFactory mockConverterFactory = mock(ConverterFactory.class);
        ContentTypeConverter mockByteToStreamConverter = mock(ContentTypeConverter.class);
        ContentTypeConverter mockStreamToByteConverter = mock(ContentTypeConverter.class);
        when(mockConverterFactory.getConverter(byte[].class, InputStream.class)).thenReturn(mockByteToStreamConverter);
        when(mockConverterFactory.getConverter(InputStream.class, byte[].class)).thenReturn(mockStreamToByteConverter);

        when(mockByteToStreamConverter.expectedSourceType()).thenReturn(byte[].class);
        when(mockStreamToByteConverter.targetType()).thenReturn(InputStream.class);
        when(mockByteToStreamConverter.convert(isA(IntermediateRepresentation.class)))
                .thenReturn(intermediate1_stream);
        when(mockStreamToByteConverter.convert(intermediate2_stream))
                .thenReturn(intermediate2_bytes);

        UpcasterChain chain = new UpcasterChain(mockConverterFactory, mockUpcaster12);

        IntermediateRepresentation actual1 = chain.upcast(object1);
        verify(mockConverterFactory).getConverter(byte[].class, InputStream.class);
        verify(mockConverterFactory, never()).getConverter(InputStream.class, byte[].class);
        verify(mockStreamToByteConverter, never()).convert(isA(IntermediateRepresentation.class));
        verify(mockByteToStreamConverter).convert(isA(IntermediateRepresentation.class));
        assertEquals(object2.getType(), actual1.getType());
        assertArrayEquals(object2.getData(), (byte[]) actual1.getData());
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

    private class MockIntermediateRepresentation implements IntermediateRepresentation {

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

        private IntermediateRepresentation upcastResult;
        private SerializedType expectedType;
        private Class<?> contentType;

        public StubUpcaster(SerializedType expectedType, IntermediateRepresentation upcastResult,
                            Class<?> contentType) {
            this.contentType = contentType;
            this.expectedType = expectedType;
            this.upcastResult = upcastResult;
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
        public IntermediateRepresentation upcast(IntermediateRepresentation intermediateRepresentation) {
            assertEquals(expectedType, intermediateRepresentation.getType());
            return upcastResult;
        }

        @Override
        public SerializedType upcast(SerializedType serializedType) {
            return new SimpleSerializedType(serializedType.getName(), serializedType.getRevision() + 1);
        }
    }
}
