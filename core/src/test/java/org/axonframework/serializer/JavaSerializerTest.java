package org.axonframework.serializer;

import org.junit.*;

import java.io.Serializable;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class JavaSerializerTest {

    private JavaSerializer testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new JavaSerializer();
    }

    @Test
    public void testSerializeAndDeserialize() {
        SerializedObject<byte[]> serializedObject = testSubject.serialize(new MySerializableObject("hello"),
                                                                          byte[].class);
        assertEquals(MySerializableObject.class.getName(), serializedObject.getType().getName());
        assertEquals(null, serializedObject.getType().getRevision());

        Object actualResult = testSubject.deserialize(serializedObject);
        assertTrue(actualResult instanceof MySerializableObject);
        assertEquals("hello", ((MySerializableObject) actualResult).getSomeProperty());
    }

    @Test
    public void testClassForType() {
        Class actual = testSubject.classForType(new SimpleSerializedType(MySerializableObject.class.getName(), "0"));
        assertEquals(MySerializableObject.class, actual);
    }

    @Test
    public void testClassForType_UnknownClass() {
        assertNull(testSubject.classForType(new SimpleSerializedType("unknown", "0")));
    }

    private static class MySerializableObject implements Serializable {

        private static final long serialVersionUID = 2166108932776672373L;
        private String someProperty;

        public MySerializableObject(String someProperty) {
            this.someProperty = someProperty;
        }

        public String getSomeProperty() {
            return someProperty;
        }
    }
}
