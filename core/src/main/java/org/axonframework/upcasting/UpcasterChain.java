package org.axonframework.upcasting;

import org.axonframework.serializer.SerializedObject;

import java.util.List;

/**
 * Represents a series of upcasters which are combined to upcast a {@link org.axonframework.serializer.SerializedObject}
 * to the most recent revision of that payload. The intermediate representation required by each of the upcasters is
 * converted using converters provided by a converterFactory.
 * <p/>
 * Upcasters for different object types may be merged into a single chain, as long as the order of related upcasters
 * can be guaranteed.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface UpcasterChain {

    /**
     * Pass the given <code>serializedObject</code> through the chain of upcasters. The result is a list of zero or
     * more serializedObjects representing the latest revision of the payload object.
     *
     * @param serializedObject the serialized object to upcast
     * @return the upcast SerializedObjects
     */
    List<SerializedObject> upcast(SerializedObject serializedObject);
}
