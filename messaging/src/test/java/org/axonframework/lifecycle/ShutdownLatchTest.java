package org.axonframework.lifecycle;

import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests targeted towards the {@link ShutdownLatch}.
 *
 * @author Steven van Beelen
 */
class ShutdownLatchTest {

    private final ShutdownLatch testSubject = new ShutdownLatch();

    @Test
    void testIncrementThrowsShutdownInProgressExceptionIfShuttingDown() {
        testSubject.initiateShutdown();

        assertThrows(ShutdownInProgressException.class, testSubject::registerActivity);
    }

    @Test
    void testDecrementCompletesWaitProcess() {
        ShutdownLatch.ActivityHandle activityHandle = testSubject.registerActivity();

        CompletableFuture<Void> latch = testSubject.initiateShutdown();

        assertFalse(latch.isDone());

        activityHandle.end();

        assertTrue(latch.isDone());
    }

    @Test
    void testIsShuttingDownIsFalseForNonAwaitedLatch() {
        assertFalse(testSubject.isShuttingDown());
    }

    @Test
    void testIsShuttingDownIsTrueForAwaitedLatch() {
        testSubject.initiateShutdown();

        assertTrue(testSubject.isShuttingDown());
    }

    @Test
    void testIsShuttingDownThrowsSuppliedExceptionForAwaitedLatch() {
        testSubject.initiateShutdown();

        assertThrows(SomeException.class, () -> testSubject.ifShuttingDown(SomeException::new));
    }

    @Test
    void testActivityHandleEndReturnsTrueForFirstInvocationAndFalseForSubsequent() {
        ShutdownLatch.ActivityHandle subject = testSubject.registerActivity();

        assertTrue(subject.end());

        assertFalse(subject.end());
    }

    private static class SomeException extends RuntimeException {

    }
}