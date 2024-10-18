package org.axonframework.serialization.avro;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.InvalidAvroMagicException;
import org.apache.avro.InvalidNumberEncodingException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BadHeaderException;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;

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

    // FIXME -> remove?
    public static SpecificData fromGenericData(GenericData genericData) {
        SpecificData specificData = new SpecificData();
        genericData.getConversions().forEach(specificData::addLogicalTypeConversion);
        return specificData;
    }

    // FIXME -> remove?
    public static GenericRecord fromSpecificRecord(SpecificRecordBase specificRecord) {
        return fromSpecificData(specificRecord.getSpecificData())
            .deepCopy(specificRecord.getSchema(), specificRecord);
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
}
