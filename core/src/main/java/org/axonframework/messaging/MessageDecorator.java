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

package org.axonframework.messaging;

import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.serialization.SerializationAware;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedObjectHolder;
import org.axonframework.serialization.Serializer;

/**
 * @author Rene de Waele
 */
public abstract class MessageDecorator<T> implements Message<T>, SerializationAware {

    private final Message<T> delegate;
    private transient final SerializedObjectHolder serializedObjectHolder;

    public MessageDecorator(Message<T> delegate) {
        this.delegate = delegate;
        serializedObjectHolder = new SerializedObjectHolder(delegate);
    }

    @Override
    public String getIdentifier() {
        return delegate.getIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return delegate.getMetaData();
    }

    @Override
    public T getPayload() {
        return delegate.getPayload();
    }

    @Override
    public Class<T> getPayloadType() {
        return delegate.getPayloadType();
    }

    @Override
    public <S> SerializedObject<S> serializePayload(Serializer serializer, Class<S> expectedRepresentation) {
        if (delegate instanceof SerializationAware) {
            return ((SerializationAware) delegate).serializePayload(serializer, expectedRepresentation);
        }
        return serializedObjectHolder.serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public <S> SerializedObject<S> serializeMetaData(Serializer serializer, Class<S> expectedRepresentation) {
        if (delegate instanceof SerializationAware) {
            return ((SerializationAware) delegate).serializeMetaData(serializer, expectedRepresentation);
        }
        return serializedObjectHolder.serializeMetaData(serializer, expectedRepresentation);
    }

    protected Message<T> getDelegate() {
        return delegate;
    }
}
