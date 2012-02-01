package org.axonframework.serializer.converters;

import org.junit.*;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class StringToByteArrayConverterTest {

    @Test
    public void testConvert() throws UnsupportedEncodingException {
        StringToByteArrayConverter testSubject = new StringToByteArrayConverter();
        assertEquals(String.class, testSubject.expectedSourceType());
        assertEquals(byte[].class, testSubject.targetType());
        assertArrayEquals("hello".getBytes("UTF-8"), testSubject.convert("hello"));
    }
}
