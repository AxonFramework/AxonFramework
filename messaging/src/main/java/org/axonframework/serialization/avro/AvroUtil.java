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

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.InvalidAvroMagicException;
import org.apache.avro.InvalidNumberEncodingException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityResult;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.generic.GenericData;
import org.apache.avro.message.BadHeaderException;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.axonframework.serialization.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

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
     * Header size of single-objet-encoding as defined in <a
     * href="https://avro.apache.org/docs/1.11.4/specification/#single-object-encoding-specification">Avro
     * specification</a>.
     */
    public static final int AVRO_HEADER_LENGTH = 8;

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
     * Reads fingerprint from byte array.
     *
     * @param singleObjectEncodedBytes byte array.
     * @return fingerprint of the schema.
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
            byte[] fingerprintBytes = new byte[AVRO_HEADER_LENGTH];
            int read = bis.read(fingerprintBytes);
            if (read != AVRO_HEADER_LENGTH) {
                throw new BadHeaderException("Could not read header bytes, end of stream reached at position " + read);
            }
            return ByteBuffer.wrap(fingerprintBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (IOException e) {
            throw new AvroRuntimeException("Could not read fingerprint from byte array");
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
     * @throws SerializationException if the schema check has not passed.
     */
    public static void assertSchemaCompatibility(@Nonnull Class<?> readerType,
                                                 @Nonnull Schema readerSchema,
                                                 @Nonnull Schema writerSchema,
                                                 boolean includeSchemasInStackTraces
    ) throws SerializationException {
        SchemaCompatibilityResult schemaPairCompatibilityResult = checkReaderWriterCompatibility(
                readerSchema,
                writerSchema
        ).getResult();

        if (schemaPairCompatibilityResult
                .getCompatibility()
                .equals(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE)
        ) {
            // reader and writer are incompatible
            // this is a fatal error, let provide information for the developer
            String incompatibilitiesMessage = schemaPairCompatibilityResult
                    .getIncompatibilities()
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
