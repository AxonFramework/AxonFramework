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
    private Object source;
    private Class<?> target;
    private List<ContentTypeConverter<?, ?>> candidates;
    private ContentTypeConverter<?, ?> stringToReaderConverter;
    private ContentTypeConverter<?, ?> stringToByteConverter;
    private ContentTypeConverter<?, ?> bytesToInputStreamConverter;
    private ContentTypeConverter<?, ?> numberToStringConverter;

    @Before
    public void setUp() {
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
        when(mock.convert(any())).thenReturn(representation);
        return mock;
    }

    @Test
    public void testComplexRoute() throws Exception {
        target = InputStream.class;
        source = 1L;
        testSubject = ChainedConverter.calculateChain(Number.class, target, candidates);
        assertNotNull(testSubject);
        verify(numberToStringConverter, never()).convert(any());
        verify(stringToReaderConverter, never()).convert(any());
        verify(bytesToInputStreamConverter, never()).convert(any());
        verify(stringToByteConverter, never()).convert(any());

        InputStream actual = convertSource();
        assertNotNull(actual);
        assertArrayEquals("hello".getBytes(), IOUtils.toByteArray(actual));

        verify(numberToStringConverter).convert(any());
        verify(stringToByteConverter).convert(any());
        verify(bytesToInputStreamConverter).convert(any());
        verify(stringToReaderConverter, never()).convert(any());
    }

    private <T> T convertSource() {
        return (T) testSubject.convert(source);
    }
    
    @Test
    public void testSimpleRoute() {
        target = String.class;
        source = 1L;
        testSubject = ChainedConverter.calculateChain(Number.class, target, candidates);
        assertNotNull(testSubject);
        verify(numberToStringConverter, never()).convert(any());
        verify(stringToReaderConverter, never()).convert(any());
        verify(bytesToInputStreamConverter, never()).convert(any());
        verify(stringToByteConverter, never()).convert(any());

        String actual = convertSource();
        assertEquals("hello", actual);

        verify(numberToStringConverter).convert(any());
        verify(stringToReaderConverter, never()).convert(any());
        verify(bytesToInputStreamConverter, never()).convert(any());
        verify(stringToByteConverter, never()).convert(any());
    }

    @Test(expected = CannotConvertBetweenTypesException.class)
    public void testInexistentRoute() {
        target = InputStream.class;
        source = new StringReader("hello");
        testSubject = ChainedConverter.calculateChain(Reader.class, target, candidates);
    }

    // Detects an issue where the ChainedConverter hangs as it evaluates a recursive route
    @Test(expected = CannotConvertBetweenTypesException.class)
    public void testAnotherInexistentRoute() {
        target = Number.class;
        source = "hello";
        assertFalse(ChainedConverter.canConvert(String.class, target, candidates));
        testSubject = ChainedConverter.calculateChain(String.class, target, candidates);
    }

    @Test(expected = CannotConvertBetweenTypesException.class)
    public void testAThirdInexistentRoute() {
        target = Documented.class;
        source = "hello".getBytes();
        testSubject = ChainedConverter.calculateChain(byte[].class, target, candidates);
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
