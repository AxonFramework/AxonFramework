package org.axonframework.common;

/**
 * Interface that provides a mechanism to stop a subscription.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public interface Subscription extends AutoCloseable {

    /**
     * Stops this subscription. By default this simply calls {@link #stop()}.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    default void close() throws Exception {
        stop();
    }

    /**
     * Stops this subscription. If the Subscription was already stopped, no action is taken.
     *
     * @return <code>true</code> if this handler is successfully unsubscribed, <code>false</code> if the given
     *         <code>handler</code> was not currently subscribed.
     */
    boolean stop();
}
