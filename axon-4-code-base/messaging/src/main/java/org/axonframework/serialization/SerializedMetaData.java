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

package org.axonframework.serialization;

import org.axonframework.messaging.MetaData;

import java.util.Objects;

/**
 * Represents the serialized form of a {@link MetaData} instance.
 *
 * @param <T> The data type representing the serialized object
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializedMetaData<T> implements SerializedObject<T> {

    private static final String METADATA_CLASS_NAME = MetaData.class.getName();

    private final SimpleSerializedObject<T> delegate;

    /**
     * Construct an instance with given {@code bytes} representing the serialized form of a {@link MetaData}
     * instance.
     *
     * @param data     data representing the serialized form of a {@link MetaData} instance.
     * @param dataType The type of data
     */
    public SerializedMetaData(T data, Class<T> dataType) {
        delegate = new SimpleSerializedObject<>(data, dataType, METADATA_CLASS_NAME, null);
    }

    /**
     * Indicates whether the given {@code serializedObject} represents a serialized form of a MetaData object,
     * such as the ones created by this class (see {@link #SerializedMetaData(Object, Class)}.
     *
     * @param serializedObject The object to check for Meta Data
     * @return {@code true} if the serialized objects represents serialized meta data, otherwise
     *         {@code false}.
     */
    public static boolean isSerializedMetaData(SerializedObject<?> serializedObject) {
        return serializedObject != null
                && serializedObject.getType() != null
                && METADATA_CLASS_NAME.equals(serializedObject.getType().getName());
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SerializedMetaData<?> that = (SerializedMetaData<?>) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }
}
