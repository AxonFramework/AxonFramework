package org.axonframework.serialization.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.message.SchemaStore;
import org.axonframework.serialization.CannotConvertBetweenTypesException;
import org.axonframework.serialization.ContentTypeConverter;

import java.io.IOException;

/**
 * Content type converter between single-object-encoded bytes and Avro generic record.
 */
public class ByteArrayToGenericRecordConverter implements ContentTypeConverter<byte[], GenericRecord> {

    private static final DecoderFactory decoderFactory = DecoderFactory.get();

    private final SchemaStore schemaStore;

    public ByteArrayToGenericRecordConverter(SchemaStore schemaStore) {
        this.schemaStore = schemaStore;
    }

    @Override
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    public Class<GenericRecord> targetType() {
        return GenericRecord.class;
    }

    @Override
    public GenericRecord convert(byte[] singleObjectEncodeBytes) {
        long fingerprint = AvroUtil.fingerprint(singleObjectEncodeBytes);
        Schema writerSchema = schemaStore.findByFingerprint(fingerprint);
        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(writerSchema, writerSchema, AvroUtil.genericData);

        try {
            return reader.read(null, decoderFactory.binaryDecoder(singleObjectEncodeBytes, null));
        } catch (IOException e) {
            throw new CannotConvertBetweenTypesException("Cannot convert bytes to GenericRecord", e);
        }
    }
}
