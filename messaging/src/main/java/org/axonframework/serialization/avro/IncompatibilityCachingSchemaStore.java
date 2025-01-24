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
import org.apache.avro.message.SchemaStore;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Caching schema store capable to cache incompatibility checks.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class IncompatibilityCachingSchemaStore extends SchemaStore.Cache {

    private final ConcurrentHashMap<Pair<Long, Long>, List<SchemaCompatibility.Incompatibility>> incompatibilitiesCache = new ConcurrentHashMap<>();

    /**
     * Checks the compatibility between reader and writer schema and caches the result.
     *
     * @param readerSchema reader schema.
     * @param writerSchema writer schema.
     * @return list of incompatibilities if any, or empty list if schemas are compatible.
     */
    public List<SchemaCompatibility.Incompatibility> checkCompatibility(
            @Nonnull Schema readerSchema,
            @Nonnull Schema writerSchema
    ) {
        return incompatibilitiesCache.computeIfAbsent(
                Pair.of(AvroUtil.fingerprint(readerSchema), AvroUtil.fingerprint(writerSchema)),
                (key) -> AvroUtil.checkCompatibility(readerSchema, writerSchema)
        );
    }

    /**
     * Retrieves a list of incompatibilities cased so far.
     * Visible for testing.
     * @return copy of immutability map.
     */
    Map<Pair<Long, Long>, List<SchemaCompatibility.Incompatibility>> getIncompatibilitiesCache() {
        HashMap<Pair<Long, Long>, List<SchemaCompatibility.Incompatibility>> copy = new HashMap<>(incompatibilitiesCache.size());
        copy.putAll(incompatibilitiesCache);
        return copy;
    }
}
