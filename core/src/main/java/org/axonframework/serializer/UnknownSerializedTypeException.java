package org.axonframework.serializer;

import org.axonframework.common.AxonNonTransientException;

import static java.lang.String.format;

/**
 * Exception indicating that an object could not be deserialized, because its serialized type cannot be mapped to a
 * class.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class UnknownSerializedTypeException extends AxonNonTransientException {

    /**
     * Initialize the constructor with a default message, containing details of the given <code>serializedType</code>
     *
     * @param serializedType The serialized type of the object being deserialized
     */
    public UnknownSerializedTypeException(SerializedType serializedType) {
        super(format("Could not deserialize a message. The serialized type is unknown: %s (rev. %s)",
                     serializedType.getName(), serializedType.getRevision()));
    }

    /**
     * Initialize the constructor with a default message, containing details of the given <code>serializedType</code>
     *
     * @param serializedType The serialized type of the object being deserialized
     * @param cause          The cause of this exception
     */
    public UnknownSerializedTypeException(SerializedType serializedType, Throwable cause) {
        super(format("Could not deserialize a message. The serialized type is unknown: %s (rev. %s)",
                     serializedType.getName(), serializedType.getRevision()),
              cause);
    }
}
