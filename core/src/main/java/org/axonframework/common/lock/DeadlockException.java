package org.axonframework.common.lock;

import org.axonframework.common.AxonTransientException;

/**
 * Exception indicating that a deadlock has been detected while a thread was attempting to acquire a lock. This
 * typically happens when a Thread attempts to acquire a lock that is owned by a Thread that is in turn waiting for a
 * lock held by the current thread.
 * <p/>
 * It is typically safe to retry the operation when this exception occurs.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DeadlockException extends AxonTransientException {

    private static final long serialVersionUID = -5552006099153686607L;

    /**
     * Initializes the exception with given <code>message</code>.
     *
     * @param message The message describing the exception
     */
    public DeadlockException(String message) {
        super(message);
    }
}
