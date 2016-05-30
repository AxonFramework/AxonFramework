/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.axon3;

import org.axonframework.messaging.AbstractMessage;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;

/**
 * @author Rene de Waele
 */
public class SerializedMessage<T> extends AbstractMessage<T> {

    private LazyDeserializingObject<T> serializedPayload;
    private LazyDeserializingObject<MetaData> serializedMetaData;

    public SerializedMessage(String identifier, LazyDeserializingObject<T> serializedPayload,
                             LazyDeserializingObject<MetaData> serializedMetaData) {
        super(identifier);
        this.serializedPayload = serializedPayload;
        this.serializedMetaData = serializedMetaData;
    }

    @Override
    protected SerializedMessage<T> withMetaData(MetaData metaData) {
        Serializer serializer = serializedMetaData.getSerializer();
        metaData = metaData.mergedWith(getMetaData());
        SerializedObject<?> serializedMetaData = serializer
                .serialize(metaData, this.serializedMetaData.getSerializedObject().getContentType());
        return new SerializedMessage<>(getIdentifier(), serializedPayload,
                                       new LazyDeserializingObject<>(serializedMetaData, serializer));
    }

    @Override
    public SerializedMessage<T> withMetaData(Map<String, ?> metaData) {
        return (SerializedMessage<T>) super.withMetaData(metaData);
    }

    @Override
    public SerializedMessage<T> andMetaData(Map<String, ?> metaData) {
        return (SerializedMessage<T>) super.andMetaData(metaData);
    }

    @Override
    public MetaData getMetaData() {
        return serializedMetaData.getObject();
    }

    @Override
    public T getPayload() {
        return serializedPayload.getObject();
    }

    @Override
    public Class<T> getPayloadType() {
        return serializedPayload.getType();
    }

    protected LazyDeserializingObject<T> getSerializedPayload() {
        return serializedPayload;
    }

    protected LazyDeserializingObject<MetaData> getSerializedMetaData() {
        return serializedMetaData;
    }
}
