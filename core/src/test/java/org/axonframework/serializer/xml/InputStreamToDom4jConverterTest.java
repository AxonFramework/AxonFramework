package org.axonframework.serializer.xml;

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.dom4j.Document;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class InputStreamToDom4jConverterTest {

    private InputStreamToDom4jConverter testSubject;
    private SimpleSerializedType type;

    @Before
    public void setUp() throws Exception {
        type = new SimpleSerializedType("bla", "0");
        testSubject = new InputStreamToDom4jConverter();
    }

    @Test
    public void testConvert() throws Exception {
        byte[] bytes = "<parent><child/></parent>".getBytes();
        InputStream inputStream = new ByteArrayInputStream(bytes);
        SerializedObject<Document> actual = testSubject
                .convert(new SimpleSerializedObject<InputStream>(inputStream, InputStream.class, type));

        assertEquals("parent", actual.getData().getRootElement().getName());
    }
}
