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
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.axonframework.serialization.*;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Avro serializer strategy responsible for operations on <code>SpecificRecordBase</code>.
 */
public class SpecificRecordBaseSerializerStrategy implements AvroSerializerStrategy {

    private final SchemaStore schemaStore;
    private final RevisionResolver revisionResolver;

    public SpecificRecordBaseSerializerStrategy(
        SchemaStore schemaStore,
        RevisionResolver revisionResolver
    ) {
        this.schemaStore = schemaStore;
        this.revisionResolver = revisionResolver;
    }

    @Override
    @Nonnull
    public SerializedObject<byte[]> serializeToSingleObjectEncoded(@Nonnull Object object) {
        if (!(object instanceof SpecificRecordBase)) {
            throw new SerializationException("Expected object to be instance of SpecificRecordBase but it was " + object.getClass().getCanonicalName());
        }

        SpecificRecordBase record = (SpecificRecordBase) object;
        BinaryMessageEncoder<SpecificRecordBase> encoder = new BinaryMessageEncoder<>(record.getSpecificData(), record.getSchema());
        final byte[] bytes;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            encoder.encode(record, outputStream);
            bytes = outputStream.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize specific record", e);
        }

        return new SimpleSerializedObject<>(bytes, byte[].class, new SimpleSerializedType(
            object.getClass().getCanonicalName(),
            revisionResolver.revisionOf(object.getClass())
        ));
    }

    @Override
    @Nonnull
    public <T> T deserializeFromSingleObjectEncoded(@Nonnull SerializedObject<byte[]> serializedObject, @Nonnull Class<T> readerType) {
        if (!SpecificRecordBase.class.isAssignableFrom(readerType)) {
            throw new SerializationException("Expected reader type to be assignable from SpecificRecordBase but it was " + readerType.getCanonicalName());
        }
        @SuppressWarnings("unchecked")
        Class<SpecificRecordBase> specificRecordBaseClass = (Class<SpecificRecordBase>) readerType;

        long fingerprint = AvroUtil.fingerprint(serializedObject.getData());
        Schema writerSchema = schemaStore.findByFingerprint(fingerprint);
        if (writerSchema == null) {
            throw AvroUtil.createExceptionNoSchemaFound(readerType, fingerprint);
        }

        SpecificData readerSpecificData = SpecificData.getForClass(specificRecordBaseClass);
        Schema readerSchema = AvroUtil.getSchemaFromSpecificRecordBase(specificRecordBaseClass);

        // TODO: check reader and writer compatibility here

        BinaryMessageDecoder<SpecificRecordBase> decoder = new BinaryMessageDecoder<SpecificRecordBase>(readerSpecificData, readerSchema);
        decoder.addSchema(writerSchema);

        try {
            //noinspection unchecked
            return (T) decoder.decode(serializedObject.getData());
        } catch (IOException | AvroRuntimeException e) {
            throw AvroUtil.createExceptionFailedToDeserialize(readerType, readerSchema, writerSchema, e);
        }
    }

    @Override
    @Nonnull
    public <T> T deserializeFromGenericRecord(@Nonnull SerializedObject<GenericRecord> serializedObject, @Nonnull Class<T> readerType) {
        if (!SpecificRecordBase.class.isAssignableFrom(readerType)) {
            throw new SerializationException("Expected reader type to be assignable from SpecificRecordBase but it was " + readerType.getCanonicalName());
        }

        Schema writerSchema = serializedObject.getData().getSchema();
//            // TODO if caller does not provide expected type, we use the writer schema to derive the class ... this could be wrong.
//            val readerClass: Class<*> = AvroKotlin.specificData.getClass(writerSchema)

        SpecificData readerSpecificData = SpecificData.getForClass(readerType);
        SpecificRecordBase decoded = (SpecificRecordBase) readerSpecificData.deepCopy(writerSchema, serializedObject.getData());
        //noinspection unchecked
        return (T) decoded;
    }

    @Override
    public boolean test(@Nonnull Class<?> payloadType) {
        return SpecificRecordBase.class.isAssignableFrom(payloadType);
    }

}
