/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Times allTimer messages, successful and failed messages
 *
 * @author Marijn van Zelst
 * @since 4.1
 */
public class MessageTimerMonitor implements MessageMonitor<Message<?>> {

    private final Timer allTimer;
    private final Timer successTimer;
    private final Timer failureTimer;
    private final Timer ignoredTimer;
    private final Clock clock;

    /**
     * Creates a message timer monitor
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @return the message timer monitor
     */
    public static MessageTimerMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {
        return buildMonitor(meterNamePrefix, meterRegistry, Clock.SYSTEM);
    }

    /**
     * Creates a message timer monitor
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @param clock           The clock used to measure the process time per message
     * @return the message timer monitor
     */
    public static MessageTimerMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry, Clock clock) {
        Timer allTimer = buildTimer(meterNamePrefix, "allTimer", meterRegistry);
        Timer successTimer = buildTimer(meterNamePrefix, "successTimer", meterRegistry);
        Timer failureTimer = buildTimer(meterNamePrefix, "failureTimer", meterRegistry);
        Timer ignoredTimer = buildTimer(meterNamePrefix, "ignoredTimer", meterRegistry);
        return new MessageTimerMonitor(allTimer, successTimer, failureTimer, ignoredTimer, clock);
    }

    private static Timer buildTimer(String meterNamePrefix, String timerName, MeterRegistry meterRegistry) {
        return Timer.builder(meterNamePrefix + "." + timerName)
                    .distributionStatisticExpiry(Duration.of(10, ChronoUnit.MINUTES))
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .register(meterRegistry);
    }

    private MessageTimerMonitor(Timer allTimer, Timer successTimer, Timer failureTimer, Timer ignoredTimer,
                                Clock clock) {
        this.allTimer = allTimer;
        this.successTimer = successTimer;
        this.failureTimer = failureTimer;
        this.ignoredTimer = ignoredTimer;
        this.clock = clock;
    }

    @Override
    public MonitorCallback onMessageIngested(Message<?> message) {
        long startTime = clock.monotonicTime();
        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                long duration = clock.monotonicTime() - startTime;
                allTimer.record(duration, TimeUnit.NANOSECONDS);
                successTimer.record(duration, TimeUnit.NANOSECONDS);
            }

            @Override
            public void reportFailure(Throwable cause) {
                long duration = clock.monotonicTime() - startTime;
                allTimer.record(duration, TimeUnit.NANOSECONDS);
                failureTimer.record(duration, TimeUnit.NANOSECONDS);
            }

            @Override
            public void reportIgnored() {
                long duration = clock.monotonicTime() - startTime;
                allTimer.record(duration, TimeUnit.NANOSECONDS);
                ignoredTimer.record(duration, TimeUnit.NANOSECONDS);
            }
        };
    }
}
