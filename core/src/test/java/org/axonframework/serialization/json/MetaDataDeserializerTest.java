package org.axonframework.serialization.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.axonframework.messaging.MetaData;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class MetaDataDeserializerTest {

    String serializedString;
    String emptySerializedString;
    String serializedContainerString;
    String serializedDataInDataString;
    ObjectMapper objectMapper;

    public static class Container {

        private String a;
        private MetaData b;
        private Integer c;

        @JsonCreator
        public Container(
                @JsonProperty("a") String a,
                @JsonProperty("b") MetaData b,
                @JsonProperty("c") Integer c){
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

    @Before
    public void setup() throws JsonProcessingException {
        objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(
            new SimpleModule("Axon-Jackson Module").addDeserializer(MetaData.class, new MetaDataDeserializer()));
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);

        Map<String, Object> map = new HashMap<>();
        map.put("one", "two");
        MetaData metaData = new MetaData(map);
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
        System.out.println(this.serializedString);
        MetaData deserialized = this.objectMapper.readValue(this.serializedString, MetaData.class);
        assertEquals(deserialized.get("one"), "two");
        System.out.println(objectMapper.writeValueAsString(deserialized));
    }

    @Test
    public void testEmptyMetaDataSerializationWithDefaultTyping() throws IOException {
        System.out.println(this.emptySerializedString);
        MetaData deserialized = this.objectMapper.readValue(this.emptySerializedString, MetaData.class);
        assertTrue(deserialized.entrySet().isEmpty());
        System.out.println(objectMapper.writeValueAsString(deserialized));
    }

    @Test
    public void testMetaDataContainerWithDefaultTyping() throws IOException {
        System.out.println(this.serializedContainerString);
        Container deserialized = this.objectMapper.readValue(this.serializedContainerString, Container.class);
        assertEquals(deserialized.b.get("one"), "two");
        System.out.println(objectMapper.writeValueAsString(deserialized));
    }

    @Test
    @Ignore("Cannot deserialize MetaData inside MetaData since ObjectMapper used does not have DefaultTyping turned ON")
    public void testMetaDataContainerWithDataInDataWithDefaultTyping() throws IOException {
        System.out.println(this.serializedDataInDataString);
        Container deserialized = this.objectMapper.readValue(this.serializedDataInDataString, Container.class);
        assertEquals(MetaData.from((Map)deserialized.b.get("one")).get("one"), "two");
        System.out.println(objectMapper.writeValueAsString(deserialized));
    }

}
