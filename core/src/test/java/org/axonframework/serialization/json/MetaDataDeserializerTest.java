package org.axonframework.serialization.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.axonframework.messaging.MetaData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class MetaDataDeserializerTest {

    private static String serializedString;
    private static String emptySerializedString;
    private static String serializedContainerString;
    private static String serializedDataInDataString;
    private static ObjectMapper objectMapper;

    private static String serializedStringDT;
    private static String emptySerializedStringDT;
    private static String serializedContainerStringDT;
    private static String serializedDataInDataStringDT;
    private static ObjectMapper objectMapperDT;

    public static class Container {

        private String a;
        private MetaData b;
        private Integer c;

        @JsonCreator
        Container(@JsonProperty("a") String a, @JsonProperty("b") MetaData b, @JsonProperty("c") Integer c){
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

    @BeforeClass
    public static void setup() throws JsonProcessingException {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(
            new SimpleModule("Axon-Jackson Module")
                .addSerializer(MetaData.class, new MetaDataSerializer())
                .addDeserializer(MetaData.class, new MetaDataDeserializer())
        );

        Map<String, Object> map = new HashMap<>();
        map.put("one", "two");
        MetaData metaData = new MetaData(map);
        serializedString = objectMapper.writeValueAsString(metaData);

        MetaData metaData1 = new MetaData(new HashMap<>());
        emptySerializedString = objectMapper.writeValueAsString(metaData1);

        Container container = new Container("a", metaData, 1);
        serializedContainerString = objectMapper.writeValueAsString(container);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("one", metaData);
        MetaData dataInData = new MetaData(map2);
        Container container2 = new Container("a", dataInData, 1);
        serializedDataInDataString = objectMapper.writeValueAsString(container2);

        objectMapperDT = new ObjectMapper();
        objectMapperDT.registerModule(
            new SimpleModule("Axon-Jackson Module")
                .addSerializer(MetaData.class, new MetaDataSerializer())
                .addDeserializer(MetaData.class, new MetaDataDeserializer())
        );
        objectMapperDT.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);

        map = new HashMap<>();
        map.put("one", "two");
        metaData = new MetaData(map);
        serializedStringDT = objectMapperDT.writeValueAsString(metaData);

        metaData1 = new MetaData(new HashMap<>());
        emptySerializedStringDT = objectMapperDT.writeValueAsString(metaData1);

        container = new Container("a", metaData, 1);
        serializedContainerStringDT = objectMapperDT.writeValueAsString(container);

        map2 = new HashMap<>();
        map2.put("one", metaData);
        dataInData = new MetaData(map2);
        container2 = new Container("a", dataInData, 1);
        serializedDataInDataStringDT = objectMapperDT.writeValueAsString(container2);
    }

    @Test
    public void testMetaDataSerializationWithDefaultTyping() throws IOException {
        System.out.println(serializedStringDT);
        MetaData deserialized = objectMapperDT.readValue(serializedStringDT, MetaData.class);
        assertEquals(deserialized.get("one"), "two");
        System.out.println(objectMapperDT.writeValueAsString(deserialized));
    }

    @Test
    public void testMetaDataSerializationWithoutDefaultTyping() throws IOException {
        System.out.println(serializedString);
        MetaData deserialized = objectMapper.readValue(serializedString, MetaData.class);
        assertEquals(deserialized.get("one"), "two");
        System.out.println(objectMapper.writeValueAsString(deserialized));
    }

    @Test
    public void testEmptyMetaDataSerializationWithDefaultTyping() throws IOException {
        System.out.println(emptySerializedStringDT);
        MetaData deserialized = objectMapperDT.readValue(emptySerializedStringDT, MetaData.class);
        assertTrue(deserialized.entrySet().isEmpty());
        System.out.println(objectMapperDT.writeValueAsString(deserialized));
    }

    @Test
    public void testEmptyMetaDataSerializationWithoutDefaultTyping() throws IOException {
        System.out.println(emptySerializedString);
        MetaData deserialized = objectMapper.readValue(emptySerializedString, MetaData.class);
        assertTrue(deserialized.entrySet().isEmpty());
        System.out.println(objectMapper.writeValueAsString(deserialized));
    }

    @Test
    public void testMetaDataContainerWithDefaultTyping() throws IOException {
        System.out.println(serializedContainerStringDT);
        Container deserialized = objectMapperDT.readValue(serializedContainerStringDT, Container.class);
        assertEquals(deserialized.b.get("one"), "two");
        System.out.println(objectMapperDT.writeValueAsString(deserialized));
    }

    @Test
    public void testMetaDataContainerWithoutDefaultTyping() throws IOException {
        System.out.println(serializedContainerString);
        Container deserialized = objectMapper.readValue(serializedContainerString, Container.class);
        assertEquals(deserialized.b.get("one"), "two");
        System.out.println(objectMapper.writeValueAsString(deserialized));
    }

    @Test
    public void testMetaDataContainerWithDataInDataWithDefaultTyping() throws IOException {
        System.out.println(serializedDataInDataStringDT);
        Container deserialized = objectMapperDT.readValue(serializedDataInDataStringDT, Container.class);
        assertEquals(((Map)deserialized.b.get("one")).get("one"), "two");
        System.out.println(objectMapperDT.writeValueAsString(deserialized));
    }

    @Test
    public void testMetaDataContainerWithDataInDataWithoutDefaultTyping() throws IOException {
        System.out.println(serializedDataInDataString);
        Container deserialized = objectMapper.readValue(serializedDataInDataString, Container.class);
        assertEquals(((Map)deserialized.b.get("one")).get("one"), "two");
        System.out.println(objectMapper.writeValueAsString(deserialized));
    }
}
