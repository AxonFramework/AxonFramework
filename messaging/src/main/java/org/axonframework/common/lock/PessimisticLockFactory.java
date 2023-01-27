/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.lock;

import org.axonframework.common.Assert;

import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedSet;
import static org.axonframework.common.Assert.nonNull;

/**
 * Implementation of a {@link LockFactory} that uses a pessimistic locking strategy. Calls to
 * {@link #obtainLock} will block until a lock could be obtained or back off limit is reached, based on the
 * settings provided, by throwing an exception. The latter will cause the command to fail, but will allow
 * the calling thread to be freed. If a lock is obtained by a thread, that thread has guaranteed unique access.
 * <p/>
 * Each thread can hold the same lock multiple times. The lock will only be released for other threads when the lock
 * has been released as many times as it was obtained.
 * <p/>
 * This lock can be used to ensure thread safe access to a number of objects, such as Aggregates and Sagas.
 *
 * @author Allard Buijze
 * @author Michael Bischoff
 * @author Henrique Sena
 * @since 1.3
 */
public class PessimisticLockFactory implements LockFactory {

    private static final Set<PessimisticLockFactory> INSTANCES = synchronizedSet(newSetFromMap(new WeakHashMap<>()));

    private final ConcurrentHashMap<String, DisposableLock> locks = new ConcurrentHashMap<>();
    private final int acquireAttempts;
    private final int maximumQueued;
    private final int lockAttemptTimeout;

    private static Set<Thread> threadsWaitingForMyLocks(Thread owner) {
        return threadsWaitingForMyLocks(owner, INSTANCES);
    }

    private static Set<Thread> threadsWaitingForMyLocks(Thread owner, Set<PessimisticLockFactory> locksInUse) {
        try {
            Set<Thread> waitingThreads = new HashSet<>();
            locksInUse.stream()
                      .flatMap(lock -> lock.locks.values().stream())
                      .filter(disposableLock -> disposableLock.isHeldBy(owner))
                      .forEach(disposableLock -> disposableLock.queuedThreads().stream()
                                                               .filter(waitingThreads::add)
                                                               .forEach(thread -> waitingThreads.addAll(threadsWaitingForMyLocks(thread, locksInUse))));
            return waitingThreads;
        } catch (ConcurrentModificationException e) {
            // the GC may be cleaning up entries form the WeakHashMap. Nothing we can do about it. Let's assume there are no threads waiting. A new attempt will reveil issues.
            return Collections.emptySet();
        }
    }

    /**
     * Creates a builder to construct an instance of this LockFactory.
     *
     * @return a builder allowing the definition of properties for this Lock Factory.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an instance using default values, as defined in the properties of the {@link Builder}.
     *
     * @return a PessimisticLockFactory instance using sensible default values
     */
    public static PessimisticLockFactory usingDefaults() {
        return builder().build();
    }

    /**
     * Creates an instance of the lock factory using the given {@code builder} containing the configuration properties
     * to use.
     *
     * @param builder The building containing the configuration properties to use
     */
    protected PessimisticLockFactory(Builder builder) {
        this.acquireAttempts = builder.acquireAttempts;
        this.maximumQueued = builder.maximumQueued;
        this.lockAttemptTimeout = builder.lockAttemptTimeout;
        INSTANCES.add(this);
    }

    /**
     * Obtain a lock for a resource identified by the given {@code identifier}. This method will block until a lock was
     * successfully obtained.
     * <p/>
     * Note: when an exception occurs during the locking process, the lock may or may not have been allocated.
     *
     * @param identifier the identifier of the lock to obtain.
     * @return A handle to release the lock. If the thread that releases the lock does not hold the lock a
     * {@link IllegalMonitorStateException} is thrown.
     * @throws IllegalArgumentException Thrown when the given {@code identifier} is {@code null}.
     */
    @Override
    public Lock obtainLock(String identifier) {
        nonNull(identifier, () -> "The identifier to obtain a lock for may not be null.");
        boolean lockObtained = false;
        DisposableLock lock = null;
        while (!lockObtained) {
            lock = lockFor(identifier);
            lockObtained = lock.lock();
            if (!lockObtained) {
                locks.remove(identifier, lock);
            }
        }
        return lock;
    }

    private DisposableLock lockFor(String identifier) {
        return locks.computeIfAbsent(identifier, DisposableLock::new);
    }

    private static final class PubliclyOwnedReentrantLock extends ReentrantLock {

        private static final long serialVersionUID = -2259228494514612163L;

        @Override
        public Collection<Thread> getQueuedThreads() { // NOSONAR
            return super.getQueuedThreads();
        }

        public boolean isHeldBy(Thread thread) {
            return thread.equals(getOwner());
        }
    }

