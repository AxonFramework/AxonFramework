package org.axonframework.domain;

/**
 * Representation of a Message, containing a Payload and MetaData. Typical examples of Messages are Commands and
 * Events.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface Message {
    /**
     * Returns the meta data for this event. This meta data is a collection of key-value pairs, where the key is a
     * String, and the value is a serializable object.
     *
     * @return the meta data for this event
     */
    MetaData getMetaData();

    /**
     * Returns the payload of this Event. The payload is the application-specific information.
     *
     * @return the payload of this Event
     */
    Object getPayload();

    /**
     * Returns the class name of the payload, as defined by {@link Class#getName()}.
     * <p/>
     * Note: the fully qualified class name is returned to prevent class loading problems on machines that might not
     * have access to this class.
     *
     * @return the fully qualified class name of the payload.
     *
     * @see Class#getName()
     */
    Class getPayloadType();
}
