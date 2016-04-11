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

package org.axonframework.serializer;

import org.axonframework.common.Assert;

/**
 * Represents a serialized object that can be deserializedObjects upon request. Typically used as a wrapper class for
 * keeping a SerializedObject and its Serializer together.
 *
 * @param <T> The type of object contained in the serialized object
 * @author Allard Buijze
 * @author Frank Versnel
 * @since 2.0
 */
public class LazyDeserializingObject<T> {

    private final transient Serializer serializer;
    private final SerializedObject<?> serializedObject;
    private final Class<T> deserializedObjectType;
    private volatile transient T deserializedObject;

    /**
     * Creates an instance with the given <code>deserializedObject</code> object instance. Using this constructor will
     * ensure that no deserializing is required when invoking the {@link #getType()} or {@link #getObject()} methods.
     *
     * @param deserializedObject The deserialized object to return on {@link #getObject()}
     */
    @SuppressWarnings("unchecked")
    public LazyDeserializingObject(T deserializedObject) {
        Assert.notNull(deserializedObject, "The given deserialized instance may not be null");
        this.serializedObject = null;
        this.serializer = null;
        this.deserializedObject = deserializedObject;
        this.deserializedObjectType = (Class<T>) deserializedObject.getClass();
    }

    /**
     * @param serializedObject The serialized payload of the message
     * @param serializer       The serializer to deserialize the payload data with
     * @throws UnknownSerializedTypeException if the type of the serialized object cannot be resolved to a class
     */
    @SuppressWarnings("unchecked")
    public LazyDeserializingObject(SerializedObject<?> serializedObject, Serializer serializer) {
        Assert.notNull(serializedObject, "The given serializedObject may not be null");
        Assert.notNull(serializer, "The given serializer may not be null");
        this.serializedObject = serializedObject;
        this.serializer = serializer;
        this.deserializedObjectType = serializer.classForType(serializedObject.getType());
    }

    /**
     * Returns the class of the serialized object.
     *
     * @return the class of the serialized object
     */
    public Class<T> getType() {
        return deserializedObjectType;
    }

    /**
     * De-serializes the object and returns the result.
     *
     * @return the deserialized objects
     */
    @SuppressWarnings("unchecked")
    public T getObject() {
        if (!isDeserialized()) {
            deserializedObject = serializer.deserialize(serializedObject);
        }
        return deserializedObject;
    }

    /**
     * Indicates whether this object has already been deserialized. When this method returns <code>true</code>, the
     * {@link #getObject()} method is able to return a value without invoking the serializer.
     *
     * @return whether the contained object has been deserialized already.
     */
    public boolean isDeserialized() {
        return deserializedObject != null;
    }

    /**
     * Returns the serializer to deserialize this object
     *
     * @return the serializer to deserialize this object
     */
    public Serializer getSerializer() {
        return serializer;
    }

    /**
     * Returns the serialized object to deserialize upon request
     *
     * @return the serialized object to deserialize upon request
     */
    public SerializedObject<?> getSerializedObject() {
        return serializedObject;
    }
}
