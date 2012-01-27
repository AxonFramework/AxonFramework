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
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private transient final SerializedObject serializedObject;

    private transient final Serializer payloadSerializer;

    private transient volatile List<Class> deserializedObjectTypes;

    private volatile List<T> deserializedObjects;
    /**
     * Creates an instance with the given <code>deserializedObject</code> object instance. Using this constructor will
     * ensure
     * that no deserialization is required when invoking the {@link #getType()} or {@link #getObject()} methods.
     *
     * @param deserializedObject The deserialized object to return as a singleton list on {@link #getObject()}
     */
    public LazyDeserializingObject(T deserializedObject) {
        this(Collections.singletonList(deserializedObject));
    }

    /**
     * Creates an instance with the given <code>deserializedObjects</code> object instances. Using this constructor will
     * ensure
     * that no deserialization is required when invoking the {@link #getType()} or {@link #getObject()} methods.
     *
     * @param deserializedObjects The deserialized objects to return on {@link #getObject()}
     */
    public LazyDeserializingObject(List<T> deserializedObjects) {
        Assert.notNull(deserializedObjects, "The given deserializedObjects instance may not be null");
        serializedObject = null;
        payloadSerializer = null;
        this.deserializedObjects = deserializedObjects;
        this.deserializedObjectTypes = extractTypes(deserializedObjects);
    }

    /**
     * @param serializedObject The serialized payload of the message
     * @param serializer       The serializer to deserialize the payload data with
     */
    public LazyDeserializingObject(SerializedObject serializedObject, Serializer serializer) {
        Assert.notNull(serializedObject, "The given serializedObject may not be null");
        Assert.notNull(serializer, "The given serializer may not be null");
        this.serializedObject = serializedObject;
        this.payloadSerializer = serializer;
    }

    /**
     * Returns the class of the serialized object, or <code>null</code> if the no serialized object or deserializer was
     * provided.
     *
     * @return the class of the serialized object
     */
    public List<Class> getType() {
        if (deserializedObjectTypes == null) {
            deserializedObjectTypes = payloadSerializer.classForType(serializedObject.getType());
        }
        return deserializedObjectTypes;
    }

    /**
     * Returns the number of objects that this serialized object consists of when it is deserialized.
     *
     * @return the number of objects that this serialized object consists of when it is deserialized
     */
    public int deserializedObjectCount() {
        return getType().size();
    }

    /**
     * Deserializes the object and returns the result.
     *
     * @return the deserialized objects
     */
    @SuppressWarnings("unchecked")
    public List<T> getObject() {
        if (!isDeserialized()) {
            deserializedObjects = (List<T>) payloadSerializer.deserialize(serializedObject);
        }
        if (deserializedObjectTypes == null && deserializedObjects != null) {
            deserializedObjectTypes = extractTypes(deserializedObjects);
        }
        return deserializedObjects;
    }

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        // make sure the contained object is deserializedObjects
        getObject();
        outputStream.defaultWriteObject();
    }

    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        deserializedObjectTypes = extractTypes(deserializedObjects);
    }

    private List<Class> extractTypes(List<T> deserializedObjects) {
        List<Class> extractedTypes = new ArrayList<Class>();
        for (T deserializedObject : deserializedObjects) {
            extractedTypes.add(deserializedObject.getClass());
        }
        return extractedTypes;
    }

    /**
     * Indicates whether this object has already been deserialized. When this method returns <code>true</code>, the
     * {@link #getObject()} method is able to return a value without invoking the serializer.
     *
     * @return whether the contained object has been deserialized already.
     */
    public boolean isDeserialized() {
        return deserializedObjects != null;
    }

    public Serializer getPayloadSerializer() {
        return payloadSerializer;
    }
}
