package org.axonframework.commandhandling.gateway;

import org.junit.*;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.axonframework.commandhandling.gateway.IntervalRetrySchedulerTest.doScheduleRetry;
import static org.junit.Assert.*;

public class ExponentialBackOffIntervalRetrySchedulerTest {

    private static final int BACKOFF_FACTOR = 100;
    private static final int MAX_RETRIES = 6;

    private ExponentialBackOffIntervalRetryScheduler retryScheduler;

    @Before
    public void setup() {
        retryScheduler = ExponentialBackOffIntervalRetryScheduler
                .builder()
                .retryExecutor(new ScheduledThreadPoolExecutor(1))
                .backoffFactor(BACKOFF_FACTOR)
                .maxRetryCount(MAX_RETRIES)
                .build();
    }

    @Test
    public void scheduleRetry() {
        for (int nrOfFailures = 1; nrOfFailures <= MAX_RETRIES; nrOfFailures++) {
            long delay = BACKOFF_FACTOR * (1L << (nrOfFailures - 1));

            assertTrue("Scheduling a retry should wait the required delay.",
                       doScheduleRetry(retryScheduler, nrOfFailures) >= delay);
        }

        assertEquals("Scheduling a retry when past maxRetryCount should have failed.",
                     0, doScheduleRetry(retryScheduler, MAX_RETRIES + 1));
    }
}