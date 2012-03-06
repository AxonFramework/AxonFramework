package org.axonframework.util.lock;

import org.axonframework.common.Assert;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Locking mechanism that allows multiple threads to hold a lock, as long as the identifier of the lock they hold is
 * not equal. When released, locks entries are automatically cleaned up.
 * <p/>
 * The lock is re-entrant, meaning each thread can hold the same lock multiple times. The lock will only be released
 * for other threads when the lock has been released as many times as it was obtained.
 * <p/>
 * This lock can be used to ensure thread safe access to a number of objects, such as Aggregates and Sagas.
 *
 * @author Allard Buijze
 * @since 1.3
 */
public class IdentifierBasedLock {

    private final ConcurrentHashMap<String, DisposableLock> locks = new ConcurrentHashMap<String, DisposableLock>();

    /**
     * Indicates whether the current thread hold a lock for the given <code>identifier</code>.
     *
     * @param identifier The identifier of the lock to verify
     * @return <code>true</code> if the current thread holds a lock, otherwise <code>false</code>
     */
    public boolean hasLock(String identifier) {
        return isLockAvailableFor(identifier)
                && lockFor(identifier).isHeldByCurrentThread();
    }

    /**
     * Obtain a lock on the given <code>identifier</code>. This method will block until a lock was successfully
     * obtained.
     * <p/>
     * Note: when an exception occurs during the locking process, the lock may or may not have been allocated.
     *
     * @param identifier the identifier of the lock to obtain.
     */
    public void obtainLock(String identifier) {
        boolean lockObtained = false;
        while (!lockObtained) {
            DisposableLock lock = lockFor(identifier);
            lockObtained = lock.lock();
            if (!lockObtained) {
                locks.remove(identifier, lock);
            }
        }
    }

    /**
     * Release the lock held on the given <code>identifier</code>. If no valid lock is held by the current thread, an
     * exception is thrown.
     *
     * @param identifier the identifier to release the lock for.
     * @throws IllegalStateException        if no lock was ever obtained for this aggregate
     * @throws IllegalMonitorStateException if a lock was obtained, but is not currently held by the current thread
     */
    public void releaseLock(String identifier) {
        Assert.state(locks.containsKey(identifier), "No lock for this identifier was ever obtained");
        DisposableLock lock = lockFor(identifier);
        lock.unlock(identifier);
    }

    private boolean isLockAvailableFor(String identifier) {
        return locks.containsKey(identifier);
    }

    private DisposableLock lockFor(String identifier) {
        DisposableLock lock = locks.get(identifier);
        while (lock == null) {
            locks.putIfAbsent(identifier, new DisposableLock());
            lock = locks.get(identifier);
        }
        return lock;
    }

    private final class DisposableLock {

        private final ReentrantLock lock;
        // guarded by "lock"
        private volatile boolean isClosed = false;

        private DisposableLock() {
            this.lock = new ReentrantLock();
        }

        private boolean isHeldByCurrentThread() {
            return lock.isHeldByCurrentThread();
        }

        private void unlock(String identifier) {
            lock.unlock();
            disposeIfUnused(identifier);
        }

        private boolean lock() {
            lock.lock();
            if (isClosed) {
                lock.unlock();
                return false;
            }
            return true;
        }

        private void disposeIfUnused(String identifier) {
            if (lock.tryLock()) {
                try {
                    if (lock.getHoldCount() == 1) {
                        // we now have a lock. We can shut it down.
                        isClosed = true;
                        locks.remove(identifier, this);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
