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
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageEncoder;
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.ContentTypeConverter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A {@link ContentTypeConverter} implementation that converts an Avro {@link GenericRecord} into a
 * single-object-encoded {@code byte[]}.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class GenericRecordToByteArrayConverter implements ContentTypeConverter<GenericRecord, byte[]> {

    @Override
    @Nonnull
    public Class<GenericRecord> expectedSourceType() {
        return GenericRecord.class;
    }

    @Override
    @Nonnull
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    @Nullable
    public byte[] convert(@Nullable GenericRecord input) {
        if (input == null) {
            return null;
        }

        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new BinaryMessageEncoder<GenericRecord>(AvroUtil.genericData, input.getSchema())
                    .encode(input, baos);
            baos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ConversionException("Cannot convert GenericRecord to bytes.", e);
        }
    }
}
