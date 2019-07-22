package org.axonframework.common;

import org.junit.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * @author Marc Gathier
 */
public class ProcessUtilsTest {

    @Test
    public void executeWithRetry() {
        AtomicLong retryCounter = new AtomicLong();

        ProcessUtils.executeWithRetry(() -> {
                                          if (retryCounter.getAndIncrement() < 5) {
                                              throw new IllegalArgumentException("Waiting for 5");
                                          }
                                      }, e -> ExceptionUtils.findException(e, IllegalArgumentException.class).isPresent(),
                                      100, TimeUnit.MILLISECONDS, 10);

        assertEquals(6, retryCounter.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void executeWithRetryStops() {
        AtomicLong retryCounter = new AtomicLong();

        ProcessUtils.executeWithRetry(() -> {
                                          if (retryCounter.getAndIncrement() < 11) {
                                              throw new IllegalArgumentException("Waiting for 11");
                                          }
                                      },
                                      e -> ExceptionUtils.findException(e, IllegalArgumentException.class).isPresent(),
                                      100,
                                      TimeUnit.MILLISECONDS,
                                      10);
    }

    @Test
    public void executeWithRetryImmediatelyStopsOnOther() {
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
}