/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link PessimisticLockFactory}.
 * <p>
 * The try-with-resource suggestion is suppressed throughout this test class, as we ascertain the locks are released at
 * all times. Furthermore, wrapping the tests in try-with-resource blocks will not enhance the readability of the
 * tests.
 *
 * @author Allard Buijze
 */
class PessimisticLockFactoryTest {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private String identifier = "mockId";

    private PessimisticLockFactory testSubject;

    @BeforeEach
    void setUp() {
        testSubject = PessimisticLockFactory.builder()
                                            .build();
    }

    @Test
    void lockReferenceCleanedUpAtUnlock() throws NoSuchFieldException, IllegalAccessException {
        //noinspection resource
        Lock lock = testSubject.obtainLock(identifier);
        lock.release();

        Field locksField = testSubject.getClass().getDeclaredField("locks");
        locksField.setAccessible(true);
        //noinspection unchecked
        Map<String, Lock> locks = (Map<String, Lock>) locksField.get(testSubject);
        assertEquals(0, locks.size(), "Expected lock to be cleaned up");
    }

    @Test
    void lockOnlyCleanedUpIfNoLocksAreHeld() throws NoSuchFieldException, IllegalAccessException {
        //noinspection resource
        Lock lockOne = testSubject.obtainLock(identifier);
        //noinspection resource
        Lock lockTwo = testSubject.obtainLock(identifier);
        lockOne.release();

        Field locksField = testSubject.getClass().getDeclaredField("locks");
        locksField.setAccessible(true);
        //noinspection unchecked
        Map<String, Lock> locks = (Map<String, Lock>) locksField.get(testSubject);
        assertEquals(1, locks.size(), "Expected lock not to be cleaned up");

        lockTwo.release();
        //noinspection unchecked
        locks = (Map<String, Lock>) locksField.get(testSubject);
        assertTrue(locks.isEmpty(), "Expected locks to be cleaned up");
    }

    @Test
    @Timeout(value = 10)
    void deadlockDetectedWithTwoThreadsInVector() throws InterruptedException {
        final CountDownLatch starter = new CountDownLatch(1);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicBoolean deadlockInThread = new AtomicBoolean(false);
        String idOne = "id1-TwoThreadsVector";
        String idTwo = "id2-TwoThreadsVector";

        Thread lockingThread = createLockingThread(
                "TwoThreadsVector", starter, cdl, deadlockInThread, testSubject, idOne, testSubject, idTwo
        );

        lockingThread.start();
        //noinspection resource
        Lock lockTwo = testSubject.obtainLock(idTwo);
        starter.await();
        cdl.countDown();
        try {
            //noinspection resource
            Lock lockOne = testSubject.obtainLock(idOne);
            assertTrue(deadlockInThread.get());
            lockOne.release();
        } catch (DeadlockException e) {
            // this is ok!
        }
        lockTwo.release();
    }

    @Test
    @Timeout(value = 12)
    void deadlockDetectedWithTwoDifferentLockInstances() throws InterruptedException {
        final PessimisticLockFactory otherTestSubject = PessimisticLockFactory.builder().build();
        final CountDownLatch starter = new CountDownLatch(1);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicBoolean deadlockInThread = new AtomicBoolean(false);
        String idOne = "id1-TwoDifferentLockInstances";

        Thread lockingThread = createLockingThread(
                "TwoDifferentLockInstances", starter, cdl, deadlockInThread, testSubject, idOne, otherTestSubject, idOne
        );

        lockingThread.start();
        //noinspection resource
        Lock otherLock = otherTestSubject.obtainLock(idOne);
        starter.await();
        cdl.countDown();
        try {
            //noinspection resource
            Lock thisLock = testSubject.obtainLock(idOne);
            assertTrue(deadlockInThread.get());
            thisLock.release();
        } catch (DeadlockException e) {
            // this is ok!
        }
        otherLock.release();
    }

    @Test
    @Timeout(value = 10)
    void deadlockDetectedWithThreeThreadsInVector() throws InterruptedException {
        final CountDownLatch starter = new CountDownLatch(3);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicBoolean deadlockInThread = new AtomicBoolean(false);
        String idOne = "id1-ThreeThreadsInVector";
        String idTwo = "id2-ThreeThreadsInVector";
        String idThree = "id3-ThreeThreadsInVector";
        String idFour = "id4-ThreeThreadsInVector";

        Thread firstLockingThread = createLockingThread(
                "ThreeThreadsInVector", starter, cdl, deadlockInThread, testSubject, idOne, testSubject, idTwo
        );
        Thread secondLockingThread = createLockingThread(
                "ThreeThreadsInVector", starter, cdl, deadlockInThread, testSubject, idTwo, testSubject, idThree
        );
        Thread thirdLockingThread = createLockingThread(
                "ThreeThreadsInVector", starter, cdl, deadlockInThread, testSubject, idThree, testSubject, idFour
        );

        firstLockingThread.start();
        secondLockingThread.start();
        thirdLockingThread.start();

        //noinspection resource
        Lock lockFour = testSubject.obtainLock(idFour);
        starter.await();
        cdl.countDown();
        try {
            //noinspection resource
            Lock lockOne = testSubject.obtainLock(idOne);
            assertTrue(deadlockInThread.get());
            lockOne.release();
        } catch (DeadlockException e) {
            // this is ok!
        }
        lockFour.release();
    }

