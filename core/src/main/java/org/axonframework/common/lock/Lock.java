package org.axonframework.common.lock;

/**
 * Interface of a lock acquired to gain (exclusive) access to a shared resource, with a mechanism to release it again.
 * <p/>
 * This lock is a {@link AutoCloseable} resource, so will be released automatically if declared in a {@code
 * try}-with-resources block.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public interface Lock extends AutoCloseable {

    /**
     * Releases this lock. By default this simply calls {@link #release()}.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    default void close() {
        release();
    }

    /**
     * Releases this lock. If this lock is already released or no longer valid, the behavior of this method is
     * undefined.
     */
    void release();

    /**
     * Indicates whether the lock is still owned {@code true}, or whether it has been released {@code false}.
     *
     * @return true if the lock is still valid, or false if it has been released
     */
    boolean isHeld();
}