    /**
     * Builder class for the {@link PessimisticLockFactory}.
     */
    public static class Builder {
        private int acquireAttempts = 6000;
        private int maximumQueued = Integer.MAX_VALUE;
        private int lockAttemptTimeout = 10;

        /**
         * Default constructor
         */
        protected Builder() {
        }

        /**
         * Indicates how many attempts should be done to acquire a lock. In combination with the
         * {@link #lockAttemptTimeout(int)}, this defines the total timeout of a lock acquisition.
         * <p>
         * Defaults to 6000.
         *
         * @param acquireAttempts The number of attempts to acquire the lock
         *
         * @return this Builder, for further configuration
         */
        public Builder acquireAttempts(int acquireAttempts) {
            Assert.isTrue(
                    acquireAttempts > 0 || acquireAttempts == -1,
                    () -> "acquireAttempts needs to be a positive integer or -1, but was '" + acquireAttempts + "'"
            );
            this.acquireAttempts = acquireAttempts;
            return this;
        }

        /**
         * Defines the maximum number of queued threads to allow for this lock. If the given number of threads are
         * waiting to acquire a lock, and another thread joins, that thread will immediately fail any attempt to acquire
         * the lock, as if it had timed out.
         * <p>
         * Defaults to unbounded.
         *
         * @param maximumQueued The maximum number of threads to allow in the queue for this lock
         * @return this Builder, for further configuration
         */
        public Builder queueLengthThreshold(int maximumQueued) {
            Assert.isTrue(
                    maximumQueued > 0,
                    () -> "queueLengthThreshold needs to be a positive integer, but was '" + maximumQueued + "'"
            );
            this.maximumQueued = maximumQueued;
            return this;
        }

        /**
         * The duration of a single attempt to acquire the internal lock. In combination with the
         * {@link #acquireAttempts(int)}, this defines the total timeout of an acquisition attempt.
         * <p>
         * Defaults to 10ms.
         *
         * @param lockAttemptTimeout The duration of a single acquisition attempt of the internal lock, in milliseconds
         *
         * @return this Builder, for further configuration
         */
        public Builder lockAttemptTimeout(int lockAttemptTimeout) {
            Assert.isTrue(
                    lockAttemptTimeout >= 0,
                    () -> "lockAttemptTimeout needs to be a non negative integer, but was '" + lockAttemptTimeout + "'"
            );
            this.lockAttemptTimeout = lockAttemptTimeout;
            return this;
        }

        /**
         * Builds the PessimisticLockFactory instance using the properties defined in this builder
         *
         * @return a fully configured PessimisticLockFactory instance
         */
        public PessimisticLockFactory build() {
            return new PessimisticLockFactory(this);
        }
    }

    private class DisposableLock implements Lock {

        private final String identifier;
        private final PubliclyOwnedReentrantLock lock;
        private volatile boolean isClosed = false;

        private DisposableLock(String identifier) {
            this.identifier = identifier;
            this.lock = new PubliclyOwnedReentrantLock();
        }

        @Override
        public void release() {
            try {
                lock.unlock();
            } finally {
                disposeIfUnused();
            }
        }

        @Override
        public boolean isHeld() {
            return lock.isHeldByCurrentThread();
        }

        public boolean lock() {
            if (lock.getQueueLength() >= maximumQueued) {
                throw new LockAcquisitionFailedException("Failed to acquire lock for identifier " + identifier + ": too many queued threads.");
            }
            try {
                if (!lock.tryLock(0, TimeUnit.NANOSECONDS)) {
                    int attempts = acquireAttempts - 1;
                    do {
                        attempts--;
                        checkForDeadlock();
                        if (attempts < 1) {
                            throw new LockAcquisitionFailedException(
                                    "Failed to acquire lock for identifier(" + identifier + "), maximum attempts exceeded (" + acquireAttempts + ")"
                            );
                        }
                    } while (!lock.tryLock(lockAttemptTimeout, TimeUnit.MILLISECONDS));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LockAcquisitionFailedException("Thread was interrupted", e);
            }
            if (isClosed) {
                lock.unlock();
                return false;
            }
            return true;
        }

        private void checkForDeadlock() {
            if (!lock.isHeldByCurrentThread() && lock.isLocked()) {
                for (Thread thread : threadsWaitingForMyLocks(Thread.currentThread())) {
                    if (lock.isHeldBy(thread)) {
                        throw new DeadlockException(
                                "An imminent deadlock was detected while attempting to acquire a lock"
                        );
                    }
                }
            }
        }

        private void disposeIfUnused() {
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

        public Collection<Thread> queuedThreads() {
            return lock.getQueuedThreads();
        }

        public boolean isHeldBy(Thread owner) {
            return lock.isHeldBy(owner);
        }

    }
}
