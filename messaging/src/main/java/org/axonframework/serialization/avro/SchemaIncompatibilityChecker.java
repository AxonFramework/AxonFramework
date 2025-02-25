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

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.axonframework.serialization.SerializationException;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides functionality for incompatibility checks.
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public interface SchemaIncompatibilityChecker {

    /**
     * Checks schema compatibilities and throws exception if schemas are not compatible.
     *
     * @param readerType   intended reader type.
     * @param readerSchema schema available on the reader side.
     * @param writerSchema schema that was used to write the data.
     * @throws SerializationException if the schema check has not passed.
     */
    default void assertSchemaCompatibility(
            @Nonnull Class<?> readerType,
            @Nonnull Schema readerSchema,
            @Nonnull Schema writerSchema,
            boolean includeSchemasInStackTraces) {
        List<SchemaCompatibility.Incompatibility> incompatibilities = checkCompatibility(
                readerSchema, writerSchema
        );
        if (!incompatibilities.isEmpty()) {
            // reader and writer are incompatible
            // this is a fatal error, let provide information for the developer
            String incompatibilitiesMessage = incompatibilities
                    .stream()
                    .map(AvroUtil::incompatibilityPrinter)
                    .collect(Collectors.joining(", "));
            throw AvroUtil.createExceptionFailedToDeserialize(
                    readerType,
                    readerSchema,
                    writerSchema,
                    "[" + incompatibilitiesMessage + "]",
                    includeSchemasInStackTraces
            );
        }
    }

    /**
     * Performs compatibility check.
     * @param readerSchema reader schema to check.
     * @param writerSchema writer schema to check.
     * @return list of compatibilities if any, or empty list
     */
    @Nonnull
    default List<SchemaCompatibility.Incompatibility> checkCompatibility(
            @Nonnull Schema readerSchema,
            @Nonnull Schema writerSchema
    ) {
        return AvroUtil.checkCompatibility(
                readerSchema,
                writerSchema
        );
    }
}
