package org.axonframework.serializer;

import java.io.InputStream;

/**
 * Interface describing the structure of a serialized object.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface SerializedObject {

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
    byte[] getData();

    /**
     * An inputstream accessing the serialized data. Each invocation of this method will return a new stream. The
     * caller should close the stream when finished reading.
     *
     * @return an inputstream accessing the serialized data
     */
    InputStream getStream();
}
