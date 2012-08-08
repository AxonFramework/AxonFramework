package org.axonframework.common.lock;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that a lock could not be obtained.
 * <p/>
 * Typically, operations failing with this exception cannot be retried without the application taking appropriate
 * measures first.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class LockAcquisitionFailedException extends AxonNonTransientException {

    private static final long serialVersionUID = 4453369833513201587L;

    /**
     * Initialize the exception with given <code>message</code> and <code>cause</code>
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public LockAcquisitionFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Initialize the exception with given <code>message</code>.
     *
     * @param message The message describing the exception
     */
    public LockAcquisitionFailedException(String message) {
        super(message);
    }
}
