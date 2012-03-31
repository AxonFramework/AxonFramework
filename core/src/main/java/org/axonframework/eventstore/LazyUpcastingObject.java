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

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.upcasting.UpcasterChain;

import java.util.List;

/**
 * Upcasts a given SerializedObject using a given UpcasterChain as soon as {@link #getObjects()} is called.
 *
 * @author Frank Versnel
 * @since 2.0
 */
public class LazyUpcastingObject {

    private UpcasterChain upcasterChain;

    private SerializedObject serializedObject;

    private List<SerializedType> upcastedTypes;
    private List<SerializedObject> upcastedObjects;

    /**
     * @param upcasterChain    the upcasters to use for upcasting the given serializedObject
     * @param serializedObject the serialized object to upcast
     */
    public LazyUpcastingObject(UpcasterChain upcasterChain, SerializedObject serializedObject) {
        this.upcastedTypes = null;
        this.upcastedObjects = null;
        this.upcasterChain = upcasterChain;
        this.serializedObject = serializedObject;
    }

    /**
     * Returns the upcasted serialized objects.
     *
     * @return the upcasted serialized objects
     */
    public List<SerializedObject> getObjects() {
        if (upcastedObjects == null) {
            upcastedObjects = upcasterChain.upcast(serializedObject);
        }
        return upcastedObjects;
    }

    /**
     * Returns the upcasted serialized types.
     *
     * @return the upcasted serialized types
     */
    public List<SerializedType> getTypes() {
        if (upcastedTypes == null) {
            upcastedTypes = upcasterChain.upcast(serializedObject.getType());
        }
        return upcastedTypes;
    }

    /**
     * Returns the number of objects that the serialized object consists of when it is upcasted.
     *
     * @return the number of objects that the serialized object consists of when it is upcasted
     */
    public int upcastedObjectCount() {
        return getTypes().size();
    }
}
