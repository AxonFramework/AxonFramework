package org.axonframework.serializer;

/**
 * Interface for Upcasters. An upcaster is the mechanism used to convert deprecated (typically serialized) objects and
 * convert them into the current format. If the serializer itself is not able to cope with the changed formats
 * (XStream, for example, will allow for some changes by using aliases), the Upcaster will allow you to configure more
 * complex structural transformations.
 * <p/>
 * Upcasters work on intermediate representations of the object to upcast. In some cases, this representation is a byte
 * array, while in other cases an object structure is used. The Serializer is responsible for converting the
 * intermediate representation form to one that is compatible with the upcaster. For performance reasons, it is
 * advisable to ensure that all upcasters in the same chain (where one's output is another's input) use the same
 * intermediate representation type.
 *
 * @param <T> The data format that this upcaster uses to represent the event
 * @author Allard Buijze
 * @since 2.0
 */
public interface Upcaster<T> {

    /**
     * Indicates whether this upcaster is capable of upcasting the given <code>type</code>. Unless this method returns
     * <code>true</code>, the {@link #upcast(SerializedType)} and {@link #upcast(SerializedType)} methods should not be
     * invoked on this Upcaster instance.
     *
     * @param serializedType The type under investigation
     * @return <code>true</code> if this upcaster can upcast the given serialized type, <code>false</code> otherwise.
     */
    boolean canUpcast(SerializedType serializedType);

    /**
     * Returns the type of intermediate representation this upcaster expects. The serializer must ensure the
     * intermediate representation is offered in a compatible format.
     *
     * @return the type of intermediate representation expected
     */
    Class<T> expectedRepresentationType();

    /**
     * Upcasts the given <code>intermediateRepresentation</code> into another one. The returned representation must
     * reflect the changes made by this upcaster by updating its SerializedType definition.
     *
     * @param intermediateRepresentation The representation of the object to upcast
     * @return the new representation of the object
     */
    SerializedObject<?> upcast(SerializedObject<T> intermediateRepresentation);

    /**
     * Upcast the given <code>serializedType</code> into its new format. Generally, this involves increasing the
     * revision number. Sometimes, it is also necessary to alter the type's name (in case of a renamed class, for
     * example).
     *
     * @param serializedType The serialized type to upcast
     * @return the upcast serialized type
     */
    SerializedType upcast(SerializedType serializedType);
}
