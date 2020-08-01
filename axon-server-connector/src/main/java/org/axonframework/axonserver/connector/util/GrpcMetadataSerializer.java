/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.messaging.MetaData;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Mapping that translates a {@link MetaData} into a map of GRPC {@link MetaDataValue}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcMetadataSerializer implements Function<MetaData, Map<String, MetaDataValue>> {

    private final GrpcMetaDataConverter metaDataConverter;

    /**
     * Constructs a {@link GrpcMetadataSerializer} using the given {@code metaDataConverter}.
     *
     * @param metaDataConverter the {@link GrpcMetaDataConverter} used to convert meta-data fields with
     */
    public GrpcMetadataSerializer(GrpcMetaDataConverter metaDataConverter) {
        this.metaDataConverter = metaDataConverter;
    }

    @Override
    public Map<String, MetaDataValue> apply(MetaData metaData) {
        Map<String, MetaDataValue> metaDataValueMap = new HashMap<>();
        metaData.forEach((key, value) -> metaDataValueMap.put(key, metaDataConverter.convertToMetaDataValue(value)));
        return metaDataValueMap;
    }
}
