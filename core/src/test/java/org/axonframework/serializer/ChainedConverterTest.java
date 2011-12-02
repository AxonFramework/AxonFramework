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
    private IntermediateRepresentation<?> source;
    private Class<?> target;
    private SimpleSerializedType mockType;
    private Set<ContentTypeConverter<?, ?>> candidates;
    private ContentTypeConverter<?, ?> stringToReaderConverter;
    private ContentTypeConverter<?, ?> stringToByteConverter;
    private ContentTypeConverter<?, ?> bytesToInputStreamConverter;
    private ContentTypeConverter<?, ?> numberToStringConverter;

    @Before
    public void setUp() throws Exception {
        mockType = new SimpleSerializedType("mock", 0);
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
        when(mock.convert(isA(IntermediateRepresentation.class))).thenReturn(new SimpleIntermediateRepresentation(
                mockType,
                targetType,
                representation));
        return mock;
    }

    @Test
    public void testComplexRoute() throws Exception {
        target = InputStream.class;
        source = new SimpleIntermediateRepresentation<Number>(mockType, Number.class, 1L);
        testSubject = ChainedConverter.calculateChain(source.getContentType(), target, candidates);
        assertNotNull(testSubject);
        verify(numberToStringConverter, never()).convert(any(IntermediateRepresentation.class));
        verify(stringToReaderConverter, never()).convert(any(IntermediateRepresentation.class));
        verify(bytesToInputStreamConverter, never()).convert(any(IntermediateRepresentation.class));
        verify(stringToByteConverter, never()).convert(any(IntermediateRepresentation.class));

        IntermediateRepresentation<InputStream> actual = testSubject.convert(source);
        assertEquals(target, actual.getContentType());
        InputStream actualContents = actual.getData();
        assertNotNull(actualContents);
        assertArrayEquals("hello".getBytes(), IOUtils.toByteArray(actualContents));

        verify(numberToStringConverter).convert(isA(IntermediateRepresentation.class));
        verify(stringToByteConverter).convert(isA(IntermediateRepresentation.class));
        verify(bytesToInputStreamConverter).convert(isA(IntermediateRepresentation.class));
        verify(stringToReaderConverter, never()).convert(isA(IntermediateRepresentation.class));
    }

    @Test
    public void testSimpleRoute() {
        target = String.class;
        source = new SimpleIntermediateRepresentation<Number>(mockType, Number.class, 1L);
        testSubject = ChainedConverter.calculateChain(source.getContentType(), target, candidates);
        assertNotNull(testSubject);
        verify(numberToStringConverter, never()).convert(any(IntermediateRepresentation.class));
        verify(stringToReaderConverter, never()).convert(any(IntermediateRepresentation.class));
        verify(bytesToInputStreamConverter, never()).convert(any(IntermediateRepresentation.class));
        verify(stringToByteConverter, never()).convert(any(IntermediateRepresentation.class));

        IntermediateRepresentation<String> actual = testSubject.convert(source);
        assertEquals("hello", actual.getData());

        verify(numberToStringConverter).convert(any(IntermediateRepresentation.class));
        verify(stringToReaderConverter, never()).convert(any(IntermediateRepresentation.class));
        verify(bytesToInputStreamConverter, never()).convert(any(IntermediateRepresentation.class));
        verify(stringToByteConverter, never()).convert(any(IntermediateRepresentation.class));
    }

    @Test(expected = CannotConvertBetweenTypesException.class)
    public void testInexistentRoute() throws Exception {
        target = InputStream.class;
        source = new SimpleIntermediateRepresentation<Reader>(mockType, Reader.class, new StringReader("hello"));
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
