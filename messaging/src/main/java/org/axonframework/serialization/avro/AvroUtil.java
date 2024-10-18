package org.axonframework.serialization.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AvroUtil {

    public static final int MAGIC_BYTE = 0xC3;
    public static final int FORMAT_VERSION = 1;

    // Constant because everything is loaded via SPI
    public static final GenericData genericData = GenericData.get();

    public static long fingerprint(byte[] singleObjectEncodedBytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(singleObjectEncodedBytes)) {
            if (bis.read() != MAGIC_BYTE) {
                throw new IllegalArgumentException("Not a valid single-object avro format, bad magic byte");
            }
            if (bis.read() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Not a valid single-object avro format, bad version byte");
            }

            byte[] fingerprintBytes = new byte[8];
            // TODO: Do we have to check if 8 bytes were read?
            bis.read(fingerprintBytes);
            return ByteBuffer.wrap(fingerprintBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static GenericData fromSpecificData(SpecificData specificData) {
        GenericData genericData = new GenericData();

        specificData.getConversions().forEach(genericData::addLogicalTypeConversion);

        return genericData;
    }

    public static SpecificData fromGenericData(GenericData genericData) {
        SpecificData specificData = new SpecificData();


        genericData.getConversions().forEach(specificData::addLogicalTypeConversion);

        return specificData;
    }

    public static GenericRecord fromSpecificRecord(SpecificRecordBase specificRecord) {
        return fromSpecificData(specificRecord.getSpecificData())
                .deepCopy(specificRecord.getSchema(), specificRecord);
    }

    public static Schema getSchemaFromSpecificRecordBase(Class<?> specificRecordBaseClass) {
        try {
            return (Schema) specificRecordBaseClass.getDeclaredField("SCHEMA$").get(null);
        } catch (Exception e) {
            // TODO meaningful message
            throw new RuntimeException(e);
        }
    }
}
