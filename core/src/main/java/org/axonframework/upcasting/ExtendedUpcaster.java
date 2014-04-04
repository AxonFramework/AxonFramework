package org.axonframework.upcasting;

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;

import java.util.List;

/**
 * Extension of the Upcaster interface that allows type upcasting to be based on the contents of the
 * serialized object. UpcasterChain implementations should invoke the {@link #upcast(org.axonframework.serializer.SerializedType,
 * org.axonframework.serializer.SerializedObject)} on upcasters that implement this interface, instead of {@link
 * #upcast(org.axonframework.serializer.SerializedType)}
 *
 * @param <T> The data format that this upcaster uses to represent the event
 * @author Allard Buijze
 * @since 2.2
 */
public interface ExtendedUpcaster<T> extends Upcaster<T> {

    /**
     * Upcast the given <code>serializedType</code> into its new format. Generally, this involves increasing the
     * revision. Sometimes, it is also necessary to alter the type's name (in case of a renamed class, for example).
     * The order and the size of the list returned has to match with the order and size of the list of the upcast
     * IntermediateRepresentations by this upcaster.
     * <p/>
     * Unlike the {@link #upcast(org.axonframework.serializer.SerializedType)} method, this gives you access to the
     * serialized object to upcast. This may be used to choose the SerializedType based on the contents of a Message's
     * payload.
     * <p/>
     * Implementations aware of the ExtendedUpcaster interface must use this method instead of {@link
     * #upcast(org.axonframework.serializer.SerializedType)}
     *
     * @param serializedType             The serialized type to upcast
     * @param intermediateRepresentation The intermediate representation of the object to define the type for
     * @return the upcast serialized type
     */
    List<SerializedType> upcast(SerializedType serializedType, SerializedObject<T> intermediateRepresentation);

    /**
     * {@inheritDoc}
     * <p/>
     * Implementing this method is optional.
     *
     * @throws java.lang.UnsupportedOperationException if the implementation requires the intermediate representation
     * to upcast the serialized type.
     */
    @Override
    List<SerializedType> upcast(SerializedType serializedType);
}
