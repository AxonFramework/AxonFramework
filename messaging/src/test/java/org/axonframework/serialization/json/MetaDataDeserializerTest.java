/*
 * Copyright (c) 2010-2023. Axon Framework
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class MetaDataDeserializerTest {

    public static Stream<Arguments> objectMappers() {
        return Stream.of(null,
                ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT,
                ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS,
                ObjectMapper.DefaultTyping.NON_FINAL,
                ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE)
                .map(defaultTyping -> Arguments.of(objectMapper(defaultTyping), defaultTyping));
    }

    private static ObjectMapper objectMapper(ObjectMapper.DefaultTyping defaultTyping) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(
                new SimpleModule("Axon-Jackson Module").addDeserializer(MetaData.class, new MetaDataDeserializer()));

        if (defaultTyping != null) {
            objectMapper.enableDefaultTyping(defaultTyping);
        }
        return objectMapper;
    }

    @MethodSource("objectMappers")
    @ParameterizedTest
    void metaDataSerializationWithDefaultTyping(ObjectMapper objectMapper) throws IOException {
        MetaData metaData = new MetaData(Collections.singletonMap("one", "two"));
        String serializedString = objectMapper.writeValueAsString(metaData);

        MetaData deserialized = objectMapper.readValue(serializedString, MetaData.class);
        assertEquals("two", deserialized.get("one"));
        assertEquals(serializedString, objectMapper.writeValueAsString(deserialized));
    }

    @MethodSource("objectMappers")
    @ParameterizedTest
    void emptyMetaDataSerializationWithDefaultTyping(ObjectMapper objectMapper) throws IOException {
        MetaData metaData1 = new MetaData(new HashMap<>());
        String emptySerializedString = objectMapper.writeValueAsString(metaData1);

        MetaData deserialized = objectMapper.readValue(emptySerializedString, MetaData.class);
        assertTrue(deserialized.entrySet().isEmpty());

        assertEquals(emptySerializedString, objectMapper.writeValueAsString(deserialized));
    }

    @MethodSource("objectMappers")
    @ParameterizedTest
    void metaDataContainerWithDefaultTyping(ObjectMapper objectMapper) throws IOException {
        MetaData metaData = new MetaData(Collections.singletonMap("one", "two"));

        Container container = new Container("a", metaData, 1);
        String serializedContainerString = objectMapper.writeValueAsString(container);

        Container deserialized = objectMapper.readValue(serializedContainerString, Container.class);
        assertEquals("two", deserialized.b.get("one"));

        assertEquals(serializedContainerString, objectMapper.writeValueAsString(deserialized));
    }

    @MethodSource("objectMappers")
    @ParameterizedTest
    void metaDataContainerWithDataInDataWithDefaultTyping(ObjectMapper objectMapper, ObjectMapper.DefaultTyping defaultTyping) throws IOException {
        MetaData metaData = new MetaData(Collections.singletonMap("one", "two"));

        Map<String, Object> map2 = new HashMap<>();
        map2.put("one", metaData);
        MetaData dataInData = new MetaData(map2);
        Container container2 = new Container("a", dataInData, 1);
        String serializedDataInDataString = objectMapper.writeValueAsString(container2);

        Container deserialized = objectMapper.readValue(serializedDataInDataString, Container.class);
        if (defaultTyping != null) {
            assertEquals(((MetaData) deserialized.b.get("one")).get("one"), "two");
        }
        else {
            // as there is no typing information, Jackson can't know it's a MetaData entry
            assertEquals(((Map) deserialized.b.get("one")).get("one"), "two");
        }

        assertEquals(serializedDataInDataString, objectMapper.writeValueAsString(deserialized));
    }

    public static class Container {

        private String a;
        private MetaData b;
        private Integer c;

        @JsonCreator
        public Container(
                @JsonProperty("a") String a,
                @JsonProperty("b") MetaData b,
                @JsonProperty("c") Integer c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        public MetaData getB() {
            return b;
        }

        public void setB(MetaData b) {
            this.b = b;
        }

        public Integer getC() {
            return c;
        }

        public void setC(Integer c) {
            this.c = c;
        }
    }

}
