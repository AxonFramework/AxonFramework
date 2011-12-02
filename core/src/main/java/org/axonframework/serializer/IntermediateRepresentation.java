package org.axonframework.serializer;

/**
 * Interface describing the intermediate representation of a serialized object, which is used by Upcasters to change
 * the
 * data structure, to reflect changes made in the structure of serialized objects.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface IntermediateRepresentation<T> {

    /**
     * Returns the type of this representation's data.
     *
     * @return the type of this representation's data
     */
    Class<T> getContentType();

    /**
     * Returns the description of the serialized object, represented by the data.
     *
     * @return the description of the serialized object, represented by the data
     */
    SerializedType getType();

    /**
     * Returns a view of the actual data contained in this representation. This may either be a copy, or access to
     * mutable internal state.
     *
     * @return a view of the actual data contained in this representation
     */
    T getData();
}
