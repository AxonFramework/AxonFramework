package org.axonframework.common;

/**
 * Interface that provides a mechanism to stop a subscription.
 *
 * @author Rene de Waele
 */
public interface Subscription extends AutoCloseable {

    /**
     * Close this subscription.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    void close() throws Exception;
}
