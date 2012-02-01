/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serializer;

import org.axonframework.domain.MetaData;

/**
 * Represents the serialized form of a {@link MetaData} instance.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializedMetaData<T> implements SerializedObject<T> {

    private final SimpleSerializedObject<T> delegate;

    /**
     * Construct an instance with given <code>bytes</code> representing the serialized form of a {@link MetaData}
     * instance.
     *
     * @param data data representing the serialized form of a {@link MetaData} instance.
     * @param dataType The type of data
     */
    public SerializedMetaData(T data, Class<T> dataType) {
        delegate = new SimpleSerializedObject<T>(data, dataType, MetaData.class.getName(), -1);
    }

    @Override
    public T getData() {
        return delegate.getData();
    }

    @Override
    public Class<T> getContentType() {
        return delegate.getContentType();
    }

    @Override
    public SerializedType getType() {
        return delegate.getType();
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SerializedMetaData that = (SerializedMetaData) o;

        if (!delegate.equals(that.delegate)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
