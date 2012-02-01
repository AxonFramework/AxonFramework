package org.axonframework.serializer.converters;

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class Dom4JToByteArrayConverterTest {

    private Dom4JToByteArrayConverter testSubject;
    private SimpleSerializedType serializedType;

    @Before
    public void setUp() throws Exception {
        testSubject = new Dom4JToByteArrayConverter();
        serializedType = new SimpleSerializedType("custom", 0);
    }

    @Test
    public void testCanConvert() {
        assertEquals(Document.class, testSubject.expectedSourceType());
        assertEquals(byte[].class, testSubject.targetType());
    }

    @Test
    public void testConvert() throws Exception {
        DocumentFactory df = DocumentFactory.getInstance();
        Document doc = df.createDocument("UTF-8");
        doc.setRootElement(df.createElement("rootElement"));

        SimpleSerializedObject<Document> original = new SimpleSerializedObject<Document>(doc,
                                                                                         Document.class,
                                                                                         serializedType);
        SerializedObject<byte[]> actual = testSubject.convert(original);

        assertNotNull(actual);
        assertNotNull(actual.getData());
        String actualString = new String(actual.getData());

        assertTrue("Wrong output: " + actualString, actualString.contains("rootElement"));
    }
}
