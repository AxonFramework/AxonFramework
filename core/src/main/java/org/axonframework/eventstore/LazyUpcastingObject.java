package org.axonframework.eventstore;

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.UpcasterChain;

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
        if(upcastedObjects == null) {
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
        if(upcastedTypes == null) {
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
