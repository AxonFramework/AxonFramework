/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.serialization.avro;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;
import org.axonframework.serialization.avro.test.ComplexObject;
import org.axonframework.serialization.avro.test.ComplexObjectSchemas;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Test Avro Serializer with default Java-SpecificRecord-Strategy.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class AvroSerializerTest {

    private static final ComplexObject complexObject = ComplexObject
            .newBuilder()
            .setValue1("foo")
            .setValue2("bar")
            .setValue3(42)
            .build();


    private final RevisionResolver revisionResolver = payloadType -> null;
    private AvroSerializer testSubject;
    private Serializer serializer;
    private SchemaStore.Cache store;
    private static final GenericRecordToByteArrayConverter toByteArrayConverter = new GenericRecordToByteArrayConverter();

    @BeforeEach
    void setUp() {
        serializer = spy(JacksonSerializer.defaultSerializer());
        store = new SchemaStore.Cache();
        testSubject = AvroSerializer
                .builder()
                .serializerDelegate(serializer)
                .revisionResolver(revisionResolver)
                .schemaStore(store)
                .build();
    }

    @Test
    void testBuilderMandatoryValues() {
        assertEquals("RevisionResolver is mandatory",
                assertThrows(AxonConfigurationException.class,
                        () -> AvroSerializer.builder().build())
                        .getMessage()
        );


        assertEquals("SchemaStore is mandatory",
                assertThrows(AxonConfigurationException.class,
                        () -> AvroSerializer.builder()
                                .revisionResolver(c -> "")
                                .build())
                        .getMessage()
        );

        assertEquals("SerializerDelegate is mandatory",
                assertThrows(AxonConfigurationException.class,
                        () -> AvroSerializer.builder()
                                .revisionResolver(c -> "")
                                .schemaStore(new SchemaStore.Cache())
                                .build())
                        .getMessage()
        );

        assertEquals("At least one AvroSerializerStrategy must be provided.",
                assertThrows(AxonConfigurationException.class,
                        () -> AvroSerializer
                                .builder()
                                .revisionResolver(c -> "")
                                .serializerDelegate(serializer)
                                .schemaStore(new SchemaStore.Cache())
                                .includeDefaultAvroSerializationStrategies(false)
                                .build()
                ).getMessage()
        );

        // that should work fine
        assertNotNull(
                AvroSerializer
                        .builder()
                        .revisionResolver(c -> "")
                        .serializerDelegate(serializer)
                        .schemaStore(new SchemaStore.Cache())
                        .includeDefaultAvroSerializationStrategies(false)
                        .addSerializerStrategy(new SpecificRecordBaseSerializerStrategy(
                                new SchemaStore.Cache(),
                                c -> ""
                        ))
                        .build()
        );
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderSetterContracts() {

        assertEquals("RevisionResolver may not be null",
                assertThrows(AxonConfigurationException.class,
                        () -> AvroSerializer.builder()
                                .revisionResolver(null)
                ).getMessage()
        );


        assertEquals("SchemaStore may not be null",
                assertThrows(AxonConfigurationException.class,
                        () -> AvroSerializer.builder().schemaStore(null)
                ).getMessage()
        );


        assertEquals("Serializer delegate may not be null",
                assertThrows(AxonConfigurationException.class,
                        () -> AvroSerializer.builder()
                                .serializerDelegate(null))
                        .getMessage()
        );

        assertEquals("AvroSerializerStrategy may not be null",
                assertThrows(AxonConfigurationException.class,
                        () -> AvroSerializer.builder()
                                .addSerializerStrategy(
                                        null))
                        .getMessage()
        );
    }

    @Test
    void deliverCorrectConverter() {
        Converter converter = testSubject.getConverter();
        assertInstanceOf(ChainingConverter.class, converter);
        assertTrue(converter.canConvert(byte[].class, GenericRecord.class)); // this is registered by AvroSerializer
        assertTrue(converter.canConvert(byte[].class, JsonNode.class)); // this is registered by the JacksonSerializer
    }

    @Test
    void deliverUnknownClassIfTypeIsNotOnClasspath() {
        Class<?> clazz = testSubject.classForType(new SimpleSerializedType("org.acme.Foo", null));
        assertEquals(UnknownSerializedType.class, clazz);
    }

    @Test
    void deliverEmptyType() {
        assertEquals(SimpleSerializedType.emptyType(), testSubject.typeForClass(null));
        assertEquals(SimpleSerializedType.emptyType(), testSubject.typeForClass(Void.class));
    }

    @Test
    void deliverNonEmptyType() {
        assertEquals(new SimpleSerializedType(String.class.getCanonicalName(),
                        revisionResolver.revisionOf(String.class)),
                testSubject.typeForClass(String.class));
    }


    @Test
    void canSerialize() {
        assertTrue(testSubject.canSerializeTo(byte[].class));
        assertTrue(testSubject.canSerializeTo(GenericRecord.class));

        assertTrue(testSubject.canSerializeTo(String.class));
        assertTrue(testSubject.canSerializeTo(InputStream.class));
        assertFalse(testSubject.canSerializeTo(Integer.class));
    }

    @Test
    void serializeMetaDataByDelegate() {

        MetaData original = MetaData.from(singletonMap("test", "test"));
        SerializedObject<byte[]> serialized = testSubject.serialize(original, byte[].class);
        MetaData actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertEquals("test", actual.get("test"));
        assertEquals(1, actual.size());

        verify(serializer).serialize(original, byte[].class);
        verify(serializer).deserialize(serialized);
    }

    @Test
    void serializeNull() {
        SerializedObject<byte[]> serialized = testSubject.serialize(null, byte[].class);
        assertEquals(SerializedType.emptyType(), serialized.getType());
        assertArrayEquals("null".getBytes(StandardCharsets.UTF_8), serialized.getData());
        verify(serializer).serialize(null, byte[].class);
    }

    @Test
    void deserializeEmptyBytes() {
        assertEquals(Void.class, testSubject.classForType(SerializedType.emptyType()));
        assertNull(testSubject.deserialize(
                new SimpleSerializedObject<>(new byte[0], byte[].class, SerializedType.emptyType())
        ));
    }

    @Test
    void serializeAndDeserializeComplexObject() {
        store.addSchema(ComplexObject.getClassSchema());

        SerializedObject<byte[]> serialized = testSubject.serialize(complexObject, byte[].class);
        assertEquals(serialized.getType().getName(), ComplexObject.class.getCanonicalName());


        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertEquals(complexObject, deserialized);
    }

    @Test
    void serializeFromCompatibleObjectAndDeserialize() {
        store.addSchema(ComplexObject.getClassSchema());
        Schema schema2 = ComplexObjectSchemas.compatibleSchema;
        store.addSchema(schema2);
        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value2", complexObject.getValue2());
        record.put("value3", complexObject.getValue3());

        byte[] encodedBytes = toByteArrayConverter.convert(record);

        SerializedObject<byte[]> serialized = createSerializedObject(encodedBytes,
                ComplexObject.class.getCanonicalName());
        assertEquals(serialized.getType().getName(), ComplexObject.class.getCanonicalName());

        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertEquals(complexObject, deserialized);
    }

    @Test
    void deserializeFromGenericRecord() {
        store.addSchema(ComplexObject.getClassSchema());
        Schema schema2 = ComplexObjectSchemas.compatibleSchema;
        store.addSchema(schema2);
        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value2", complexObject.getValue2());
        record.put("value3", complexObject.getValue3());


        SerializedObject<GenericRecord> serialized = createSerializedObject(record);

        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertEquals(complexObject, deserialized);
    }

    @Test
    void serializeFromCompatibleWithAdditionalIgnoredFieldObjectAndDeserialize() {
        store.addSchema(ComplexObject.getClassSchema());
        Schema schema2 = ComplexObjectSchemas.compatibleSchemaWithAdditionalField;
        store.addSchema(schema2);
        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value2", complexObject.getValue2());
        record.put("value3", complexObject.getValue3());
        record.put("value4", "ignored value");

        byte[] encodedBytes = toByteArrayConverter.convert(record);

        SerializedObject<byte[]> serialized = createSerializedObject(encodedBytes,
                ComplexObject.class.getCanonicalName());
        assertEquals(serialized.getType().getName(), ComplexObject.class.getCanonicalName());

        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertEquals(complexObject, deserialized);
    }

    @Test
    void serializeFromCompatibleSchemaAndDeserializeUsingDefault() {
        store.addSchema(ComplexObject.getClassSchema());
        Schema schema2 = ComplexObjectSchemas.compatibleSchemaWithoutValue2;
        store.addSchema(schema2);
        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value3", complexObject.getValue3());

        byte[] encodedBytes = toByteArrayConverter.convert(record);

        SerializedObject<byte[]> serialized = createSerializedObject(encodedBytes,
                ComplexObject.class.getCanonicalName());
        assertEquals(serialized.getType().getName(), ComplexObject.class.getCanonicalName());

        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertEquals("default value", deserialized.getValue2());
    }

    @ParameterizedTest
    @MethodSource("serializerAndIncompatibleSerializedObject")
    void failToDeserializeFromIncompatibleSchema(
            SerializedObject<byte[]> serialized,
            AvroSerializer serializer,
            String expectedMessage) {
        assertEquals(expectedMessage,
                assertThrows(
                        SerializationException.class,
                        () -> serializer.deserialize(serialized)
                ).getMessage()
        );
    }

    static Stream<Arguments> serializerAndIncompatibleSerializedObject() {

        Schema writerSchema = ComplexObjectSchemas.incompatibleSchema;

        SchemaCompatibility.Incompatibility incompatibility =
                SchemaCompatibility.checkReaderWriterCompatibility(
                        ComplexObject.getClassSchema(), writerSchema
                ).getResult().getIncompatibilities().get(0);


        SchemaStore.Cache schemaStore = new SchemaStore.Cache();
        schemaStore.addSchema(ComplexObject.getClassSchema());
        schemaStore.addSchema(writerSchema);


        GenericData.Record record = new GenericData.Record(writerSchema);
        record.put("value2", complexObject.getValue1());
        record.put("value3", complexObject.getValue3());

        byte[] encodedBytes = toByteArrayConverter.convert(record);

        SerializedObject<byte[]> serialized = createSerializedObject(encodedBytes,
                ComplexObject.class.getCanonicalName());
        assertEquals(serialized.getType().getName(), ComplexObject.class.getCanonicalName());


        return Stream.of(
                Arguments.of(
                        serialized,
                        AvroSerializer
                                .builder()
                                .serializerDelegate(JacksonSerializer.defaultSerializer())
                                .revisionResolver((c) -> null)
                                .performSchemaCompatibilityCheck(true)
                                .includeSchemasInStackTraces(false)
                                .schemaStore(schemaStore)
                                .build(),
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                "[" + AvroUtil.incompatibilityPrinter(incompatibility) + "]",
                                false
                        ).getMessage()
                ),
                Arguments.of(
                        serialized,
                        AvroSerializer
                                .builder()
                                .serializerDelegate(JacksonSerializer.defaultSerializer())
                                .revisionResolver((c) -> null)
                                .schemaStore(schemaStore)
                                .performSchemaCompatibilityCheck(true)
                                .includeSchemasInStackTraces(true)
                                .build(),
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                "[" + AvroUtil.incompatibilityPrinter(incompatibility) + "]",
                                true
                        ).getMessage()
                ),
                Arguments.of(
                        serialized,
                        AvroSerializer
                                .builder()
                                .serializerDelegate(JacksonSerializer.defaultSerializer())
                                .revisionResolver((c) -> null)
                                .schemaStore(schemaStore)
                                .performSchemaCompatibilityCheck(false)
                                .includeSchemasInStackTraces(false)
                                .build(),
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                (Exception) null,
                                false
                        ).getMessage()
                ),
                Arguments.of(
                        serialized,
                        AvroSerializer
                                .builder()
                                .serializerDelegate(JacksonSerializer.defaultSerializer())
                                .revisionResolver((c) -> null)
                                .schemaStore(schemaStore)
                                .performSchemaCompatibilityCheck(false)
                                .includeSchemasInStackTraces(true)
                                .build(),
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                (Exception) null,
                                true
                        ).getMessage()
                )


        );
    }

    @Test
    void failToDeserializeIfClassIsNotAvailable() {
        Schema writerSchema = ComplexObjectSchemas.incompatibleSchema;
        store.addSchema(writerSchema);
        GenericData.Record record = new GenericData.Record(writerSchema);
        record.put("value2", complexObject.getValue1());
        record.put("value3", complexObject.getValue3());

        SimpleSerializedObject<GenericRecord> object = new SimpleSerializedObject<>(
                record,
                GenericRecord.class,
                new SimpleSerializedType("org.acme.Foo", null));
        Object deserialized = testSubject.deserialize(object);
        assertEquals(UnknownSerializedType.class, deserialized.getClass());
    }


    private static SerializedObject<byte[]> createSerializedObject(byte[] payload, String objectType) {
        return new SimpleSerializedObject<>(
                payload,
                byte[].class,
                new SimpleSerializedType(objectType, null));
    }

    private static SerializedObject<GenericRecord> createSerializedObject(GenericRecord record) {
        return new SimpleSerializedObject<>(
                record,
                GenericRecord.class,
                new SimpleSerializedType(record.getSchema().getFullName(), null));
    }
}
