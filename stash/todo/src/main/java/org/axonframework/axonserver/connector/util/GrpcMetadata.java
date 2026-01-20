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

package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.MetaDataValue;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.conversion.Serializer;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Implementation that provides access to a {@link Map} of gRPC {@link MetaDataValue}s in the form of {@link Metadata}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
@Deprecated(forRemoval = true, since = "5.0.0")
public class GrpcMetadata implements Supplier<Metadata> {

    private final Map<String, MetaDataValue> metaDataValues;
    private final GrpcMetadataConverter grpcMetaDataConverter;
    private volatile Metadata metaData;

    /**
     * Instantiate a wrapper around the given {@code metaDataValues} providing access to them as a {@link Metadata}
     * object.
     *
     * @param metaDataValues a {@link Map} of {@link String} to {@link MetaDataValue} to wrap as a {@link Metadata}
     * @param serializer     the {@link Serializer} used to deserialize {@link MetaDataValue}s with
     */
    public GrpcMetadata(Map<String, MetaDataValue> metaDataValues, Serializer serializer) {
        this.metaDataValues = metaDataValues;
        this.grpcMetaDataConverter = new GrpcMetadataConverter(serializer);
    }

    @Override
    public Metadata get() {
        if (metaData == null) {
            this.metaData = grpcMetaDataConverter.convert(metaDataValues);
        }
        return metaData;
    }
}
