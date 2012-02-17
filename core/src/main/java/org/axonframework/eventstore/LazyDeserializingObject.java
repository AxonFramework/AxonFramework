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

package org.axonframework.eventstore;

import org.axonframework.common.Assert;
import org.axonframework.serializer.Serializer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Represents a serialized object that can be deserializedObjects upon request. Typically used as a wrapper class for keeping
 * a SerializedObject and its Serializer together.
 *
 * @param <T> The type of object contained in the serialized object
 * @author Allard Buijze
 * @author Frank Versnel
 * @since 2.0
 */
public class LazyDeserializingObject<T> implements Serializable {

    private static final long serialVersionUID = -5533042142349963796L;
    
    private transient Serializer serializer;
    private transient LazyUpcastingObject serializedObject;

    private int indexOnDeserializedObject;
    private volatile T deserializedObject;
    private transient volatile Class deserializedObjectType;

    /**
     * Creates an instance with the given <code>deserializedObject</code> object instance. Using this constructor will
     * ensure
     * that no deserialization is required when invoking the {@link #getType()} or {@link #getObject()} methods.
     *
     * @param deserializedObject The deserialized object to return on {@link #getObject()}
     */
    public LazyDeserializingObject(T deserializedObject) {
        Assert.notNull(deserializedObject, "The given deserialized instance may not be null");
        serializedObject = null;
        serializer = null;
        this.deserializedObject = deserializedObject;
        this.deserializedObjectType = deserializedObject.getClass();
    }

    /**
     * @param serializedObject The serialized payload of the message
     * @param serializer       The serializer to deserialize the payload data with
     * @param indexOnDeserializedObject the index on the deserialized object
     */
    public LazyDeserializingObject(LazyUpcastingObject serializedObject, Serializer serializer,
                                   int indexOnDeserializedObject) {
        Assert.notNull(serializedObject, "The given serializedObject may not be null");
        Assert.notNull(serializer, "The given serializer may not be null");
        this.serializedObject = serializedObject;
        this.serializer = serializer;
        this.indexOnDeserializedObject = indexOnDeserializedObject;
    }

    /**
     * Returns the class of the serialized object, or <code>null</code> if the no serialized object or deserializer was
     * provided.
     *
     * @return the class of the serialized object
     */
    public Class getType() {
        if (deserializedObjectType == null) {
            deserializedObjectType = serializer.classForType(serializedObject.getTypes().get(indexOnDeserializedObject));
        }
        return deserializedObjectType;
    }

    /**
     * Deserializes the object and returns the result.
     *
     * @return the deserialized objects
     */
    @SuppressWarnings("unchecked")
    public T getObject() {
        if (!isDeserialized()) {
            deserializedObject = (T) serializer.deserialize(serializedObject.getObjects().get(indexOnDeserializedObject));
        }
        if (deserializedObjectType == null && deserializedObject != null) {
            deserializedObjectType = extractType(deserializedObject);
        }
        return deserializedObject;
    }

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        // make sure the contained object is deserializedObjects
        getObject();
        outputStream.defaultWriteObject();
    }

    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        deserializedObjectType = extractType(deserializedObject);
    }

    private Class extractType(T deserializedObject) {
        return deserializedObject.getClass();
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

}
