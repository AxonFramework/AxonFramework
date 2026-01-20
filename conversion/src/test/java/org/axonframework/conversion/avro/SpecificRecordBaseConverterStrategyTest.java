/*
 * Copyright (c) 2010-2026. Axon Framework
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


import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.avro.test.ComplexObject;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Strategy test.
 *
 * @author Simon Zambrovski
 * @since 5.0.0
 */
class SpecificRecordBaseConverterStrategyTest {

    private final SchemaStore.Cache cache = new SchemaStore.Cache();
    private final SpecificRecordBaseConverterStrategy testSubject = new SpecificRecordBaseConverterStrategy(
            cache,
            new DefaultSchemaIncompatibilityChecker()
    );
    private final ComplexObject testComplexObject = ComplexObject
            .newBuilder()
            .setValue1("value1")
            .setValue2("value2")
            .setValue3(42)
            .build();

    @BeforeEach
    public void clean() {
        cache.addSchema(ComplexObject.getClassSchema());
    }

    @Test
    public void rejectsUnsupportedTypes() {
        assertThat(testSubject.test(Integer.class)).isFalse();

        final byte[] encodedBytes = testSubject.convertToSingleObjectEncoded(testComplexObject);
        assertEquals("Expected reader type to be assignable from SpecificRecordBase but it was java.lang.Integer",
                     assertThrows(ConversionException.class,
                                  () -> testSubject.convertFromSingleObjectEncoded(encodedBytes, Integer.class)
                     ).getMessage()
        );

        final GenericRecord record = testComplexObject;
        assertEquals("Expected reader type to be assignable from SpecificRecordBase but it was java.lang.Integer",
                     assertThrows(ConversionException.class,
                                  () -> testSubject.convertFromGenericRecord(record, Integer.class)
                     ).getMessage()
        );


        assertEquals("Expected object to be instance of SpecificRecordBase but it was java.lang.Integer",
                     assertThrows(ConversionException.class,
                                  () -> testSubject.convertToSingleObjectEncoded(42)
                     ).getMessage()
        );
    }
}