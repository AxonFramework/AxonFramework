/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.axoniq.axonserver.grpc.MetaDataValue;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.conversion.Serializer;
import org.axonframework.conversion.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test whether the {@link GrpcMetadataConverter} can cope with several forms of the {@link MetaDataValue}. Born out of
 * the scenario that gRPC is of pulling a serialized object of type {@code empty} over the wire, which any used
 * {@link Serializer} would be incapable of handling correctly.
 *
 * @author Steven van Beelen
 */
@Deprecated(forRemoval = true, since = "5.0.0")
class GrpcMetadataConverterTest {

    private final Serializer serializer = spy(JacksonSerializer.defaultSerializer());
    private final GrpcMetadataConverter testSubject = new GrpcMetadataConverter(serializer);

    @Test
    void convertStringToMetadDataValue() {
        String testValue = "some-text";

        MetaDataValue result = testSubject.convertToMetadataValue(testValue);

        assertEquals(testValue, result.getTextValue());
    }

    @Test
    void convertDoubleToMetaDataValue() {
        double testValue = 10d;

        MetaDataValue result = testSubject.convertToMetadataValue(testValue);

        assertEquals(testValue, result.getDoubleValue(), 0);
    }

    @Test
    void convertFloatToMetaDataValue() {
        float testValue = 10f;

        MetaDataValue result = testSubject.convertToMetadataValue(testValue);

        assertEquals(testValue, result.getDoubleValue(), 0);
    }

    @Test
    void convertIntegerToMetaDataValue() {
        int testValue = 10;

        MetaDataValue result = testSubject.convertToMetadataValue(testValue);

        assertEquals(testValue, result.getNumberValue());
    }

    @Test
    void convertLongToMetaDataValue() {
        long testValue = 10L;

        MetaDataValue result = testSubject.convertToMetadataValue(testValue);

        assertEquals(testValue, result.getNumberValue());
    }

    @Test
    void convertBooleanToMetaDataValue() {
        MetaDataValue result = testSubject.convertToMetadataValue(true);

        assertTrue(result.getBooleanValue());
    }

    @Test
    void convertNullToMetaDataValue() {
        MetaDataValue result = testSubject.convertToMetadataValue(null);

        verify(serializer).serialize(null, byte[].class);

        assertEquals(MetaDataValue.DataCase.DATA_NOT_SET, result.getDataCase());
    }

    @Test
    void convertFromTextMetaDataValue() {
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
    void convertFromBytesMetaDataValue() {
        TestObject testObject = new TestObject("some-text");
        MetaDataValue testMetaData = testSubject.convertToMetadataValue(testObject);

        String result = testSubject.convertFromMetaDataValue(testMetaData);

        verify(serializer).deserialize(isA(GrpcSerializedObject.class));
        assertEquals(testObject.toString(), result);
    }

    @Test
    void convertFromBytesMetaDataValueOfTypeEmptyReturnsNull() {
        MetaDataValue data = testSubject.convertToMetadataValue(null);

        assertNull(testSubject.convertFromMetaDataValue(data));
    }

    @Test
    void convertFromDoubleMetaDataValue() {
        double expected = 10d;
        MetaDataValue testMetaData = MetaDataValue.newBuilder()
                                                  .setDoubleValue(expected)
                                                  .build();

        String result = testSubject.convertFromMetaDataValue(testMetaData);

        assertEquals(Double.toString(expected), result);
    }

    @Test
    void convertFromNumberMetaDataValue() {
        long expected = 10L;
        MetaDataValue testMetaData = MetaDataValue.newBuilder()
                                                  .setNumberValue(expected)
                                                  .build();

        String result = testSubject.convertFromMetaDataValue(testMetaData);

        assertEquals(Long.toString(expected), result);
    }

    @Test
    void convertFromBooleanMetaDataValue() {
        MetaDataValue testMetaData = MetaDataValue.newBuilder()
                                                  .setBooleanValue(true)
                                                  .build();

        String result = testSubject.convertFromMetaDataValue(testMetaData);

        assertEquals("true", result);
    }

    @Test
    void convertFromDataNotSetMetaDataValue() {
        MetaDataValue testMetaData = MetaDataValue.getDefaultInstance();

        assertNull(testSubject.convertFromMetaDataValue(testMetaData));
    }

    @SuppressWarnings("unused")
    private static class TestObject {

        private final String testField;

        private TestObject(@JsonProperty("testField") String testField) {
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
    @Event(version = "some-revision")
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