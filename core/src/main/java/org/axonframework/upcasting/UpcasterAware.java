package org.axonframework.upcasting;

/**
 * Interface indicating that the implementing mechanism is aware of Upcasters. Upcasting is the process where
 * deprecated Domain Objects (typically Events) are converted into the current format. This process typically occurs
 * with Domain Events that have been stored in an Event Store.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface UpcasterAware {

    /**
     * Sets the UpcasterChain which allow older revisions of serialized objects to be deserialized.
     *
     * @param upcasterChain the upcaster chain providing the upcasting capabilities
     */
    void setUpcasterChain(UpcasterChain upcasterChain);
}
