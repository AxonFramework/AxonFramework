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

package org.axonframework.serialization.avro;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.axonframework.serialization.ConversionException;
import org.axonframework.serialization.ConverterTestSuite;
import org.axonframework.serialization.avro.test.ComplexObject;
import org.axonframework.serialization.avro.test.ComplexObjectSchemas;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.InputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

/**
 * Test class validating the {@link AvroConverter}.
 *
 * @author Simon Zambrovski
 */
class AvroConverterTest extends ConverterTestSuite<AvroConverter> {

    private static final ComplexObject complexObject = ComplexObject
            .newBuilder()
            .setValue1("foo")
            .setValue2("bar")
            .setValue3(42)
            .build();
    private static final GenericData.Record record = new GenericData.Record(ComplexObjectSchemas.compatibleSchema);

    static {
        record.put("value1", complexObject.getValue1());
        record.put("value2", complexObject.getValue2());
        record.put("value3", complexObject.getValue3());
    }

    private static final GenericRecordToByteArrayConverter toByteArrayConverter = new GenericRecordToByteArrayConverter();

    private SchemaStore.Cache store;

    @Override
    protected AvroConverter buildConverter() {
        store = new SchemaStore.Cache();
        store.addSchema(ComplexObject.getClassSchema());
        store.addSchema(ComplexObjectSchemas.compatibleSchema);
        return new AvroConverter(
                store, (c) -> c
        );
    }

    @Override
    protected Stream<Arguments> commonSupportedConversions() {
        return Stream.of(
                // Intermediate conversion levels:
                arguments(String.class, byte[].class),
                arguments(byte[].class, String.class),
                arguments(byte[].class, InputStream.class),
                arguments(InputStream.class, byte[].class),
                arguments(String.class, InputStream.class),
                arguments(InputStream.class, String.class),
                // Same type:
                arguments(byte[].class, byte[].class),
                arguments(String.class, String.class)
        );
    }

    @Override
    protected Stream<Arguments> specificSupportedConversions() {
        return Stream.of(
                arguments(byte[].class, GenericRecord.class), // before upcaster
                arguments(GenericRecord.class, ComplexObject.class), // after upcaster
                arguments(ComplexObject.class, byte[].class) // direct to bytes
        );
    }

    @Override
    protected Stream<Arguments> specificUnsupportedConversions() {
        return Stream.of(
                arguments(byte[].class, Integer.class), // no strategy for integer
                arguments(Integer.class, byte[].class) // no strategy for integer
        );
    }

    @Override
    protected Stream<Arguments> specificSameTypeConversions() {
        return Stream.of(
                arguments(complexObject, ComplexObject.class),
                arguments(record, GenericRecord.class)
        );
    }

    @Override
    protected Stream<Arguments> specificConversionScenarios() {
        byte[] encodedBytes = toByteArrayConverter.convert(record);
        return Stream.of(
                arguments(complexObject, ComplexObject.class, byte[].class),
                arguments(encodedBytes, byte[].class, ComplexObject.class)
        );
    }

    @Override
    protected Stream<Arguments> commonConversionScenarios() {
        return Stream.empty();
    }

    @Test
    void convertNull() {
        byte[] serialized = testSubject.convert(null, byte[].class);
        assertThat(serialized).isNull();
        ComplexObject deserialized = testSubject.convert(null, ComplexObject.class);
        assertThat(deserialized).isNull();
    }

    @Test
    void deserializeFromCompatibleObject() {
        byte[] encodedBytes = toByteArrayConverter.convert(record);
        ComplexObject deserialized = testSubject.convert(encodedBytes, ComplexObject.class);
        assertEquals(complexObject, deserialized);
    }

    @Test
    void serializeFromCompatibleWithAdditionalIgnoredFieldObjectAndDeserialize() {
        Schema schema2 = ComplexObjectSchemas.compatibleSchemaWithAdditionalField;
        store.addSchema(schema2);

        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value2", complexObject.getValue2());
        record.put("value3", complexObject.getValue3());
        record.put("value4", "ignored value");

        byte[] encodedBytes = toByteArrayConverter.convert(record);
        ComplexObject deserialized = testSubject.convert(encodedBytes, ComplexObject.class);
        assertEquals(complexObject, deserialized);
    }

