/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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