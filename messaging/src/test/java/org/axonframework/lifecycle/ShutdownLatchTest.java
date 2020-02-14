package org.axonframework.lifecycle;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests targeted towards the {@link ShutdownLatch}.
 *
 * @author Steven van Beelen
 */
class ShutdownLatchTest {

    private final ShutdownLatch testSubject = new ShutdownLatch();

    @Test
    void testIncrementThrowsIllegalStateExceptionIfShuttingDown() {
        testSubject.await();

        assertThrows(IllegalStateException.class, testSubject::increment);
    }

    @Test
    void testDecrementCompletesWaitProcess() {
        testSubject.increment();

        CompletableFuture<Void> latch = testSubject.await();

        assertFalse(latch.isDone());

        testSubject.decrement();

        assertTrue(latch.isDone());
    }

    @Test
    void testIsShuttingDownIsFalseForNonAwaitedLatch() {
        assertFalse(testSubject.isShuttingDown());
    }

    @Test
    void testIsShuttingDownIsTrueForAwaitedLatch() {
        testSubject.await();

        assertTrue(testSubject.isShuttingDown());
    }

    @Test
    void testIsShuttingDownThrowsSuppliedExceptionForAwaitedLatch() {
        testSubject.await();

        assertThrows(SomeException.class, () -> testSubject.isShuttingDown(SomeException::new));
    }

    @Test
    void testLatchCompletesExceptionallyAfterDuration() {
        CompletableFuture<Void> latch = testSubject.await(Duration.ofMillis(50));

        assertFalse(latch.isDone());

        assertThrows(ExecutionException.class, latch::get);

        assertTrue(latch.isCompletedExceptionally());
    }

    private static class SomeException extends RuntimeException {

    }
}