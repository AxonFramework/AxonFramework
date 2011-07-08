package org.axonframework.examples.addressbook.rest.listener;

/**
 * <p>Very generic interface to be used by the listeners to publish a Message to the implemented store</p>
 *
 * @author Jettro Coenradie
 */
public interface Publisher {
    /**
     * Publish the provided Message, beware that no exceptios should be thrown here if the underlying system is not
     * available.
     *
     * @param message Message to be stored
     */
    void publish(Message<?> message);
}
