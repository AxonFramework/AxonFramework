package org.axonframework.serializer.converters;

import org.axonframework.serializer.IntermediateRepresentation;
import org.axonframework.serializer.SimpleIntermediateRepresentation;
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
        type = new SimpleSerializedType("bla", 0);
    }

    @Test
    public void testConvert() {
        byte[] bytes = "Hello, world!".getBytes();
        InputStream inputStream = new ByteArrayInputStream(bytes);
        IntermediateRepresentation<byte[]> actual = testSubject
                .convert(new SimpleIntermediateRepresentation<InputStream>(type, InputStream.class, inputStream));

        assertEquals(type, actual.getType());
        assertArrayEquals(bytes, actual.getData());
    }
}
