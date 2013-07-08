package org.axonframework.eventhandling;

/**
 * Interface describing a mechanism that provides the order for any given Event Listener. Listeners with a lower order
 * should receive precedence over the instances with a higher value. The order of instances with default order is
 * undefined.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public interface OrderResolver {

    /**
     * Returns the order for the given <code>listener</code>.
     * <p/>
     * Implementations should check whether the <code>listener</code> implements {@link EventListenerProxy}. In that
     * case, use {@link org.axonframework.eventhandling.EventListenerProxy#getTargetType()} to get access to the actual
     * type handling the events.
     *
     * @param listener the listener to resolve the order for
     * @return the order for the given listener, or <code>0</code> if no specific order is provided.
     */
    int orderOf(EventListener listener);
}
