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

import org.apache.avro.*;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityResult;
import org.apache.avro.generic.GenericData;
import org.apache.avro.message.BadHeaderException;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.axonframework.serialization.SerializationException;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.avro.SchemaCompatibility.checkReaderWriterCompatibility;

/**
 * Utilities for Avro manipulations.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class AvroUtil {

    /**
     * Magic marker bytes, indicating single-objet-encoding as defined in <a
     * href="https://avro.apache.org/docs/1.11.4/specification/#single-object-encoding-specification">Avro
     * specification</a>.
     */
    public static final int MAGIC_BYTE = 0xC3;
    /**
     * Format version of single-objet-encoding as defined in <a
     * href="https://avro.apache.org/docs/1.11.4/specification/#single-object-encoding-specification">Avro
     * specification</a>.
     */
    public static final int FORMAT_VERSION = 0x01;

    /**
     * Length of encoded fingerprint long value.
     */
    public static final int AVRO_FINGERPRINT_LENGTH = Long.BYTES;

    /**
     * Header size of single-object-encoding as defined in <a
     * href="https://avro.apache.org/docs/1.11.4/specification/#single-object-encoding-specification">Avro
     * specification</a>.
     */
    public static final int AVRO_HEADER_LENGTH = AVRO_FINGERPRINT_LENGTH + 2;

    /**
     * Constant utilities for construction of {@link org.apache.avro.generic.GenericRecord} preloaded by default Avro
     * stack.
     */
    public static final GenericData genericData = GenericData.get();

    /**
     * Hide utility class constructor.
     */
    private AvroUtil() {

    }

    /**
     * Reads fingerprint from single object encoded byte array.
     *
     * @param singleObjectEncodedBytes The single object encoded byte array.
     * @return fingerprint of the schema.
     * @throws AvroRuntimeException if fingerprint can not be read from input bytes.
     */
    public static long fingerprint(@Nonnull byte[] singleObjectEncodedBytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(singleObjectEncodedBytes)) {
            int magicByte = bis.read();
            int versionByte = bis.read();

            if (magicByte != MAGIC_BYTE) {
                throw new InvalidAvroMagicException(String.format("Unrecognized header magic byte: 0x%02X", magicByte));
            }
            if (versionByte != FORMAT_VERSION) {
                throw new InvalidNumberEncodingException(String.format("Unrecognized header version bytes: 0x%02X",
                        versionByte));
            }
            byte[] fingerprintBytes = new byte[AVRO_FINGERPRINT_LENGTH];
            int read = bis.read(fingerprintBytes);
            if (read != AVRO_FINGERPRINT_LENGTH) {
                throw new BadHeaderException("Could not read header bytes, end of stream reached at position " + read);
            }
            return ByteBuffer.wrap(fingerprintBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (IOException e) {
            throw new AvroRuntimeException("Could not read fingerprint from byte array");
        }
    }

    /**
     * Reads payload from single object encoded byte array.
     *
     * @param singleObjectEncodedBytes The single object encoded byte array.
     * @return payload bytes.
     * @throws AvroRuntimeException if payload bytes can not be read from input bytes.
     */
    public static byte[] payload(@Nonnull byte[] singleObjectEncodedBytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(singleObjectEncodedBytes)) {
            if (AVRO_HEADER_LENGTH != bis.skip(AVRO_HEADER_LENGTH)) {
                throw new BadHeaderException("Could not read header bytes, end of stream reached.");
            }
            byte[] payloadBytes = new byte[singleObjectEncodedBytes.length - AVRO_HEADER_LENGTH];
            if (bis.read(payloadBytes) != payloadBytes.length) {
                throw new AvroRuntimeException("Could not read payload from byte array - Insufficient payload bytes.");
            }
            return payloadBytes;
        } catch (IOException e) {
            throw new AvroRuntimeException("Could not read payload from byte array");
        }
    }

    /**
     * Creates generic data from specific data.
     *
     * @param specificData specific data.
     * @return generic data containing the same logical type conversions.
     */
    public static GenericData fromSpecificData(SpecificData specificData) {
        GenericData genericData = new GenericData();
        specificData.getConversions().forEach(genericData::addLogicalTypeConversion);
        return genericData;
    }

    /**
     * Retrieves schema from specific record base class.
     *
     * @param specificRecordBaseClass class extending <code>SpecificRecordBase</code>
     * @return schema.
     * @throws AvroRuntimeException on any errors.
     */
    @Nonnull
    public static Schema getClassSchemaChecked(@Nonnull Class<SpecificRecordBase> specificRecordBaseClass) {
        try {
            return getClassSchema(specificRecordBaseClass);
        } catch (Exception e) {
            throw new AvroRuntimeException(
                    "Could not get schema from specific record class " + specificRecordBaseClass.getCanonicalName(), e);
        }
    }

    /**
     * Retrieves schema from specific record base class.
     *
     * @param clazz class extending <code>SpecificRecordBase</code>
     * @return schema.
     * @throws NoSuchMethodException     on wrong class structure.
     * @throws InvocationTargetException on wrong class structure.
     * @throws IllegalAccessException    on wrong class structure.
     */
    @Nonnull
    public static Schema getClassSchema(@Nonnull Class<SpecificRecordBase> clazz)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (Schema) clazz.getMethod("getClassSchema").invoke(null); // invoke static method
    }


    /**
     * Returns a fingerprint based on the given {@code schema}.
     *
     * @param schema A {@link Schema} to return the fingerprint for.
     * @return The fingerprint for the given {@code schema}.
     */
    public static long fingerprint(@Nonnull Schema schema) {
        return SchemaNormalization.parsingFingerprint64(schema);
    }

    /**
     * Checks schema compatibilities and throws exception if schemas are not compatible.
     *
     * @param readerType   intended reader type.
     * @param readerSchema schema available on the reader side.
     * @param writerSchema schema that was used to write the data.
     * @param  schemaStore schema store. If instance of {@link IncompatibilityCachingSchemaStore} is provided, the
     *                     check result is cached.
     * @throws SerializationException if the schema check has not passed.
     */
    public static void assertSchemaCompatibility(@Nonnull Class<?> readerType,
                                                 @Nonnull Schema readerSchema,
                                                 @Nonnull Schema writerSchema,
                                                 @Nonnull SchemaStore schemaStore,
                                                 boolean includeSchemasInStackTraces
    ) throws SerializationException {
        final List<SchemaCompatibility.Incompatibility> incompatibilities;
        if (schemaStore instanceof IncompatibilityCachingSchemaStore) {
            incompatibilities = ((IncompatibilityCachingSchemaStore) schemaStore).checkCompatibility(readerSchema, writerSchema);
        } else {
            incompatibilities = checkCompatibility(readerSchema, writerSchema);
        }
        if (!incompatibilities.isEmpty()) {
            // reader and writer are incompatible
            // this is a fatal error, let provide information for the developer
            String incompatibilitiesMessage = incompatibilities
                    .stream()
                    .map(AvroUtil::incompatibilityPrinter)
                    .collect(Collectors.joining(", "));
            throw createExceptionFailedToDeserialize(
                    readerType,
                    readerSchema,
                    writerSchema,
                    "[" + incompatibilitiesMessage + "]",
                    includeSchemasInStackTraces
            );
        }
    }

    /**
     * Checks compatibility between reader and writer schema.
     * @param readerSchema reader schema.
     * @param writerSchema writer schema.
     * @return list of incompatibilities if any, or empty list if schemas are compatible.
     */
    public static List<SchemaCompatibility.Incompatibility> checkCompatibility(
            @Nonnull Schema readerSchema,
            @Nonnull Schema writerSchema
    ) {
        SchemaCompatibility.SchemaCompatibilityResult schemaPairCompatibilityResult = checkReaderWriterCompatibility(
                readerSchema,
                writerSchema
        ).getResult();
        if (schemaPairCompatibilityResult
                .getCompatibility()
                .equals(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE)
        ) {
            return schemaPairCompatibilityResult.getIncompatibilities();
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Creates a serialization exception for reader type.
     *
     * @param readerType   object type to deserialize.
     * @param readerSchema reader schema.
     * @param writerSchema writer schema.
     * @param cause        the cause of exception.
     * @return serialization exception.
     */
    @Nonnull
    public static SerializationException createExceptionFailedToDeserialize(@Nonnull Class<?> readerType,
                                                                            @Nonnull Schema readerSchema,
                                                                            @Nonnull Schema writerSchema,
                                                                            Exception cause,
                                                                            boolean includeSchemasInStackTraces
    ) {
        return new SerializationException(
                "Failed to deserialize single-object-encoded bytes to instance of "
                        + readerType.getCanonicalName()
                        + ", writer schema fingerprint is " + fingerprint(writerSchema)
                        + (includeSchemasInStackTraces ? ", writer schema fingerprint is " + writerSchema : "")
                        + " reader schema fingerprint is " + fingerprint(readerSchema)
                        + (includeSchemasInStackTraces ? ", reader schema is " + readerSchema : ""),
                cause);
    }

    public static SerializationException createExceptionFailedToDeserialize(@Nonnull Class<?> readerType,
                                                                            @Nonnull Schema readerSchema,
                                                                            @Nonnull Schema writerSchema,
                                                                            String message,
                                                                            boolean includeSchemasInStackTraces
    ) {
        return new SerializationException(
                "Failed to deserialize single-object-encoded bytes to instance of "
                        + readerType.getCanonicalName()
                        + ", writer schema fingerprint is " + fingerprint(writerSchema)
                        + (includeSchemasInStackTraces ? ", writer schema is " + writerSchema : "")
                        + ", reader schema fingerprint is " + fingerprint(readerSchema)
                        + (includeSchemasInStackTraces ? ", reader schema is " + readerSchema : "")
                        + ", detected incompatibilities are: " + message
                        + ". Consider to define an upcaster to fix this problem."
        );
    }

    /**
     * Creates incompatibility string representation.
     *
     * @param incompatibility incompatibility to display.
     * @return string representation.
     */
    public static String incompatibilityPrinter(@Nonnull SchemaCompatibility.Incompatibility incompatibility) {
        return String.format("%s located at \"%s\" with value \"%s\"",
                incompatibility.getType(),
                incompatibility.getLocation(),
                incompatibility.getMessage());
    }

    /**
     * Creates exception if the schema for a given fingerprint could not be found.
     *
     * @param readerType  type of object to deserialize.
     * @param fingerprint fingerprint of writer schema.
     * @return exception to throw.
     */
    public static SerializationException createExceptionNoSchemaFound(
            @Nonnull Class<?> readerType,
            long fingerprint
    ) {
        return new SerializationException("Schema store could didn't contain schema deserializing "
                + readerType
                + " with fingerprint:"
                + fingerprint);
    }
}
