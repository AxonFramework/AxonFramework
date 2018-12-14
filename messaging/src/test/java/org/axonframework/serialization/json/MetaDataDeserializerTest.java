/*
 * Copyright (c) 2010-2018. Axon Framework
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.axonframework.messaging.MetaData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

@RunWith(Parameterized.class)
public class MetaDataDeserializerTest {

    private final ObjectMapper.DefaultTyping defaultTyping;
    private String serializedString;
    private String emptySerializedString;
    private String serializedContainerString;
    private String serializedDataInDataString;
    private ObjectMapper objectMapper;

    @Parameters(name = "{0}")
    public static Collection<ObjectMapper.DefaultTyping> data() {
        return Arrays.asList(null,
                             ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT,
                             ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS,
                             ObjectMapper.DefaultTyping.NON_FINAL,
                             ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
    }

    public MetaDataDeserializerTest(ObjectMapper.DefaultTyping defaultTyping) {
        this.defaultTyping = defaultTyping;
    }

    @Before
    public void setup() throws JsonProcessingException {
        objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(
                new SimpleModule("Axon-Jackson Module").addDeserializer(MetaData.class, new MetaDataDeserializer()));

        if (defaultTyping != null) {
            objectMapper.enableDefaultTyping(defaultTyping);
        }

        MetaData metaData = new MetaData(Collections.singletonMap("one", "two"));
        this.serializedString = objectMapper.writeValueAsString(metaData);

        MetaData metaData1 = new MetaData(new HashMap<>());
        this.emptySerializedString = objectMapper.writeValueAsString(metaData1);

        Container container = new Container("a", metaData, 1);
        this.serializedContainerString = objectMapper.writeValueAsString(container);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("one", metaData);
        MetaData dataInData = new MetaData(map2);
        Container container2 = new Container("a", dataInData, 1);
        this.serializedDataInDataString = objectMapper.writeValueAsString(container2);
    }

    @Test
    public void testMetaDataSerializationWithDefaultTyping() throws IOException {
        MetaData deserialized = this.objectMapper.readValue(this.serializedString, MetaData.class);
        assertEquals(deserialized.get("one"), "two");
        assertEquals(this.serializedString, objectMapper.writeValueAsString(deserialized));
    }

    @Test
    public void testEmptyMetaDataSerializationWithDefaultTyping() throws IOException {
        MetaData deserialized = this.objectMapper.readValue(this.emptySerializedString, MetaData.class);
        assertTrue(deserialized.entrySet().isEmpty());

        assertEquals(this.emptySerializedString, objectMapper.writeValueAsString(deserialized));
    }

    @Test
    public void testMetaDataContainerWithDefaultTyping() throws IOException {
        Container deserialized = this.objectMapper.readValue(this.serializedContainerString, Container.class);
        assertEquals(deserialized.b.get("one"), "two");

        assertEquals(this.serializedContainerString, objectMapper.writeValueAsString(deserialized));
    }

    @Test
    public void testMetaDataContainerWithDataInDataWithDefaultTyping() throws IOException {
        Container deserialized = this.objectMapper.readValue(this.serializedDataInDataString, Container.class);
        if (defaultTyping != null) {
            assertEquals(((MetaData) deserialized.b.get("one")).get("one"), "two");
        } else {
            // as there is no typing information, Jackson can't know it's a MetaData entry
            assertEquals(((Map) deserialized.b.get("one")).get("one"), "two");
        }

        assertEquals(this.serializedDataInDataString, objectMapper.writeValueAsString(deserialized));
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
