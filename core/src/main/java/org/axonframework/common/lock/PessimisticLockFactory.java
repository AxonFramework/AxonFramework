/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedMap;

/**
 * Implementation of a {@link LockFactory} that uses a pessimistic locking strategy. Calls to
 * {@link #obtainLock} will block until a lock could be obtained or back off limit is reached, based on the
 * {@link BackoffParameters}, by throwing an exception. The latter will cause the command to fail, but will allow
 * the calling thread to be freed. If a lock is obtained by a thread, that thread has guaranteed unique access.
 * <p/>
 * Each thread can hold the same lock multiple times. The lock will only be released for other threads when the lock
 * has been released as many times as it was obtained.
 * <p/>
 * This lock can be used to ensure thread safe access to a number of objects, such as Aggregates and Sagas.
 * <p>
 * Back off properties with respect to acquiring locks can be configured through the {@link BackoffParameters}.
 *
 * @author Allard Buijze
 * @author Michael Bischoff
 * @since 1.3
 */
public class PessimisticLockFactory implements LockFactory {

    private static final Set<PessimisticLockFactory> INSTANCES = newSetFromMap(synchronizedMap(new WeakHashMap<>()));

    private final ConcurrentHashMap<String, DisposableLock> locks = new ConcurrentHashMap<>();
    private final BackoffParameters backoffParameters;

    private static Set<Thread> threadsWaitingForMyLocks(Thread owner) {
        return threadsWaitingForMyLocks(owner, INSTANCES);
    }

    private static Set<Thread> threadsWaitingForMyLocks(Thread owner, Set<PessimisticLockFactory> locksInUse) {
        Set<Thread> waitingThreads = new HashSet<>();
        for (PessimisticLockFactory lock : locksInUse) {
            lock.locks.values().stream()
                      .filter(disposableLock -> disposableLock.isHeldBy(owner))
                      .forEach(disposableLock -> disposableLock.queuedThreads().stream()
                                                               .filter(waitingThreads::add)
                                                               .forEach(thread -> waitingThreads.addAll(threadsWaitingForMyLocks(thread, locksInUse))));
        }
        return waitingThreads;
    }

    /**
     * Creates a new IdentifierBasedLock instance.
     * <p/>
     * Deadlocks are detected across instances of the IdentifierBasedLock.
     * This constructor specifies no back off from lock acquisition
     *
     * @apiNote Since the previous versions didn't support any backoff properties, this no-arg constructor creates a
     * {@link PessimisticLockFactory} with no backoff properties. This is however a poor default (the system will
     * very likely converge to a state where it no longer handles any commands if any lock is held indefinitely.) In the
     * next major version of Axon other defaults will chosen and thus behavior will change. Should your setup rely
     * on the no-backoff behavior then you are advised to call {@link #PessimisticLockFactory(BackoffParameters)} with
     * explicitly specified {@link BackoffParameters}.
     * @Deprecated use {@link #PessimisticLockFactory(BackoffParameters)} instead
     */
    @Deprecated
    public PessimisticLockFactory() {
        this(new BackoffParameters(-1, -1, 100));
    }

    /**
     * Creates a new IdentifierBasedLock instance.
     * <p/>
     * Deadlocks are detected across instances of the IdentifierBasedLock.
     * Back off policy as by supplied {@link BackoffParameters}
     *
     * @param backoffParameters back off policy configuration
     */
    public PessimisticLockFactory(BackoffParameters backoffParameters) {
        this.backoffParameters = backoffParameters;
        INSTANCES.add(this);
    }

    /**
     * Obtain a lock for a resource identified by the given {@code identifier}. This method will block until a
     * lock was successfully obtained.
     * <p/>
     * Note: when an exception occurs during the locking process, the lock may or may not have been allocated.
     *
     * @param identifier the identifier of the lock to obtain.
     * @return a handle to release the lock. If the thread that releases the lock does not hold the lock
     * {@link IllegalMonitorStateException} is thrown
     */
    @Override
    public Lock obtainLock(String identifier) {
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
        DisposableLock lock = locks.get(identifier);
        while (lock == null) {
            locks.putIfAbsent(identifier, new DisposableLock(identifier));
            lock = locks.get(identifier);
        }
        return lock;
    }

