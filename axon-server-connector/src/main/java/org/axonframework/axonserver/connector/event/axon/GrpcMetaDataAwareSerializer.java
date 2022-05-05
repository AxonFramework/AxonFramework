/*
 * Copyright (c) 2010-2022. Axon Framework
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
package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.grpc.MetaDataValue;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Wrapper around standard Axon Framework serializer that can deserialize Metadata from AxonServer events.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public final class GrpcMetaDataAwareSerializer implements Serializer {

    private final Serializer delegate;
    private final GrpcMetaDataConverter metaDataConverter;

    /**
     * Constructs a {@link GrpcMetaDataAwareSerializer}, using the given {@code delegate} to delegate serialization to
     *
     * @param delegate the {@link Serializer} to delegate serialization to
     */
    public GrpcMetaDataAwareSerializer(Serializer delegate) {
        this.metaDataConverter = new GrpcMetaDataConverter(delegate);
        this.delegate = delegate;
    }

    @Override
    public <T> SerializedObject<T> serialize(Object object, @Nonnull Class<T> expectedRepresentation) {
        return delegate.serialize(object, expectedRepresentation);
    }

    @Override
    public <T> boolean canSerializeTo(@Nonnull Class<T> expectedRepresentation) {
        return delegate.canSerializeTo(expectedRepresentation);
    }

    @Override
    public <S, T> T deserialize(@Nonnull SerializedObject<S> serializedObject) {
        if (Map.class.equals(serializedObject.getContentType())) {
            // this is the MetaDataMap, deserialize differently
            //noinspection unchecked
            Map<String, MetaDataValue> metaDataMap = (Map<String, MetaDataValue>) serializedObject.getData();

            //noinspection unchecked
            return (T) metaDataConverter.convert(metaDataMap);
        }
        return delegate.deserialize(serializedObject);
    }

    @Override
    public Class classForType(@Nonnull SerializedType type) {
        return delegate.classForType(type);
    }

    @Override
    public SerializedType typeForClass(Class type) {
        return delegate.typeForClass(type);
    }

    @Override
    public Converter getConverter() {
        return delegate.getConverter();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GrpcMetaDataAwareSerializer)) {
            return false;
        }
        GrpcMetaDataAwareSerializer that = (GrpcMetaDataAwareSerializer) o;
        return delegate.equals(that.delegate) && metaDataConverter.equals(that.metaDataConverter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, metaDataConverter);
    }
}
