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


package org.axonframework.serialization.gson;

import com.google.gson.*;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GsonSerializerTest {

    private final GsonSerializer testSubject = GsonSerializer.builder()
            .gsonBuilder(
                    new GsonBuilder()
                            .registerTypeAdapter(SimpleSerializableTypeList.class, new SimpleSerializableTypeListDeserializer())
                            .registerTypeAdapter(ComplexObject.class, new ComplexObjectDeserializer())
            ).build();

    private Instant time = Instant.now();

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

    /**
     * http://www.gwtproject.org/doc/latest/DevGuideServerCommunication.html#DevGuideSerializableTypes
     * <p>
     * as a good practice, wrap classes with type parameters, like `List<SimpleSerializableType>`
     * into `SimpleSerializableTypeList extends ArrayList<SimpleSerializableType>`
     */
    static class SimpleSerializableTypeList extends ArrayList<SimpleSerializableType> {
        public SimpleSerializableTypeList() {
            super();
        }

        public SimpleSerializableTypeList(SimpleSerializableType obj) {
            super();
            super.add(obj);
        }
    }

    static class SimpleSerializableTypeListDeserializer implements JsonDeserializer<SimpleSerializableTypeList> {
        @Override
        public SimpleSerializableTypeList deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            SimpleSerializableTypeList list = new SimpleSerializableTypeList();
            Iterator<JsonElement> iter = json.getAsJsonArray().iterator();

            while (iter.hasNext()) {
                SimpleSerializableType deserialized = context.deserialize(iter.next(), SimpleSerializableType.class);
                list.add(deserialized);
            }

            return list;
        }
    }

    @Test
    void testSerializeAndDeserializeList() {
        SimpleSerializableType toSerialize =
                new SimpleSerializableType(
                        "first",
                        time,
                        new SimpleSerializableType("nested")
                );

        SimpleSerializableTypeList toSerializeList = new SimpleSerializableTypeList(toSerialize);
        SerializedObject<String> serialized = testSubject.serialize(toSerializeList, String.class);

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


        Object actual = testSubject.deserialize(
                new SimpleSerializedObject<>(
                        serialized.getData(),
                        byte[].class,
                        "someUnknownType",
                        "42.1"
                )
        );

        assertTrue(actual instanceof UnknownSerializedType);
        UnknownSerializedType actualUnknown = ((UnknownSerializedType) actual);

        assertTrue(actualUnknown.supportsFormat(JsonElement.class));
        JsonObject actualJson = actualUnknown.readData(JsonElement.class).getAsJsonObject();

        assertEquals("first", actualJson.get("value").getAsString());
        assertEquals("nested", actualJson.get("nested").getAsJsonObject().get("value").getAsString());
    }

    @Test
    void testSerializeAndDeserializeObject_JsonNodeFormat() {
        SimpleSerializableType toSerialize = new SimpleSerializableType("first", time,
                new SimpleSerializableType("nested"));

        SerializedObject<JsonElement> serialized = testSubject.serialize(toSerialize, JsonElement.class);
        SimpleSerializableType actual = testSubject.deserialize(serialized);

        assertEquals(toSerialize.getValue(), actual.getValue());
        assertEquals(toSerialize.getNested().getValue(), actual.getNested().getValue());
    }

    @Test
    void testCustomObjectMapperRevisionResolverAndConverter() {
        RevisionResolver revisionResolver = spy(new AnnotationRevisionResolver());
        ChainingConverter converter = spy(new ChainingConverter());

        GsonSerializer anotherTestSubject = GsonSerializer.builder()
                .revisionResolver(revisionResolver)
                .converter(converter)
                .build();

        SerializedObject<byte[]> serialized = anotherTestSubject.serialize(new SimpleSerializableType("test"),
                byte[].class);
        SimpleSerializableType actual = anotherTestSubject.deserialize(serialized);

        assertNotNull(actual);
        verify(revisionResolver).revisionOf(SimpleSerializableType.class);
        verify(converter, times(2)).registerConverter(isA(ContentTypeConverter.class));
    }

    @Test
    void testSerializeMetaData() {
        SerializedObject<byte[]> serialized = testSubject.serialize(
                MetaData.from(singletonMap("test", "test")),
                byte[].class);
        MetaData actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertEquals("test", actual.get("test"));
        assertEquals(1, actual.size());
    }

    @Test
    void testSerializeMetaDataWithComplexObjects() {
        ComplexObject complexObject = new ComplexObject("String1", "String2", 3);
        MetaData metaData = MetaData.with("myKey", complexObject);
        SerializedObject<byte[]> serialized = testSubject.serialize(metaData, byte[].class);
        MetaData actual = testSubject.deserialize(serialized);

        Map<String, Object> actualValue = (Map<String, Object>) actual.get("myKey");
        assertNotNull(actualValue);
        assertEquals(complexObject.getValue1(), actualValue.get("value1"));
        assertEquals(complexObject.getValue2(), actualValue.get("value2"));
        // https://stackoverflow.com/questions/15507997/how-to-prevent-gson-from-expressing-integers-as-floats
        assertEquals(complexObject.getValue3(), Double.valueOf(actualValue.get("value3").toString()).intValue());
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
        GsonSerializer testSubject = GsonSerializer.builder().lenientDeserialization().build();

        SerializedObject<JsonElement> serialized = testSubject.serialize(new ComplexObject("one", "two", 3), JsonElement.class);

        JsonObject data = serialized.getData().getAsJsonObject();
        data.addProperty("newField", "newValue");

        ComplexObject actual = testSubject.deserialize(new SimpleSerializedObject<>(data, JsonElement.class, serialized.getType()));

        assertEquals("one", actual.getValue1());
        assertEquals("two", actual.getValue2());
        assertEquals(3, actual.getValue3());
    }

    public static class ComplexObjectDeserializer implements JsonDeserializer<ComplexObject> {

        @Override
        public ComplexObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            return new ComplexObject(
                    jsonObject.get("value1").getAsString(),
                    jsonObject.get("value2").getAsString(),
                    jsonObject.get("value3").getAsInt()
            );
        }
    }

    public static class ComplexObject {

        private final String value1;
        private final String value2;
        private final int value3;

        public ComplexObject(String value1,
                             String value2,
                             int value3) {
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

        public SimpleSerializableType(String value,
                                      Instant time,
                                      SimpleSerializableType nested) {
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

