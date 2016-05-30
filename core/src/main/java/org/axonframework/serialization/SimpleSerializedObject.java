/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.serialization;

import org.axonframework.common.Assert;

import static java.lang.String.format;

/**
 * SerializedObject implementation that takes all properties as constructor parameters.
 *
 * @param <T> The data type representing the serialized object
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleSerializedObject<T> implements SerializedObject<T> {

    private final T data;
    private final SerializedType type;
    private final Class<T> dataType;

    /**
     * Initializes a SimpleSerializedObject using given <code>data</code> and <code>serializedType</code>.
     *
     * @param data           The data of the serialized object
     * @param dataType       The type of data
     * @param serializedType The type description of the serialized object
     */
    public SimpleSerializedObject(T data, Class<T> dataType, SerializedType serializedType) {
        Assert.notNull(data, "Data for a serialized object cannot be null");
        Assert.notNull(serializedType, "The type identifier of the serialized object");
        this.data = data;
        this.dataType = dataType;
        this.type = serializedType;
    }

    /**
     * Initializes a SimpleSerializedObject using given <code>data</code> and a serialized type identified by given
     * <code>type</code> and <code>revision</code>.
     *
     * @param data     The data of the serialized object
     * @param dataType The type of data
     * @param type     The type identifying the serialized object
     * @param revision The revision of the serialized object
     */
    public SimpleSerializedObject(T data, Class<T> dataType, String type, String revision) {
        this(data, dataType, new SimpleSerializedType(type, revision));
    }

    @Override
    public T getData() {
        return data;
    }

    @Override
    public Class<T> getContentType() {
        return dataType;
    }

    @Override
    public SerializedType getType() {
        return type;
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

        SimpleSerializedObject that = (SimpleSerializedObject) o;

        if (data != null ? !data.equals(that.data) : that.data != null) {
            return false;
        }
        if (dataType != null ? !dataType.equals(that.dataType) : that.dataType != null) {
            return false;
        }
        if (type != null ? !type.equals(that.type) : that.type != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = data != null ? data.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (dataType != null ? dataType.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return format("SimpleSerializedObject [%s]", type);
    }
}
