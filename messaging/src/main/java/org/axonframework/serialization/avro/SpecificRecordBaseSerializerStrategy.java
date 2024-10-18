package org.axonframework.serialization.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SpecificRecordBaseSerializerStrategy implements AvroSerializerStrategy {

    private final SchemaStore schemaStore;

    public SpecificRecordBaseSerializerStrategy(SchemaStore schemaStore) {
        this.schemaStore = schemaStore;
    }

    @Override
    public SerializedObject<byte[]> serializeToSingleObjectEncoded(Object object) {
        SpecificRecordBase record = (SpecificRecordBase) object;

        final byte[] bytes;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            new BinaryMessageEncoder<SpecificRecordBase>(record.getSpecificData(), record.getSchema())
                    .encode(record, outputStream);
            bytes = outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        return new SimpleSerializedObject<>(bytes, byte[].class, new SimpleSerializedType(
                object.getClass().getCanonicalName(),
                "0" // FIXME: resolve
        ));
    }

    @Override
    public Object deserializeFromSingleObjectEncoded(SerializedObject<byte[]> serializedObject, Class<?> readerType) {
        long fingerprint = AvroUtil.fingerprint(serializedObject.getData());
        Schema writerSchema = schemaStore.findByFingerprint(fingerprint);
        // TODO: we could check if classes match :
        // SpecificData writerSpecificData = SpecificData.getForSchema(writerSchema);
        // val readerClass = writerSpecificData.getClass(writerSchema.get()) as Class<SpecificRecordBase>
        // TODO compatibility check: required or done by decoder?
        SpecificData readerSpecificData = SpecificData.getForClass(readerType);

        Schema readerSchema = AvroUtil.getSchemaFromSpecificRecordBase(readerType);

        BinaryMessageDecoder<SpecificRecordBase> decoder = new BinaryMessageDecoder<SpecificRecordBase>(readerSpecificData, readerSchema);
        decoder.addSchema(writerSchema);

        try {
            return decoder.decode(serializedObject.getData());
        } catch (IOException e) {
            // TODO meaningful message
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object deserializeFromGenericRecord(SerializedObject<GenericRecord> serializedObject, Class<?> readerType) {
        Schema writerSchema = serializedObject.getData().getSchema();
//            // TODO if caller does not provide expected type, we use the writer schema to derive the class ... this could be wrong.
//            val readerClass: Class<*> = AvroKotlin.specificData.getClass(writerSchema)

        SpecificData readerSpecificData = SpecificData.getForClass(readerType);
        SpecificRecordBase decoded = (SpecificRecordBase) readerSpecificData.deepCopy(writerSchema, serializedObject.getData());

        return decoded;
    }

    @Override
    public boolean test(Class<?> payloadType) {
        return SpecificRecordBase.class.isAssignableFrom(payloadType);
    }

}