    /**
     * There are 3 values:
     * <p>
     * acquireAttempts
     * This used to specify the maxium number of attempts to obtain a lock before we back off
     * (throwing a {@link LockAcquisitionFailedException} if it does). A value of '-1' means unlimited attempts.
     * <p>
     * maximumQueued
     * Maximum number of queued threads we allow to try and obtain a lock, if another thread tries to obtain the lock
     * after the limit is reached we back off (throwing a {@link LockAcquisitionFailedException}). A value of '-1'
     * means the maximum queued threads is unbound / no limit.
     * NOTE: This relies on approximation given by {@link ReentrantLock#getQueueLength()} so the effective limit may
     * be higher then specified. Since this is a back off control this should be ok.
     * <p>
     * lockAttemptTimeout
     * Time permitted to try and obtain a lock per acquire attempt in milliseconds.
     * NOTE: The lockAttemptTimeout of the first attempt is always zero, so max wait time is approximately
     * (acquireAttempts - 1) * lockAttemptTimeout
     */
    public static final class BackoffParameters {
        public final int acquireAttempts;
        public final int maximumQueued;
        public final int lockAttemptTimeout;

        /**
         * Initialize the BackoffParameters using given parameters.
         *
         * @param acquireAttempts    the total number of attempts to make to acquire the lock. A value of {@code -1} means indefinite attempts
         * @param maximumQueued      the threshold of the number of threads queued for acquiring the lock, or {@code -1} to ignore queue size
         * @param lockAttemptTimeout the amount of time to wait, in milliseconds, for each lock acquisition attempt
         */
        public BackoffParameters(int acquireAttempts, int maximumQueued, int lockAttemptTimeout) {
            Assert.isTrue(
                    acquireAttempts > 0 || acquireAttempts == -1,
                    () -> "acquireAttempts needs to be a positive integer or -1, but was '" + acquireAttempts + "'"
            );
            this.acquireAttempts = acquireAttempts;
            Assert.isTrue(
                    maximumQueued > 0 || maximumQueued == -1,
                    () -> "maximumQueued needs to be a positive integer or -1, but was '" + maximumQueued + "'"
            );
            this.maximumQueued = maximumQueued;
            Assert.isFalse(
                    lockAttemptTimeout < 0,
                    () -> "lockAttemptTimeout needs to be a non negative integer, but was '" + lockAttemptTimeout + "'"
            );
            this.lockAttemptTimeout = lockAttemptTimeout;
        }

        public boolean hasAcquireAttemptLimit() {
            return acquireAttempts != -1;
        }

        public boolean hasAcquireQueueLimit() {
            return maximumQueued != -1;
        }

        public boolean maximumQueuedThreadsReached(int queueLength) {
            if (!hasAcquireQueueLimit()) {
                return false;
            }
            return queueLength >= maximumQueued;
        }
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
            if (backoffParameters.maximumQueuedThreadsReached(lock.getQueueLength())) {
                throw new LockAcquisitionFailedException("Failed to acquire lock for aggregate identifier " + identifier + ": too many queued threads.");
            }
            try {
                if (!lock.tryLock(0, TimeUnit.NANOSECONDS)) {
                    int attempts = backoffParameters.acquireAttempts - 1;
                    do {
                        attempts--;
                        checkForDeadlock();
                        if (backoffParameters.hasAcquireAttemptLimit() && attempts < 1) {
                            throw new LockAcquisitionFailedException(
                                    "Failed to acquire lock for aggregate identifier(" + identifier + "), maximum attempts exceeded (" + backoffParameters.maximumQueued + ")"
                            );
                        }
                    } while (!lock.tryLock(backoffParameters.lockAttemptTimeout, TimeUnit.MILLISECONDS));
                }
            } catch (InterruptedException e) {
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
