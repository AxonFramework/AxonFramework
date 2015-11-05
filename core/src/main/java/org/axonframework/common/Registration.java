package org.axonframework.common;

/**
 * Interface that provides a mechanism to cancel a registration.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public interface Registration extends AutoCloseable {

    /**
     * Cancels this Registration. By default this simply calls {@link #cancel()}.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    default void close() throws Exception {
        cancel();
    }

    /**
     * Cancels this Registration. If the Registration was already cancelled, no action is taken.
     *
     * @return <code>true</code> if this handler is successfully unregistered, <code>false</code> if this handler
     * was not currently registered.
     */
    boolean cancel();
}
