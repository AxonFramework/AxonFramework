/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class JacksonSerializerTest {

    private JacksonSerializer testSubject;
    private Instant time;

    @Before
    public void setUp() throws Exception {
        testSubject = new JacksonSerializer();
        time = Instant.now();
    }

    @Test
    public void testCanSerializeToStringByteArrayAndInputStream() {
        assertTrue(testSubject.canSerializeTo(byte[].class));
        assertTrue(testSubject.canSerializeTo(String.class));
        assertTrue(testSubject.canSerializeTo(InputStream.class));
    }

    @Test
    public void testSerializeAndDeserializeObject_StringFormat() throws Exception {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<String> serialized = testSubject.serialize(toSerialize, String.class);

        System.out.println(serialized.getData());
        SimpleSerializableType actual = testSubject.deserialize(serialized);
        assertEquals(toSerialize.getValue(), actual.getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.getNested().getValue());
    }

    @Test
    public void testSerializeAndDeserializeObject_ByteArrayFormat() throws Exception {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<byte[]> serialized = testSubject.serialize(toSerialize, byte[].class);

        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertEquals(toSerialize.getValue(), actual.getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.getNested().getValue());
    }

    @Test
    public void testSerializeAndDeserializeObject_JsonNodeFormat() throws Exception {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<JsonNode> serialized = testSubject.serialize(toSerialize, JsonNode.class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertEquals(toSerialize.getValue(), actual.getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.getNested().getValue());
    }

    @Test
    public void testCustomObjectMapperRevisionResolverAndConverter() {
        ObjectMapper objectMapper = spy(new ObjectMapper());
        RevisionResolver revisionResolver = spy(new AnnotationRevisionResolver());
        ChainingConverter converter = spy(new ChainingConverter());

        testSubject = new JacksonSerializer(objectMapper, revisionResolver, converter);

        SerializedObject<byte[]> serialized = testSubject.serialize(new SimpleSerializableType("test"),
                                                                    byte[].class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        verify(objectMapper).reader(SimpleSerializableType.class);
        verify(objectMapper).writer();
        verify(revisionResolver).revisionOf(SimpleSerializableType.class);
        verify(converter, times(2)).registerConverter(isA(ContentTypeConverter.class));
        assertSame(objectMapper, testSubject.getObjectMapper());
    }

    @Test
    public void testCustomObjectMapperAndRevisionResolver() {
        ObjectMapper objectMapper = spy(new ObjectMapper());
        RevisionResolver revisionResolver = spy(new AnnotationRevisionResolver());

        testSubject = new JacksonSerializer(objectMapper, revisionResolver);

        SerializedObject<byte[]> serialized = testSubject.serialize(new SimpleSerializableType("test"),
                                                                    byte[].class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertTrue(testSubject.getConverter() instanceof ChainingConverter);
        verify(objectMapper).reader(SimpleSerializableType.class);
        verify(objectMapper).writer();
        verify(revisionResolver).revisionOf(SimpleSerializableType.class);
    }

    @Test
    public void testCustomObjectMapper() {
        ObjectMapper objectMapper = spy(new ObjectMapper());

        testSubject = new JacksonSerializer(objectMapper);

        SerializedObject<byte[]> serialized = testSubject.serialize(new SimpleSerializableType("test"),
                                                                    byte[].class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertTrue(testSubject.getConverter() instanceof ChainingConverter);
        verify(objectMapper).reader(SimpleSerializableType.class);
        verify(objectMapper).writer();
    }

    @Test
    public void testSerializeMetaData() {
        testSubject = new JacksonSerializer();

        SerializedObject<byte[]> serialized = testSubject.serialize(MetaData.from(singletonMap("test", "test")),
                                                                    byte[].class);
        MetaData actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertEquals("test", actual.get("test"));
        assertEquals(1, actual.size());
    }

    @Test
    public void testSerializeMetaDataWithComplexObjects() throws Exception {
        // typing must be enabled for this (which we expect end-users to do
        testSubject.getObjectMapper().enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "@type");

        MetaData metaData = MetaData.with("myKey", new ComplexObject("String1", "String2", 3));
        SerializedObject<byte[]> serialized = testSubject.serialize(metaData, byte[].class);
        System.out.println(new String(serialized.getData()));
        MetaData actual = testSubject.deserialize(serialized);

        assertEquals(metaData, actual);
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
