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

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.axonframework.serialization.avro.test.ComplexObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies correct behavior of {@link GenericRecord} {@link org.axonframework.serialization.ContentTypeConverter}s
 * in isolation.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class GenericRecordConverterTest {

    private final SchemaStore.Cache schemaStore = new SchemaStore.Cache();
    private final GenericRecordToByteArrayConverter toByteArrayConverter = new GenericRecordToByteArrayConverter();
    private final ByteArrayToGenericRecordConverter toGenericRecordConverter = new ByteArrayToGenericRecordConverter(schemaStore);

    @BeforeEach
    void setUp() {
        schemaStore.addSchema(ComplexObject.getClassSchema());
    }

    @Test
    void convertBackAndForth() throws IOException {
        final GenericData.Record record = new GenericData.Record(ComplexObject.getClassSchema());
        record.put("value1", "foo");
        record.put("value2", "bar");
        record.put("value3", 4711);

        byte[] singleObjectEncodedBytes = toByteArrayConverter.convert(record);

        final GenericRecord convertedRecord = toGenericRecordConverter.convert(singleObjectEncodedBytes);
        assertEquals(record, convertedRecord);
    }
}
