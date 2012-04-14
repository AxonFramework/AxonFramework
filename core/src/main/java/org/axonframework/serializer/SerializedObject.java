package org.axonframework.serializer;

/**
 * Interface describing the structure of a serialized object.
 *
 * @param <T> The data type representing the serialized object
 * @author Allard Buijze
 * @since 2.0
 */
public interface SerializedObject<T> {

    /**
     * Returns the type of this representation's data.
     *
     * @return the type of this representation's data
     */
    Class<T> getContentType();

    /**
     * Returns the description of the type of object contained in the data.
     *
     * @return the description of the type of object contained in the data
     */
    SerializedType getType();

    /**
     * The actual data of the serialized object.
     *
     * @return the actual data of the serialized object
     */
    T getData();

}
