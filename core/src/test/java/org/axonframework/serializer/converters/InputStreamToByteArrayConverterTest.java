package org.axonframework.serializer.converters;

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class InputStreamToByteArrayConverterTest {

    private InputStreamToByteArrayConverter testSubject;
    private SimpleSerializedType type;

    @Before
    public void setUp() throws Exception {
        testSubject = new InputStreamToByteArrayConverter();
        type = new SimpleSerializedType("bla", "0");
    }

    @Test
    public void testConvert() {
        byte[] bytes = "Hello, world!".getBytes();
        InputStream inputStream = new ByteArrayInputStream(bytes);
        SerializedObject<byte[]> actual = testSubject
                .convert(new SimpleSerializedObject<InputStream>(inputStream, InputStream.class, type));

        assertEquals(type, actual.getType());
        assertArrayEquals(bytes, actual.getData());
    }
}
