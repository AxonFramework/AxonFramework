/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.serialization.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.ChainingConverter;
import org.axonframework.serialization.ContentTypeConverter;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.UnknownSerializedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Allard Buijze
 */
class JacksonSerializerTest {

    private JacksonSerializer testSubject;
    private Instant time;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testSubject = JacksonSerializer.builder().objectMapper(objectMapper).build();
        time = Instant.now();
    }

    @Test
    void testCanSerializeToStringByteArrayAndInputStream() {
        assertTrue(testSubject.canSerializeTo(byte[].class));
        assertTrue(testSubject.canSerializeTo(String.class));
        assertTrue(testSubject.canSerializeTo(InputStream.class));
    }

    @Test
    void testSerializeAndDeserializeObject_StringFormat() {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<String> serialized = testSubject.serialize(toSerialize, String.class);

        SimpleSerializableType actual = testSubject.deserialize(serialized);
        assertEquals(toSerialize.getValue(), actual.getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.getNested().getValue());
    }

    @Test
    void testSerializeAndDeserializeArray() {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<String> serialized = testSubject.serialize(new SimpleSerializableType[]{toSerialize}, String.class);

        SimpleSerializableType[] actual = testSubject.deserialize(serialized);
        assertEquals(1, actual.length);
        assertEquals(toSerialize.getValue(), actual[0].getValue());
        assertEquals(toSerialize.getNested().getValue(), actual[0].getNested().getValue());
    }

    @Test
    void testSerializeAndDeserializeList() {
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, JsonTypeInfo.As.PROPERTY);

        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<String> serialized = testSubject.serialize(singletonList(toSerialize), String.class);

        List<SimpleSerializableType> actual = testSubject.deserialize(serialized);
        assertEquals(1, actual.size());
        assertEquals(toSerialize.getValue(), actual.get(0).getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.get(0).getNested().getValue());
    }

    @Test
    void testSerializeAndDeserializeObject_ByteArrayFormat() {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<byte[]> serialized = testSubject.serialize(toSerialize, byte[].class);

        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertEquals(toSerialize.getValue(), actual.getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.getNested().getValue());
    }

    @Test
    void testSerializeAndDeserializeObjectUnknownType() {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<byte[]> serialized = testSubject.serialize(toSerialize, byte[].class);

        Object actual = testSubject.deserialize(new SimpleSerializedObject<>(serialized.getData(),
                                                                             byte[].class,
                                                                             "someUnknownType",
                                                                             "42.1"));

        assertTrue(actual instanceof UnknownSerializedType);
        UnknownSerializedType actualUnknown = ((UnknownSerializedType) actual);

        assertTrue(actualUnknown.supportsFormat(JsonNode.class));
        JsonNode actualJson = actualUnknown.readData(JsonNode.class);

        assertEquals("first", actualJson.get("value").asText());
        assertEquals("nested", actualJson.path("nested").path("value").asText());
    }

    @Test
    void testSerializeAndDeserializeObject_JsonNodeFormat() {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<JsonNode> serialized = testSubject.serialize(toSerialize, JsonNode.class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertEquals(toSerialize.getValue(), actual.getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.getNested().getValue());
    }

    @Test
    void testCustomObjectMapperRevisionResolverAndConverter() {
        RevisionResolver revisionResolver = spy(new AnnotationRevisionResolver());
        ChainingConverter converter = spy(new ChainingConverter());
        ObjectMapper objectMapper = spy(new ObjectMapper());

        testSubject = JacksonSerializer.builder()
                                       .revisionResolver(revisionResolver)
                                       .converter(converter)
                                       .objectMapper(objectMapper)
                                       .build();

        SerializedObject<byte[]> serialized = testSubject.serialize(new SimpleSerializableType("test"),
                                                                    byte[].class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        verify(objectMapper).readerFor(SimpleSerializableType.class);
        verify(objectMapper).writer();
        verify(revisionResolver).revisionOf(SimpleSerializableType.class);
        verify(converter, times(2)).registerConverter(isA(ContentTypeConverter.class));
        assertSame(objectMapper, testSubject.getObjectMapper());
    }

    @Test
    void testCustomObjectMapperAndRevisionResolver() {
        ObjectMapper objectMapper = spy(new ObjectMapper());
        RevisionResolver revisionResolver = spy(new AnnotationRevisionResolver());

        testSubject = JacksonSerializer.builder()
                                       .revisionResolver(revisionResolver)
                                       .objectMapper(objectMapper)
                                       .build();

        SerializedObject<byte[]> serialized = testSubject.serialize(new SimpleSerializableType("test"),
                                                                    byte[].class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertTrue(testSubject.getConverter() instanceof ChainingConverter);
        verify(objectMapper).readerFor(SimpleSerializableType.class);
        verify(objectMapper).writer();
        verify(revisionResolver).revisionOf(SimpleSerializableType.class);
    }

    @Test
    void testCustomObjectMapper() {
        ObjectMapper objectMapper = spy(new ObjectMapper());

        testSubject = JacksonSerializer.builder()
                                       .objectMapper(objectMapper)
                                       .build();

        SerializedObject<byte[]> serialized = testSubject.serialize(new SimpleSerializableType("test"),
                                                                    byte[].class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertTrue(testSubject.getConverter() instanceof ChainingConverter);
        verify(objectMapper).readerFor(SimpleSerializableType.class);
        verify(objectMapper).writer();
    }

    @Test
    void testSerializeMetaData() {
        testSubject = JacksonSerializer.builder().build();

        SerializedObject<byte[]> serialized = testSubject.serialize(MetaData.from(singletonMap("test", "test")),
                                                                    byte[].class);
        MetaData actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertEquals("test", actual.get("test"));
        assertEquals(1, actual.size());
    }

    @Test
    void testSerializeMetaDataWithComplexObjects() {
        // typing must be enabled for this (which we expect end-users to do
        testSubject.getObjectMapper()
                   .enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "@type");

        MetaData metaData = MetaData.with("myKey", new ComplexObject("String1", "String2", 3));
        SerializedObject<byte[]> serialized = testSubject.serialize(metaData, byte[].class);
        MetaData actual = testSubject.deserialize(serialized);

        assertEquals(metaData, actual);
    }

    @Test
    void testDeserializeNullValue() {
        SerializedObject<byte[]> serializedNull = testSubject.serialize(null, byte[].class);
        SimpleSerializedObject<byte[]> serializedNullString = new SimpleSerializedObject<>(
                serializedNull.getData(), byte[].class, testSubject.typeForClass(String.class)
        );
        assertNull(testSubject.deserialize(serializedNull));
        assertNull(testSubject.deserialize(serializedNullString));
    }

    @Test
    void testDeserializeEmptyBytes() {
        assertEquals(Void.class, testSubject.classForType(SerializedType.emptyType()));
        assertNull(testSubject.deserialize(new SimpleSerializedObject<>(new byte[0], byte[].class, SerializedType.emptyType())));
    }

    @Test
    void testDeserializeLenientIgnoresUnknownValues() {
        testSubject = JacksonSerializer.builder().lenientDeserialization().objectMapper(objectMapper).build();
        SerializedObject<JsonNode> serialized = testSubject.serialize(new ComplexObject("one", "two", 3), JsonNode.class);
        ObjectNode data = (ObjectNode) serialized.getData();
        JsonNodeFactory nf = objectMapper.getNodeFactory();
        data.set("newField", nf.textNode("newValue"));
        ArrayNode arrayNode = nf.arrayNode().add(data);
        ComplexObject actual = testSubject.deserialize(new SimpleSerializedObject<>(arrayNode, JsonNode.class, serialized.getType()));
        assertEquals("one", actual.getValue1());
        assertEquals("two", actual.getValue2());
        assertEquals(3, actual.getValue3());
    }

    public static class ComplexObject {

        private final String value1;
        private final String value2;
        private final int value3;

        @JsonCreator
        public ComplexObject(@JsonProperty("value1") String value1,
                             @JsonProperty("value2") String value2,
                             @JsonProperty("value3") int value3) {
            this.value1 = value1;
            this.value2 = value2;
            this.value3 = value3;
        }

        public String getValue1() {
            return value1;
        }

        public String getValue2() {
            return value2;
        }

        public int getValue3() {
            return value3;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ComplexObject that = (ComplexObject) o;
            return value3 == that.value3 &&
                    Objects.equals(value1, that.value1) &&
                    Objects.equals(value2, that.value2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value1, value2, value3);
        }
    }

    public static class SimpleSerializableType {

        private final String value;
        private final Instant time;
        private final SimpleSerializableType nested;

        public SimpleSerializableType(String value) {
            this(value, Instant.now(), null);
        }

        @JsonCreator
        public SimpleSerializableType(@JsonProperty("value") String value,
                                      @JsonProperty("time") Instant time,
                                      @JsonProperty("nested") SimpleSerializableType nested) {
            this.value = value;
            this.time = time;
            this.nested = nested;
        }

        public SimpleSerializableType getNested() {
            return nested;
        }

        public String getValue() {
            return value;
        }

        public Instant getTime() {
            return time;
        }
    }
}
