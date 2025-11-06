/*
 * Copyright (c) 2010-2025. Axon Framework
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
import jakarta.annotation.Nullable;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.message.SchemaStore;
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.ContentTypeConverter;

import java.io.IOException;
import java.util.Objects;

/**
 * A {@link ContentTypeConverter} implementation that converts {@code byte[]} into an Avro {@link GenericRecord}.
 * <p>
 * Searches for the correct Avro {@link Schema} by extracting the {@link AvroUtil#fingerprint(Schema) fingerprint} from
 * the given {@code byte[]}.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class ByteArrayToGenericRecordConverter implements ContentTypeConverter<byte[], GenericRecord> {

    private static final DecoderFactory decoderFactory = DecoderFactory.get();

    private final SchemaStore schemaStore;

    /**
     * Constructs a content type converter used during deserialization for upcasting, to create {@link GenericRecord}
     * from single-object-encoded for a given schema.
     *
     * @param schemaStore The schema store to resolve schemas with based on a fingerprint.
     */
    public ByteArrayToGenericRecordConverter(@Nonnull SchemaStore schemaStore) {
        this.schemaStore = Objects.requireNonNull(schemaStore, "The SchemaStore may not be null.");
    }

    @Override
    @Nonnull
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    @Nonnull
    public Class<GenericRecord> targetType() {
        return GenericRecord.class;
    }

    @Override
    @Nullable
    public GenericRecord convert(@Nullable byte[] input) {
        if (input == null) {
            return null;
        }

        long fingerprint = AvroUtil.fingerprint(input);
        Schema writerSchema = schemaStore.findByFingerprint(fingerprint);
        GenericDatumReader<GenericRecord> reader =
                new GenericDatumReader<>(writerSchema, writerSchema, AvroUtil.genericData);

        try {
            return reader.read(null, decoderFactory.binaryDecoder(AvroUtil.payload(input), null));
        } catch (IOException e) {
            throw new ConversionException("Cannot convert bytes to GenericRecord.", e);
        }
    }
}
