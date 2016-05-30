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

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.annotation.Documented;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class ChainedConverterTest {

    private ContentTypeConverter testSubject;
    private SerializedObject<?> source;
    private Class<?> target;
    private SimpleSerializedType mockType;
    private List<ContentTypeConverter<?, ?>> candidates;
    private ContentTypeConverter<?, ?> stringToReaderConverter;
    private ContentTypeConverter<?, ?> stringToByteConverter;
    private ContentTypeConverter<?, ?> bytesToInputStreamConverter;
    private ContentTypeConverter<?, ?> numberToStringConverter;

    @Before
    public void setUp() throws Exception {
        mockType = new SimpleSerializedType("mock", "0");
        candidates = new ArrayList<>();
        numberToStringConverter = mockConverter(Number.class, String.class, "hello");
        stringToByteConverter = mockConverter(String.class, byte[].class, "hello".getBytes());
        stringToReaderConverter = mockConverter(String.class, Reader.class, new StringReader("hello"));
        bytesToInputStreamConverter = mockConverter(byte[].class, InputStream.class,
                                                    new ByteArrayInputStream("hello".getBytes()));
        ContentTypeConverter<?, ?> inputStreamToNumberConverter = mockConverter(InputStream.class,
                                                                                byte[].class,
                                                                                "hello".getBytes());

        candidates.add(stringToByteConverter);
        candidates.add(stringToReaderConverter);
        candidates.add(bytesToInputStreamConverter);
        candidates.add(inputStreamToNumberConverter);
        candidates.add(numberToStringConverter);
    }

    private ContentTypeConverter<?, ?> mockConverter(Class<?> expectedType, Class<?> targetType,
                                                     Object representation) {
        ContentTypeConverter mock = mock(ContentTypeConverter.class);
        when(mock.expectedSourceType()).thenReturn(expectedType);
        when(mock.targetType()).thenReturn(targetType);
        when(mock.convert(isA(SerializedObject.class))).thenReturn(new SimpleSerializedObject(representation,
                                                                                              targetType,
                                                                                              mockType));
        return mock;
    }

    @Test
    public void testComplexRoute() throws Exception {
        target = InputStream.class;
        source = new SimpleSerializedObject<>(1L, Number.class, mockType);
        testSubject = ChainedConverter.calculateChain(source.getContentType(), target, candidates);
        assertNotNull(testSubject);
        verify(numberToStringConverter, never()).convert(any(SerializedObject.class));
        verify(stringToReaderConverter, never()).convert(any(SerializedObject.class));
        verify(bytesToInputStreamConverter, never()).convert(any(SerializedObject.class));
        verify(stringToByteConverter, never()).convert(any(SerializedObject.class));

        SerializedObject<InputStream> actual = testSubject.convert(source);
        assertEquals(target, actual.getContentType());
        InputStream actualContents = actual.getData();
        assertNotNull(actualContents);
        assertArrayEquals("hello".getBytes(), IOUtils.toByteArray(actualContents));

        verify(numberToStringConverter).convert(isA(SerializedObject.class));
        verify(stringToByteConverter).convert(isA(SerializedObject.class));
        verify(bytesToInputStreamConverter).convert(isA(SerializedObject.class));
        verify(stringToReaderConverter, never()).convert(isA(SerializedObject.class));
    }

    @Test
    public void testSimpleRoute() {
        target = String.class;
        source = new SimpleSerializedObject<>(1L, Number.class, mockType);
        testSubject = ChainedConverter.calculateChain(source.getContentType(), target, candidates);
        assertNotNull(testSubject);
        verify(numberToStringConverter, never()).convert(any(SerializedObject.class));
        verify(stringToReaderConverter, never()).convert(any(SerializedObject.class));
        verify(bytesToInputStreamConverter, never()).convert(any(SerializedObject.class));
        verify(stringToByteConverter, never()).convert(any(SerializedObject.class));

        SerializedObject<String> actual = testSubject.convert(source);
        assertEquals("hello", actual.getData());

        verify(numberToStringConverter).convert(any(SerializedObject.class));
        verify(stringToReaderConverter, never()).convert(any(SerializedObject.class));
        verify(bytesToInputStreamConverter, never()).convert(any(SerializedObject.class));
        verify(stringToByteConverter, never()).convert(any(SerializedObject.class));
    }

    @Test(expected = CannotConvertBetweenTypesException.class)
    public void testInexistentRoute() throws Exception {
        target = InputStream.class;
        source = new SimpleSerializedObject<Reader>(new StringReader("hello"), Reader.class, mockType);
        testSubject = ChainedConverter.calculateChain(source.getContentType(), target, candidates);
    }

    // Detects an issue where the ChainedConverter hangs as it evaluates a recursive route
    @Test(expected = CannotConvertBetweenTypesException.class)
    public void testAnotherInexistentRoute() throws Exception {
        target = Number.class;
        source = new SimpleSerializedObject<>("hello", String.class, mockType);
        assertFalse(ChainedConverter.canConvert(String.class, target, candidates));
        testSubject = ChainedConverter.calculateChain(source.getContentType(), target, candidates);
    }

    @Test(expected = CannotConvertBetweenTypesException.class)
    public void testAThirdInexistentRoute() throws Exception {
        target = Documented.class;
        source = new SimpleSerializedObject<>("hello".getBytes(), byte[].class, mockType);
        testSubject = ChainedConverter.calculateChain(source.getContentType(), target, candidates);
    }

    @Test
    public void testDiscontinuousChainIsRejected() {
        try {
            testSubject = new ChainedConverter(Arrays.<ContentTypeConverter>asList(numberToStringConverter,
                                                                                   bytesToInputStreamConverter));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue("Wrong message: " + e.getMessage(), e.getMessage().contains("continuous chain"));
        }
    }
}
