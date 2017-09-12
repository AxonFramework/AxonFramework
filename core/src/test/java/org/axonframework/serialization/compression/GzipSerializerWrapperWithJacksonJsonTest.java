package org.axonframework.serialization.compression;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.ChainingConverter;
import org.axonframework.serialization.ContentTypeConverter;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class GzipSerializerWrapperWithJacksonJsonTest {

    private GzipSerializerWrapper testSubject;
    private JacksonSerializer embeddedSerializer;
    private Instant time;

    @Before
    public void setUp() throws Exception {
        embeddedSerializer = new JacksonSerializer();
        testSubject = new GzipSerializerWrapper(embeddedSerializer);
        time = Instant.now();
    }

    @Test
    public void testCanSerializeToStringByteArrayAndInputStream() {
        assertTrue(testSubject.canSerializeTo(byte[].class));
        assertTrue(testSubject.canSerializeTo(String.class));
        assertTrue(testSubject.canSerializeTo(InputStream.class));
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
    public void testDeserializeUncompressedObject_ByteArrayFormat() throws Exception {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                                                                        new SimpleSerializableType("nested"));

        SerializedObject<byte[]> serialized = embeddedSerializer.serialize(toSerialize, byte[].class);

        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertEquals(toSerialize.getValue(), actual.getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.getNested().getValue());
    }

    @Test
    public void testDeserializeUncompressedObject_InputStreamFormat() throws Exception {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                new SimpleSerializableType("nested"));

        SerializedObject<InputStream> serialized = testSubject.serialize(toSerialize, InputStream.class);

        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertEquals(toSerialize.getValue(), actual.getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.getNested().getValue());
    }

    @Test
    public void testCustomObjectMapperRevisionResolverAndConverter() {
        ObjectMapper objectMapper = spy(new ObjectMapper());
        RevisionResolver revisionResolver = spy(new AnnotationRevisionResolver());
        ChainingConverter converter = spy(new ChainingConverter());

        embeddedSerializer = new JacksonSerializer(objectMapper, revisionResolver, converter);
        testSubject = new GzipSerializerWrapper(embeddedSerializer);

        SerializedObject<byte[]> serialized = testSubject.serialize(new SimpleSerializableType("test"),
                                                                    byte[].class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        verify(objectMapper).readerFor(SimpleSerializableType.class);
        verify(objectMapper).writer();
        verify(revisionResolver).revisionOf(SimpleSerializableType.class);
        verify(converter, times(2)).registerConverter(isA(ContentTypeConverter.class));
        assertSame(objectMapper, embeddedSerializer.getObjectMapper());
    }

    @Test
    public void testCustomObjectMapperAndRevisionResolver() {
        ObjectMapper objectMapper = spy(new ObjectMapper());
        RevisionResolver revisionResolver = spy(new AnnotationRevisionResolver());

        embeddedSerializer = new JacksonSerializer(objectMapper, revisionResolver);
        testSubject = new GzipSerializerWrapper(embeddedSerializer);

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
    public void testCustomObjectMapper() {
        ObjectMapper objectMapper = spy(new ObjectMapper());

        embeddedSerializer = new JacksonSerializer(objectMapper);
        testSubject = new GzipSerializerWrapper(embeddedSerializer);

        SerializedObject<byte[]> serialized = testSubject.serialize(new SimpleSerializableType("test"),
                                                                    byte[].class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertTrue(testSubject.getConverter() instanceof ChainingConverter);
        verify(objectMapper).readerFor(SimpleSerializableType.class);
        verify(objectMapper).writer();
    }

    @Test
    public void testSerializeMetaData() {
        embeddedSerializer = new JacksonSerializer();
        testSubject = new GzipSerializerWrapper(embeddedSerializer);

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
        embeddedSerializer.getObjectMapper().enableDefaultTypingAsProperty(
                ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "@type");

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