    @Test
    @Timeout(value = 5)
    void reachingConfigureAcquireAttemptsCausesLockAcquisitionFailedException() {
        final PessimisticLockFactory lockFactory = PessimisticLockFactory.builder()
                                                                         .acquireAttempts(10)
                                                                         .queueLengthThreshold(Integer.MAX_VALUE)
                                                                         .lockAttemptTimeout(0)
                                                                         .build();
        final CountDownLatch rendezvous = new CountDownLatch(1);
        try {
            final AtomicReference<Exception> exceptionInThread = new AtomicReference<>();
            final String id = "aggregateId";
            // Obtain the lock
            createThreadObtainLockAndWaitForState(lockFactory, Thread.State.WAITING, rendezvous, exceptionInThread, id);
            // backoff triggers, too many spins
            assertThrows(LockAcquisitionFailedException.class, () -> {
                //noinspection resource
                Lock lock = lockFactory.obtainLock(id);
                lock.release();
            });
        } finally {
            rendezvous.countDown();
        }
    }

    @Test
    @Timeout(value = 5)
    void reachingConfiguredQueueBackoffCausesLockAcquisitionFailedException() {
        final PessimisticLockFactory lockFactory = PessimisticLockFactory.builder()
                                                                         .acquireAttempts(Integer.MAX_VALUE)
                                                                         .queueLengthThreshold(2)
                                                                         .lockAttemptTimeout(10000)
                                                                         .build();
        final CountDownLatch rendezvous = new CountDownLatch(1);
        try {
            final AtomicReference<Exception> exceptionInThread = new AtomicReference<>();
            final String id = "aggregateId";
            // Obtain the lock
            createThreadObtainLockAndWaitForState(lockFactory, Thread.State.WAITING, rendezvous, exceptionInThread, id);
            // Fill Queue 1/2
            createThreadObtainLockAndWaitForState(lockFactory,
                                                  Thread.State.TIMED_WAITING,
                                                  rendezvous,
                                                  exceptionInThread,
                                                  id);
            // Fill Queue 2/2
            createThreadObtainLockAndWaitForState(lockFactory,
                                                  Thread.State.TIMED_WAITING,
                                                  rendezvous,
                                                  exceptionInThread,
                                                  id);
            // backoff triggers, queue
            assertThrows(LockAcquisitionFailedException.class, () -> {
                //noinspection resource
                Lock lock = lockFactory.obtainLock(id);
                lock.release();
            });
        } finally {
            rendezvous.countDown();
        }
    }

    private Thread createLockingThread(final String threadName,
                                       final CountDownLatch starter,
                                       final CountDownLatch cdl,
                                       final AtomicBoolean deadlockInThread,
                                       final PessimisticLockFactory factoryOne,
                                       final String idOne,
                                       final PessimisticLockFactory factoryTwo,
                                       final String idTwo) {
        return new Thread(() -> {
            //noinspection resource
            Lock lock1 = factoryOne.obtainLock(idOne);
            starter.countDown();
            try {
                cdl.await();
                //noinspection resource
                Lock lock2 = factoryTwo.obtainLock(idTwo);
                lock2.release();
            } catch (InterruptedException e) {
                logger.info("Thread 1 interrupted", e);
            } catch (DeadlockException e) {
                deadlockInThread.set(true);
            } finally {
                lock1.release();
            }
        }, threadName);
    }

    private void createThreadObtainLockAndWaitForState(PessimisticLockFactory lockFactory,
                                                       Thread.State state,
                                                       CountDownLatch rendezvous,
                                                       AtomicReference<Exception> exceptionInThread,
                                                       @SuppressWarnings("SameParameterValue") String id) {
        Thread thread = new Thread(() -> {
            try (Lock ignored = lockFactory.obtainLock(id)) {
                rendezvous.await();
            } catch (Exception e) {
                exceptionInThread.set(e);
            }
        }, "");
        thread.start();
        while (thread.isAlive() && rendezvous.getCount() > 0 && thread.getState() != state) {
            Thread.yield();
        }
    }

    @Test
    void backoffParametersConstructorAcquireAttempts() {
        PessimisticLockFactory.Builder builderTestSubject = PessimisticLockFactory.builder();

        int illegalValue = 0;
        assertThrows(IllegalArgumentException.class, () -> builderTestSubject.acquireAttempts(illegalValue));
    }

    @Test
    void backoffParametersConstructorMaximumQueued() {
        PessimisticLockFactory.Builder builderTestSubject = PessimisticLockFactory.builder();

        int illegalValue = 0;
        assertThrows(IllegalArgumentException.class, () -> builderTestSubject.queueLengthThreshold(illegalValue));
    }

    @Test
    void backoffParametersConstructorSpinTime() {
        PessimisticLockFactory.Builder builderTestSubject = PessimisticLockFactory.builder();

        int illegalValue = -1;
        assertThrows(IllegalArgumentException.class, () -> builderTestSubject.lockAttemptTimeout(illegalValue));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenIdentifierIsNull() {
        this.identifier = null;

        //noinspection resource
        assertThrows(IllegalArgumentException.class, () -> testSubject.obtainLock(identifier));
    }
}
