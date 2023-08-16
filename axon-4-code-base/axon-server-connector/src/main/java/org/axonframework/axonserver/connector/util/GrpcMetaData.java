/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Implementation that provides access to a {@link Map} of gRPC {@link MetaDataValue}s in the form of {@link MetaData}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class GrpcMetaData implements Supplier<MetaData> {

    private final Map<String, MetaDataValue> metaDataValues;
    private final GrpcMetaDataConverter grpcMetaDataConverter;
    private volatile MetaData metaData;

    /**
     * Instantiate a wrapper around the given {@code metaDataValues} providing access to them as a {@link MetaData}
     * object.
     *
     * @param metaDataValues a {@link Map} of {@link String} to {@link MetaDataValue} to wrap as a {@link MetaData}
     * @param serializer     the {@link Serializer} used to deserialize {@link MetaDataValue}s with
     */
    public GrpcMetaData(Map<String, MetaDataValue> metaDataValues, Serializer serializer) {
        this.metaDataValues = metaDataValues;
        this.grpcMetaDataConverter = new GrpcMetaDataConverter(serializer);
    }

    @Override
    public MetaData get() {
        if (metaData == null) {
            this.metaData = grpcMetaDataConverter.convert(metaDataValues);
        }
        return metaData;
    }
}