    @Test
    void serializeFromCompatibleSchemaAndDeserializeUsingDefault() {
        Schema schema2 = ComplexObjectSchemas.compatibleSchemaWithoutValue2;
        store.addSchema(schema2);
        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value3", complexObject.getValue3());

        byte[] encodedBytes = toByteArrayConverter.convert(record);
        ComplexObject deserialized = testSubject.convert(encodedBytes, ComplexObject.class);
        assertThat(deserialized).isNotNull();
        assertEquals("default value", deserialized.getValue2());
    }

    @ParameterizedTest
    @MethodSource("converterAndIncompatibleBytes")
    void failToDeserializeFromIncompatibleSchema(
            byte[] serialized,
            AvroConverter converter,
            DefaultSchemaIncompatibilityChecker incompatibilityChecker,
            String expectedMessage) {
        assertEquals(expectedMessage,
                     assertThrows(
                             ConversionException.class,
                             () -> converter.convert(serialized, ComplexObject.class)
                     ).getMessage()
        );
        // check the incompatibility remains in the cache
        assertEquals(1, incompatibilityChecker.getIncompatibilitiesCache().size());
    }


    /**
     * Returns a stream of parameters.
     *
     * @return stream of four parameters: 1. serialized bytes 2. converter 3. incompatibility checker 4. expected
     * exception message.
     */
    static Stream<Arguments> converterAndIncompatibleBytes() {

        Schema writerSchema = ComplexObjectSchemas.incompatibleSchema;

        SchemaCompatibility.Incompatibility incompatibility =
                SchemaCompatibility.checkReaderWriterCompatibility(
                        ComplexObject.getClassSchema(), writerSchema
                ).getResult().getIncompatibilities().getFirst();


        SchemaStore.Cache schemaStore = new SchemaStore.Cache();
        schemaStore.addSchema(ComplexObject.getClassSchema());
        schemaStore.addSchema(writerSchema);

        DefaultSchemaIncompatibilityChecker incompatibilityChecker = new DefaultSchemaIncompatibilityChecker();

        GenericData.Record record = new GenericData.Record(writerSchema);
        record.put("value2", complexObject.getValue1());
        record.put("value3", complexObject.getValue3());

        byte[] encodedBytes = toByteArrayConverter.convert(record);

        return Stream.of(
                Arguments.of(
                        encodedBytes,
                        new AvroConverter(schemaStore,
                                          (c) -> c.performAvroCompatibilityCheck(true)
                                                  .includeSchemasInStackTraces(false)
                                                  .schemaIncompatibilityChecker(incompatibilityChecker))

                        ,
                        incompatibilityChecker,
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                "[" + AvroUtil.incompatibilityPrinter(incompatibility) + "]",
                                false
                        ).getMessage()
                ),
                Arguments.of(
                        encodedBytes,
                        new AvroConverter(schemaStore,
                                          (c) -> c.performAvroCompatibilityCheck(true)
                                                  .includeSchemasInStackTraces(true)
                                                  )
                        ,
                        incompatibilityChecker,
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                "[" + AvroUtil.incompatibilityPrinter(incompatibility) + "]",
                                true
                        ).getMessage()
                ),
                Arguments.of(
                        encodedBytes,
                        new AvroConverter(schemaStore,
                                          (c) -> c.performAvroCompatibilityCheck(false)
                                                  .includeSchemasInStackTraces(false)
                        ),
                        incompatibilityChecker,
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                (Exception) null,
                                false
                        ).getMessage()
                ),
                Arguments.of(
                        encodedBytes,
                        new AvroConverter(schemaStore,
                                          (c) -> c.performAvroCompatibilityCheck(false)
                                                  .includeSchemasInStackTraces(true)
                        ),
                        incompatibilityChecker,
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
}
