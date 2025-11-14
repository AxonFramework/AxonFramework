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

package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.MetaDataValue;
import org.axonframework.messaging.core.Metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Mapping that translates a {@link Metadata} into a map of GRPC {@link MetaDataValue}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
@Deprecated(forRemoval = true, since = "5.0.0")
public class GrpcMetadataSerializer implements Function<Metadata, Map<String, MetaDataValue>> {

    private final GrpcMetadataConverter metadataConverter;

    /**
     * Constructs a {@link GrpcMetadataSerializer} using the given {@code metadataConverter}.
     *
     * @param metadataConverter the {@link GrpcMetadataConverter} used to convert meta-data fields with
     */
    public GrpcMetadataSerializer(GrpcMetadataConverter metadataConverter) {
        this.metadataConverter = metadataConverter;
    }

    @Override
    public Map<String, MetaDataValue> apply(Metadata metadata) {
        Map<String, MetaDataValue> metadataValueMap = new HashMap<>();
        metadata.forEach((key, value) -> metadataValueMap.put(key, metadataConverter.convertToMetadataValue(value)));
        return metadataValueMap;
    }
}
