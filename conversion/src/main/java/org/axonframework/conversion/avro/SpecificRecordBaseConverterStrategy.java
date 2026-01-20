/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.conversion.avro;

import jakarta.annotation.Nonnull;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.conversion.ConversionException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Avro strategy responsible for operations on <code>SpecificRecordBase</code> as a superclass of all objects you are
 * working on.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class SpecificRecordBaseConverterStrategy implements AvroConverterStrategy {

    private final SchemaStore schemaStore;
    private final SchemaIncompatibilityChecker schemaIncompatibilityChecker;
    private AvroConverterStrategyConfiguration config = AvroConverterStrategyConfiguration.DEFAULT;

    /**
     * Constructs avro conversion strategy supporting conversion and deserialization of Java Avro classes
     * extending {@link SpecificRecordBase}.
     *
     * @param schemaStore                  schema store to resolve schema from fingerprint.
     * @param schemaIncompatibilityChecker stateful utility to perform compatibility checks.
     */
    public SpecificRecordBaseConverterStrategy(
            @Nonnull SchemaStore schemaStore,
            @Nonnull SchemaIncompatibilityChecker schemaIncompatibilityChecker
    ) {
        this.schemaStore = Objects.requireNonNull(
                schemaStore,
                "Schema store must not be null.");
        this.schemaIncompatibilityChecker = Objects.requireNonNull(
                schemaIncompatibilityChecker,
                "SchemaIncompatibilityChecker must not be null.");
    }

    @Override
    @Nonnull
    public byte[] convertToSingleObjectEncoded(@Nonnull Object object) {
        if (!(object instanceof SpecificRecordBase record)) {
            throw new ConversionException(
                    "Expected object to be instance of SpecificRecordBase but it was " + object.getClass()
                                                                                               .getCanonicalName());
        }

        BinaryMessageEncoder<SpecificRecordBase> encoder = new BinaryMessageEncoder<>(record.getSpecificData(),
                                                                                      record.getSchema());
        final byte[] bytes;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.encode(record, outputStream);
            bytes = outputStream.toByteArray();
        } catch (IOException e) {
            throw new ConversionException("Failed to convert specific record", e);
        }
        return bytes;
    }

    @Override
    @Nonnull
    public <T> T convertFromSingleObjectEncoded(@Nonnull byte[] bytes,
                                                @Nonnull Class<T> readerType) {
        if (!test(readerType)) {
            throw new ConversionException("Expected reader type to be assignable from SpecificRecordBase but it was "
                                                  + readerType.getCanonicalName());
        }
        @SuppressWarnings("unchecked")
        Class<SpecificRecordBase> specificRecordBaseClass = (Class<SpecificRecordBase>) readerType;

        long fingerprint = AvroUtil.fingerprint(bytes);
        Schema writerSchema = schemaStore.findByFingerprint(fingerprint);
        if (writerSchema == null) {
            throw AvroUtil.createExceptionNoSchemaFound(readerType, fingerprint);
        }

        SpecificData readerSpecificData = SpecificData.getForClass(specificRecordBaseClass);
        Schema readerSchema = AvroUtil.getClassSchemaChecked(specificRecordBaseClass);

        if (this.config.performAvroCompatibilityCheck()) {
            schemaIncompatibilityChecker.assertSchemaCompatibility(
                    readerType,
                    readerSchema,
                    writerSchema,
                    config.includeSchemasInStackTraces()
            );
        }

        BinaryMessageDecoder<SpecificRecordBase> decoder = new BinaryMessageDecoder<>(
                readerSpecificData,
                readerSchema);
        decoder.addSchema(writerSchema);

        try {
            //noinspection unchecked
            return (T) decoder.decode(bytes);
        } catch (IOException | AvroRuntimeException e) {
            throw AvroUtil.createExceptionFailedToDeserialize(
                    readerType,
                    readerSchema,
                    writerSchema,
                    e,
                    config.includeSchemasInStackTraces()
            );
        }
    }

    @Override
    @Nonnull
    public <T> T convertFromGenericRecord(@Nonnull GenericRecord genericRecord,
                                          @Nonnull Class<T> readerType) {
        if (!test(readerType)) {
            throw new ConversionException("Expected reader type to be assignable from SpecificRecordBase but it was "
                                                  + readerType.getCanonicalName());
        }

        Schema writerSchema = genericRecord.getSchema();
        SpecificData readerSpecificData = SpecificData.getForClass(readerType);
        Schema readerSchema = readerSpecificData.getSchema(readerType);

        if (config.performAvroCompatibilityCheck()) {
            schemaIncompatibilityChecker.assertSchemaCompatibility(
                    readerType,
                    readerSchema,
                    writerSchema,
                    config.includeSchemasInStackTraces()
            );
        }

        SpecificRecordBase decoded = (SpecificRecordBase) readerSpecificData.deepCopy(writerSchema,
                                                                                      genericRecord);
        //noinspection unchecked
        return (T) decoded;
    }

    @Override
    public boolean test(@Nonnull Class<?> payloadType) {
        return SpecificRecordBase.class.isAssignableFrom(payloadType);
    }

    @Override
    public void applyStrategyConfiguration(@Nonnull AvroConverterStrategyConfiguration avroConverterConfiguration) {
        assertNonNull(avroConverterConfiguration, "AvroConverterConfiguration must not be null");
        this.config = avroConverterConfiguration;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("avroConverterStrategyConfiguration", this.config);
        descriptor.describeProperty("schemaIncompatibilityChecker", this.schemaIncompatibilityChecker);
        descriptor.describeProperty("schemaStore", this.schemaStore);
    }
}
