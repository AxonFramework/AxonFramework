package org.axonframework.commandhandling.gateway;

import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.*;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.axonframework.commandhandling.gateway.IntervalRetrySchedulerTest.doScheduleRetry;
import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackOffIntervalRetrySchedulerTest {

    private static final int BACKOFF_FACTOR = 100;
    private static final int MAX_RETRIES = 6;

    private ExponentialBackOffIntervalRetryScheduler retryScheduler;

    @BeforeEach
    void setup() {
        retryScheduler = ExponentialBackOffIntervalRetryScheduler
                .builder()
                .retryExecutor(new ScheduledThreadPoolExecutor(1))
                .backoffFactor(BACKOFF_FACTOR)
                .maxRetryCount(MAX_RETRIES)
                .build();
    }

    @Test
    void scheduleRetry() {
        for (int nrOfFailures = 1; nrOfFailures <= MAX_RETRIES; nrOfFailures++) {
            long delay = BACKOFF_FACTOR * (1L << (nrOfFailures - 1));

            assertTrue(doScheduleRetry(retryScheduler, nrOfFailures) >= delay,
                    "Scheduling a retry should wait the required delay.");
        }

        assertEquals(0, doScheduleRetry(retryScheduler, MAX_RETRIES + 1),
                "Scheduling a retry when past maxRetryCount should have failed.");
    }

    @Test
    void buildingWhilstMissingScheduledExecutorServiceThrowsConfigurationException() {
        ExponentialBackOffIntervalRetryScheduler.Builder builder = ExponentialBackOffIntervalRetryScheduler.builder();
        assertThrows(AxonConfigurationException.class, builder::build);
    }
}