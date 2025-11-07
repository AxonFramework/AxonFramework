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

import org.apache.avro.message.SchemaStore;
import org.axonframework.conversion.avro.AvroConverter;
import org.axonframework.conversion.avro.AvroConverterConfiguration;
import org.axonframework.conversion.avro.DefaultSchemaIncompatibilityChecker;
import org.axonframework.conversion.avro.SpecificRecordBaseConverterStrategy;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for the avro converter configuration.
 *
 * @author Simon Zambrovski
 * @since 5.0.0
 */
class AvroConverterConfigurationTest {

    @Test
    void buildsConverterFromConfigOverrideFlippingAllValues() {
        var store = new SchemaStore.Cache();
        var schemaIncompatibilitiesChecker = new DefaultSchemaIncompatibilityChecker();
        var converter = new AvroConverter(
                store,
                (c) -> c
                        .addConverterStrategy(
                                new SpecificRecordBaseConverterStrategy(store, schemaIncompatibilitiesChecker)
                        )
                        .includeDefaultAvroConverterStrategies(false)
                        .includeSchemasInStackTraces(true)
                        .performAvroCompatibilityCheck(false)
                        .includeSchemasInStackTraces(true)
                        .schemaIncompatibilityChecker(schemaIncompatibilitiesChecker)
        );
        assertThat(converter).isNotNull();
    }

    @Test
    void configurationMandatoryValues() {
        assertEquals("Schema store cannot be null",
                     assertThrows(NullPointerException.class,
                                  () -> new AvroConverterConfiguration(null)
                     )
                             .getMessage()
        );

        // that should work fine
        assertNotNull(
                new AvroConverterConfiguration(new SchemaStore.Cache())
                        .addConverterStrategy(new SpecificRecordBaseConverterStrategy(
                                new SchemaStore.Cache(),
                                new DefaultSchemaIncompatibilityChecker()
                        ))
                        .includeDefaultAvroConverterStrategies(false)
        );
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void builderSetterContracts() {

        assertEquals("At least one Avro converter strategy is required and no default strategies will be used",
                     assertThrows(IllegalArgumentException.class,
                                  () -> new AvroConverterConfiguration(new SchemaStore.Cache())
                                          .includeDefaultAvroConverterStrategies(false)
                     ).getMessage()
        );

        assertEquals("Avro converter strategy cannot be null",
                     assertThrows(NullPointerException.class,
                                  () -> new AvroConverterConfiguration(new SchemaStore.Cache())
                                          .addConverterStrategy(
                                                  null))
                             .getMessage()
        );

        assertEquals("Schema incompatibility checker cannot be null",
                     assertThrows(NullPointerException.class,
                                  () -> new AvroConverterConfiguration(new SchemaStore.Cache())
                                          .schemaIncompatibilityChecker(
                                                  null))
                             .getMessage()
        );
    }
}
