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
import org.apache.avro.SchemaNormalization;
import org.apache.avro.generic.GenericData;
import org.apache.avro.message.BadHeaderException;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.axonframework.serialization.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.annotation.Nonnull;

/**
 * Utilities for Avro manipulations.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class AvroUtil {

    public static final int MAGIC_BYTE = 0xC3;
    public static final int FORMAT_VERSION = 0x01;
    public static final int AVRO_HEADER_LENGTH = 8;

    // Constant because everything is loaded via SPI
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
     */
    @Nonnull
    public static Schema getSchemaFromSpecificRecordBase(@Nonnull Class<SpecificRecordBase> specificRecordBaseClass) {
        try {
            return (Schema) specificRecordBaseClass.getDeclaredField("SCHEMA$").get(null);
        } catch (Exception e) {
            throw new AvroRuntimeException(
                    "Could not get schema from specific record class " + specificRecordBaseClass.getCanonicalName(), e);
        }
    }

    /**
     * Returns a fingerprint for schema.
     *
     * @param schema schema to return the fingerprint for.
     * @return fingerprint.
     */
    public static long fingerprint(@Nonnull Schema schema) {
        return SchemaNormalization.parsingFingerprint64(schema);
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
                                                                            Exception cause) {
        return new SerializationException("Failed to deserialize specific record to instance of "
                                                  + readerType.getCanonicalName()
                                                  + ", writer fp was " + fingerprint(writerSchema)
                                                  + " reader fp was " + fingerprint(readerSchema),
                                          cause);
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
        return new SerializationException("Schema store could not contain schema deserializing "
                                                  + readerType
                                                  + " with fp:"
                                                  + fingerprint);
    }
}
