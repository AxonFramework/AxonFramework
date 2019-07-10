package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import java.util.Objects;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test whether the {@link GrpcMetaDataConverter} can cope with several forms of the {@link MetaDataValue}. Born out of
 * the scenario that gRPC is of pulling a serialized object of type {@code empty} over the wire, which any used
 * {@link Serializer} would be incapable of handling correctly.
 *
 * @author Steven van Beelen
 */
public class GrpcMetaDataConverterTest {

    private final Serializer serializer = spy(XStreamSerializer.defaultSerializer());
    private final GrpcMetaDataConverter testSubject = new GrpcMetaDataConverter(serializer);

    @Test
    public void testConvertStringToMetaDataValue() {
        String testValue = "some-text";

        MetaDataValue result = testSubject.convertToMetaDataValue(testValue);

        assertEquals(testValue, result.getTextValue());
    }

    @Test
    public void testConvertDoubleToMetaDataValue() {
        double testValue = 10d;

        MetaDataValue result = testSubject.convertToMetaDataValue(testValue);

        assertEquals(testValue, result.getDoubleValue(), 0);
    }

    @Test
    public void testConvertFloatToMetaDataValue() {
        float testValue = 10f;

        MetaDataValue result = testSubject.convertToMetaDataValue(testValue);

        assertEquals(testValue, result.getDoubleValue(), 0);
    }

    @Test
    public void testConvertIntegerToMetaDataValue() {
        int testValue = 10;

        MetaDataValue result = testSubject.convertToMetaDataValue(testValue);

        assertEquals(testValue, result.getNumberValue());
    }

    @Test
    public void testConvertLongToMetaDataValue() {
        long testValue = 10L;

        MetaDataValue result = testSubject.convertToMetaDataValue(testValue);

        assertEquals(testValue, result.getNumberValue());
    }

    @Test
    public void testConvertBooleanToMetaDataValue() {
        MetaDataValue result = testSubject.convertToMetaDataValue(true);

        assertTrue(result.getBooleanValue());
    }

    @Test
    public void testConvertObjectToMetaDataValueUsesSerializer() {
        TestObject testObject = new TestObject("some-text");

        MetaDataValue result = testSubject.convertToMetaDataValue(testObject);

        verify(serializer).serialize(testObject, byte[].class);

        SerializedObject resultBytes = result.getBytesValue();
        assertEquals(TestObject.class.getName(), resultBytes.getType());
        assertNotNull(resultBytes.getData());
        assertEquals("", resultBytes.getRevision());
    }

    @Test
    public void testConvertObjectWithRevisionToMetaDataValue() {
        RevisionTestObject testObject = new RevisionTestObject("some-text");

        MetaDataValue result = testSubject.convertToMetaDataValue(testObject);

        verify(serializer).serialize(testObject, byte[].class);

        SerializedObject resultBytes = result.getBytesValue();
        assertEquals(RevisionTestObject.class.getName(), resultBytes.getType());
        assertNotNull(resultBytes.getData());
        assertEquals("some-revision", resultBytes.getRevision());
    }

    @Test
    public void testConvertNullToMetaDataValue() {
        MetaDataValue result = testSubject.convertToMetaDataValue(null);

        verify(serializer).serialize(null, byte[].class);

        assertEquals(MetaDataValue.DataCase.DATA_NOT_SET, result.getDataCase());
    }

    @Test
    public void testConvertFromTextMetaDataValue() {
        String expected = "some-text";
        MetaDataValue testMetaData = MetaDataValue.newBuilder()
                                                  .setTextValue(expected)
                                                  .build();

        Object resultObject = testSubject.convertFromMetaDataValue(testMetaData);

        assertTrue(resultObject instanceof String);
        String result = (String) resultObject;
        assertEquals(expected, result);
    }

    @Test
    public void testConvertFromBytesMetaDataValue() {
        TestObject testObject = new TestObject("some-text");
        MetaDataValue testMetaData = testSubject.convertToMetaDataValue(testObject);

        Object resultObject = testSubject.convertFromMetaDataValue(testMetaData);

        verify(serializer).deserialize(isA(GrpcSerializedObject.class));
        assertTrue(resultObject instanceof TestObject);
        assertEquals(testObject, resultObject);
    }

    @Test
    public void testConvertFromBytesMetaDataValueOfTypeEmptyReturnsNull() {
        MetaDataValue testMetaData = testSubject.convertToMetaDataValue(null);

        assertNull(testSubject.convertFromMetaDataValue(testMetaData));
    }

    @Test
    public void testConvertFromDoubleMetaDataValue() {
        @SuppressWarnings("WrapperTypeMayBePrimitive")
        Double expected = 10d;
        MetaDataValue testMetaData = MetaDataValue.newBuilder()
                                                  .setDoubleValue(expected)
                                                  .build();

        Object resultObject = testSubject.convertFromMetaDataValue(testMetaData);

        assertTrue(resultObject instanceof Double);
        Double result = (Double) resultObject;
        assertEquals(expected, result);
    }

    @Test
    public void testConvertFromNumberMetaDataValue() {
        @SuppressWarnings("WrapperTypeMayBePrimitive")
        Long expected = 10L;
        MetaDataValue testMetaData = MetaDataValue.newBuilder()
                                                  .setNumberValue(expected)
                                                  .build();

        Object resultObject = testSubject.convertFromMetaDataValue(testMetaData);

        assertTrue(resultObject instanceof Long);
        Long result = (Long) resultObject;
        assertEquals(expected, result);
    }

    @Test
    public void testConvertFromBooleanMetaDataValue() {
        MetaDataValue testMetaData = MetaDataValue.newBuilder()
                                                  .setBooleanValue(true)
                                                  .build();

        Object resultObject = testSubject.convertFromMetaDataValue(testMetaData);

        assertTrue(resultObject instanceof Boolean);
        Boolean result = (Boolean) resultObject;
        assertTrue(result);
    }

    @Test
    public void testConvertFromDataNotSetMetaDataValue() {
        MetaDataValue testMetaData = MetaDataValue.getDefaultInstance();

        assertNull(testSubject.convertFromMetaDataValue(testMetaData));
    }

    @SuppressWarnings("unused")
    private static class TestObject {

        private final String testField;

        private TestObject(String testField) {
            this.testField = testField;
        }

        public String getTestField() {
            return testField;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestObject that = (TestObject) o;
            return Objects.equals(testField, that.testField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(testField);
        }
    }

    @SuppressWarnings("unused")
    @Revision("some-revision")
    private static class RevisionTestObject {

        private final String testField;

        private RevisionTestObject(String testField) {
            this.testField = testField;
        }

        public String getTestField() {
            return testField;
        }
    }
}