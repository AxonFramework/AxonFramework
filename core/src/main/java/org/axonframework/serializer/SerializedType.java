package org.axonframework.serializer;

/**
 * Describes the type of a serialized object. This information is used to decide how to deserialize an object.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface SerializedType {

    /**
     * Returns the name of the serialized type. This may be the class name of the serialized object, or an alias for
     * that name.
     *
     * @return the name of the serialized type
     */
    String getName();

    /**
     * Returns the revision number of the serialized object. This revision number is used by upcasters to decide how
     * to transform serialized objects during deserialization.
     *
     * @return the revision number of the serialized object
     */
    int getRevision();
}
