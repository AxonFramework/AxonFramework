package org.axonframework.common;

import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Marc Gathier
 */
class ProcessUtilsTest {

    @Test
    void executeWithRetry() {
        AtomicLong retryCounter = new AtomicLong();

        ProcessUtils.executeWithRetry(() -> {
                                          if (retryCounter.getAndIncrement() < 5) {
                                              throw new IllegalArgumentException("Waiting for 5");
                                          }
                                      }, e -> ExceptionUtils.findException(e, IllegalArgumentException.class).isPresent(),
                                      100, TimeUnit.MILLISECONDS, 10);

        assertEquals(6, retryCounter.get());
    }

    @Test
    void executeWithRetryStops() {
        AtomicLong retryCounter = new AtomicLong();

        assertThrows(IllegalArgumentException.class, () ->
                ProcessUtils.executeWithRetry(() -> {
                            if (retryCounter.getAndIncrement() < 11) {
                                throw new IllegalArgumentException("Waiting for 11");
                            }
                        },
                        e -> ExceptionUtils.findException(e, IllegalArgumentException.class).isPresent(),
                        100,
                        TimeUnit.MILLISECONDS,
                        10)
        );
    }

    @Test
    void executeWithRetryImmediatelyStopsOnOther() {
        AtomicLong retryCounter = new AtomicLong();

        try {
            ProcessUtils.executeWithRetry(() -> {
                if (retryCounter.getAndIncrement() < 11) {
                    throw new IllegalArgumentException("Waiting for 11");
                }
            }, e -> false, 100, TimeUnit.MILLISECONDS, 10);
            fail("Should not get here");
        } catch (Exception ex) {
            assertTrue(ex instanceof IllegalArgumentException);
            assertEquals(1, retryCounter.get());
        }
    }

    @Test
    void executeUntilTrueRetries(){
        AtomicLong retryCounter = new AtomicLong();
        ProcessUtils.executeUntilTrue(() -> retryCounter.getAndIncrement() >= 1, 10L, 10L);
        assertEquals(2, retryCounter.get());
    }

    @Test
    void executeUntilTrueThrowsWhenMaxRetriesReached(){
        AtomicLong retryCounter = new AtomicLong();
        assertThrows(ProcessRetriesExhaustedException.class, () ->
                ProcessUtils.executeUntilTrue(() -> retryCounter.getAndIncrement() >= 100, 1L, 10L)
        );
        assertEquals(10, retryCounter.get());
    }
}