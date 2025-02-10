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
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides functionality for incompatibility checks and saves the results in a in memory cache.
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class DefaultSchemaIncompatibilityChecker implements SchemaIncompatibilityChecker {

    private final ConcurrentHashMap<Pair<Long, Long>, List<SchemaCompatibility.Incompatibility>> cache
            = new ConcurrentHashMap<>();

    @Nonnull
    @Override
    public List<SchemaCompatibility.Incompatibility> checkCompatibility(
            @Nonnull Schema readerSchema,
            @Nonnull Schema writerSchema
    ) {
        return cache.computeIfAbsent(
                Pair.of(AvroUtil.fingerprint(readerSchema), AvroUtil.fingerprint(writerSchema)),
                (key) -> SchemaIncompatibilityChecker.super
                        .checkCompatibility(readerSchema, writerSchema)
        );
    }

    /**
     * Retrieves a list of incompatibilities cached so far.
     * Visible for testing.
     * @return copy of immutability map.
     */
    Map<Pair<Long, Long>, List<SchemaCompatibility.Incompatibility>> getIncompatibilitiesCache() {
        HashMap<Pair<Long, Long>, List<SchemaCompatibility.Incompatibility>> copy = new HashMap<>(cache.size());
        copy.putAll(cache);
        return copy;
    }

    /**
     * Clears the cache.
     */
    void clear() {
        cache.clear();
    }
}
