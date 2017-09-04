package org.axonframework.serialization.compression;

import org.axonframework.serialization.*;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class GzipSerializerWrapperTest {

    private GzipSerializerWrapper testSubject;
    private Serializer embeddedSerializerMock;

    @Before
    public void setUp() {
        embeddedSerializerMock = mock(Serializer.class);
        when(embeddedSerializerMock.canSerializeTo(byte[].class)).thenReturn(true);
        testSubject = new GzipSerializerWrapper(embeddedSerializerMock);
    }

    @Test
    public void testInit() {
        embeddedSerializerMock = mock(Serializer.class);
        when(embeddedSerializerMock.canSerializeTo(byte[].class)).thenReturn(true);
        testSubject = new GzipSerializerWrapper(embeddedSerializerMock);    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitNoByteArray() {
        embeddedSerializerMock = mock(Serializer.class);
        testSubject = new GzipSerializerWrapper(embeddedSerializerMock);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSerializeAndDeserialize() {
        when(embeddedSerializerMock.serialize("hello", byte[].class))
                .thenReturn(new SimpleSerializedObject<>("hello".getBytes(StandardCharsets.UTF_8), byte[].class,
                                                         new SimpleSerializedType("type", "0")));

        SerializedObject<byte[]> serializedObject = testSubject.serialize("hello", byte[].class);

        verify(embeddedSerializerMock).serialize("hello", byte[].class);

        when(embeddedSerializerMock.deserialize(any(SerializedObject.class)))
                .thenReturn("hello");

        Object deserializedObject = testSubject.deserialize(serializedObject);

        verify(embeddedSerializerMock).deserialize(any(SerializedObject.class));

        assertEquals("hello", deserializedObject);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDeserializeUncompressed() {
        SerializedObject<byte[]> serializedObject = new SimpleSerializedObject<>(
                "hello".getBytes(StandardCharsets.UTF_8),
                byte[].class,
                new SimpleSerializedType("type", "0"));

        when(embeddedSerializerMock.deserialize(any(SerializedObject.class)))
                .thenReturn("hello");

        Object deserializedObject = testSubject.deserialize(serializedObject);

        verify(embeddedSerializerMock).deserialize(any(SerializedObject.class));

        assertEquals("hello", deserializedObject);
    }

    @Test
    public void testCanSerializeTo() {
        boolean canSerializeTo = testSubject.canSerializeTo(byte[].class);

        //happens twice due to check in constructor
        verify(embeddedSerializerMock, times(2)).canSerializeTo(byte[].class);

        assertTrue(canSerializeTo);
    }

    @Test
    public void testClassForType() {
        Class expectedType = Object.class;
        when(embeddedSerializerMock.classForType(new SimpleSerializedType("type", "0"))).thenReturn(expectedType);

        Class type = testSubject.classForType(new SimpleSerializedType("type", "0"));

        verify(embeddedSerializerMock).classForType(new SimpleSerializedType("type", "0"));

        assertEquals(expectedType, type);
    }

    @Test
    public void testTypeForClass() {
        SerializedType expectedSerializedType = new SimpleSerializedType("type", "0");
        when(embeddedSerializerMock.typeForClass(Object.class)).thenReturn(expectedSerializedType);

        SerializedType serializedType = testSubject.typeForClass(Object.class);

        verify(embeddedSerializerMock).typeForClass(Object.class);

        assertEquals(expectedSerializedType, serializedType);
    }

    @Test
    public void testGetConverter() throws Exception {
        Converter expectedConverter = mock(Converter.class);
        when(embeddedSerializerMock.getConverter()).thenReturn(expectedConverter);

        Converter converter = testSubject.getConverter();

        verify(embeddedSerializerMock).getConverter();

        assertEquals(expectedConverter, converter);
    }

    @Test
    public void testGetEmbeddedSerializer() {
        Serializer embeddedSerializer = testSubject.getEmbeddedSerializer();
        assertEquals(embeddedSerializerMock, embeddedSerializer);
    }

    @Test
    public void testDoCompressAndDoDecompress() throws IOException {
        String expectedString = "<org.java.package.TypeWithSpecialChar>" +
                        "<value>'\"&;\n\\<>/\n\t</value>" +
                        "</org.java.package.TypeWithSpecialChar>";
        byte[] uncompressedData = expectedString.getBytes(StandardCharsets.UTF_8);
        byte[] compressedData = testSubject.doCompress(uncompressedData);

        assertFalse(Arrays.equals(uncompressedData, compressedData));
        assertTrue(compressedData.length < uncompressedData.length);

        byte[] decompressedData = testSubject.doDecompress(compressedData);
        String decompressedString = new String(decompressedData, StandardCharsets.UTF_8);

        assertTrue(Arrays.equals(uncompressedData, decompressedData));
        assertEquals(expectedString, decompressedString);
    }

    @Test(expected = NotCompressedException.class)
    public void testDoDecompressUncompressedData() throws IOException {
        String expectedString = "<org.java.package.TypeWithSpecialChar>" +
                "<value>'\"&;\n\\<>/\n\t</value>" +
                "</org.java.package.TypeWithSpecialChar>";
        byte[] uncompressedData = expectedString.getBytes(StandardCharsets.UTF_8);

        testSubject.doDecompress(uncompressedData);
    }
}