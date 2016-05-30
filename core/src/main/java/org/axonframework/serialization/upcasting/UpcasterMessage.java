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

package org.axonframework.serialization.upcasting;

import org.axonframework.messaging.AbstractMessage;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Rene de Waele
 */
public class UpcasterMessage<T> extends AbstractMessage<T> {

    private final LazyDeserializingObject<MetaData> serializedMetaData;
    private final transient Supplier<LazyDeserializingObject<T>> upcaster;
    private volatile transient LazyDeserializingObject<T> serializedPayload;

    public UpcasterMessage(String identifier,
                           LazyDeserializingObject<MetaData> serializedMetaData,
                           Supplier<LazyDeserializingObject<T>> upcaster) {
        super(identifier);
        this.serializedMetaData = serializedMetaData;
        this.upcaster = upcaster;
    }

    private UpcasterMessage(UpcasterMessage<T> message, LazyDeserializingObject<MetaData> serializedMetaData) {
        this(message.getIdentifier(), serializedMetaData, message.upcaster);
        serializedPayload = message.serializedPayload;
    }

    @Override
    public T getPayload() {
        return getSerializedPayload().getObject();
    }

    @Override
    public MetaData getMetaData() {
        return serializedMetaData.getObject();
    }

    @Override
    public Class<T> getPayloadType() {
        return getSerializedPayload().getType();
    }

    @Override
    protected UpcasterMessage<T> withMetaData(MetaData metaData) {
        Serializer serializer = serializedMetaData.getSerializer();
        metaData = metaData.mergedWith(getMetaData());
        SerializedObject<?> serializedMetaData = serializer
                .serialize(metaData, this.serializedMetaData.getSerializedObject().getContentType());
        return new UpcasterMessage<>(this, new LazyDeserializingObject<>(serializedMetaData, serializer));
    }

    @Override
    public UpcasterMessage<T> withMetaData(Map<String, ?> metaData) {
        return (UpcasterMessage<T>) super.withMetaData(metaData);
    }

    @Override
    public UpcasterMessage<T> andMetaData(Map<String, ?> metaData) {
        return (UpcasterMessage<T>) super.andMetaData(metaData);
    }

    protected LazyDeserializingObject<T> getSerializedPayload() {
        if (serializedPayload == null) {
            serializedPayload = upcaster.get();
        }
        return serializedPayload;
    }

    protected LazyDeserializingObject<MetaData> getSerializedMetaData() {
        return serializedMetaData;
    }

    protected Supplier<LazyDeserializingObject<T>> getUpcaster() {
        return upcaster;
    }
}
