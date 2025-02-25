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

import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageEncoder;
import org.axonframework.serialization.CannotConvertBetweenTypesException;
import org.axonframework.serialization.ContentTypeConverter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.axonframework.serialization.avro.AvroUtil.genericData;

/**
 * Content type converter between Avro generic record and single-object-encoded bytes.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class GenericRecordToByteArrayConverter implements ContentTypeConverter<GenericRecord, byte[]> {

    @Override
    public Class<GenericRecord> expectedSourceType() {
        return GenericRecord.class;
    }

    @Override
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    public byte[] convert(GenericRecord genericRecord) {

        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new BinaryMessageEncoder<GenericRecord>(AvroUtil.genericData, genericRecord.getSchema()).encode(genericRecord, baos);
            baos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new CannotConvertBetweenTypesException("Cannot convert GenericRecord to bytes", e);
        }
    }
}
