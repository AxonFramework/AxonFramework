package org.axonframework.serialization.avro;

import org.apache.avro.*;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BadHeaderException;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.axonframework.serialization.SerializationException;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AvroUtil {

    public static final int MAGIC_BYTE = 0xC3;
    public static final int FORMAT_VERSION = 0x01;
    public static final int AVRO_HEADER_LENGTH = 8;

    // Constant because everything is loaded via SPI
    public static final GenericData genericData = GenericData.get();

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
                throw new InvalidNumberEncodingException(String.format("Unrecognized header version bytes: 0x%02X", versionByte));
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
            throw new AvroRuntimeException("Could not get schema from specific record class " + specificRecordBaseClass.getCanonicalName(), e);
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
     * @param readerType object type to deserialize.
     * @param readerSchema reader schema.
     * @param writerSchema writer schema.
     * @param cause the cause of exception.
     * @return serialization exception.
     */
    @Nonnull
    public static SerializationException createSerializationException(@Nonnull Class<?> readerType,
                                                                      @Nonnull Schema readerSchema,
                                                                      @Nonnull Schema writerSchema,
                                                                      Exception cause) {
        return new SerializationException("Failed to deserialize specific record to instance of "
            + readerType.getCanonicalName()
            + ", writer fp was " + fingerprint(writerSchema)
            + " reader fp was " + fingerprint(readerSchema),
            cause);
    }
}
