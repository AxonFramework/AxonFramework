package org.axonframework.serializer.converters;

import org.axonframework.serializer.IntermediateRepresentation;
import org.axonframework.serializer.SimpleIntermediateRepresentation;
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
        type = new SimpleSerializedType("bla", 0);
        testSubject = new InputStreamToDom4jConverter();
    }

    @Test
    public void testConvert() throws Exception {
        byte[] bytes = "<parent><child/></parent>".getBytes();
        InputStream inputStream = new ByteArrayInputStream(bytes);
        IntermediateRepresentation<Document> actual = testSubject
                .convert(new SimpleIntermediateRepresentation<InputStream>(type, InputStream.class, inputStream));

        assertEquals("parent", actual.getData().getRootElement().getName());
    }
}
