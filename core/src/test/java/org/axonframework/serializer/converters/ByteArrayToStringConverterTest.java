package org.axonframework.serializer.converters;

import org.junit.*;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class ByteArrayToStringConverterTest {

    @Test
    public void testConvert() throws UnsupportedEncodingException {
        ByteArrayToStringConverter testSubject = new ByteArrayToStringConverter();
        assertEquals(String.class, testSubject.targetType());
        assertEquals(byte[].class, testSubject.expectedSourceType());
        assertEquals("hello", testSubject.convert("hello".getBytes("UTF-8")));
    }

}
