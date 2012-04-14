package org.axonframework.serializer;

import org.apache.commons.io.IOUtils;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class ChainedConverterTest {

    private ContentTypeConverter testSubject;
    private SerializedObject<?> source;
    private Class<?> target;
    private SimpleSerializedType mockType;
    private Set<ContentTypeConverter<?, ?>> candidates;
    private ContentTypeConverter<?, ?> stringToReaderConverter;
    private ContentTypeConverter<?, ?> stringToByteConverter;
    private ContentTypeConverter<?, ?> bytesToInputStreamConverter;
    private ContentTypeConverter<?, ?> numberToStringConverter;

    @Before
    public void setUp() throws Exception {
        mockType = new SimpleSerializedType("mock", "0");
        candidates = new HashSet<ContentTypeConverter<?, ?>>();
        numberToStringConverter = mockConverter(Number.class, String.class, "hello");
        stringToByteConverter = mockConverter(String.class, byte[].class, "hello".getBytes());
        stringToReaderConverter = mockConverter(String.class, Reader.class, new StringReader("hello"));
        bytesToInputStreamConverter = mockConverter(byte[].class, InputStream.class,
                                                    new ByteArrayInputStream("hello".getBytes()));

        candidates.add(stringToByteConverter);
        candidates.add(stringToReaderConverter);
        candidates.add(bytesToInputStreamConverter);
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
        source = new SimpleSerializedObject<Number>(1L, Number.class, mockType);
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
        source = new SimpleSerializedObject<Number>(1L, Number.class, mockType);
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
