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

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

class MessageTimerMonitorTest {

    private static final String PROCESSOR_NAME = "processorName";

    @Test
    void testSuccessMessage() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, testClock);
        MessageTimerMonitor testSubject = MessageTimerMonitor.buildMonitor(PROCESSOR_NAME, meterRegistry, testClock);
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.addSeconds(1);
        monitorCallback.reportSuccess();

        Timer all = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".allTimer").timer());
        Timer successTimer = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".successTimer").timer());
        Timer failureTimer = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".failureTimer").timer());
        Timer ignoredTimer = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".ignoredTimer").timer());

        assertEquals(1, all.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(1, successTimer.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(0, failureTimer.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(0, ignoredTimer.totalTime(TimeUnit.SECONDS), 0);
    }

    @Test
    void testFailureMessage() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, testClock);
        MessageTimerMonitor testSubject = MessageTimerMonitor.buildMonitor(PROCESSOR_NAME, meterRegistry, testClock);
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.addSeconds(1);
        monitorCallback.reportFailure(null);

        Timer all = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".allTimer").timer());
        Timer successTimer = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".successTimer").timer());
        Timer failureTimer = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".failureTimer").timer());
        Timer ignoredTimer = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".ignoredTimer").timer());

        assertEquals(1, all.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(0, successTimer.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(1, failureTimer.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(0, ignoredTimer.totalTime(TimeUnit.SECONDS), 0);
    }

    @Test
    void testIgnoredMessage() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, testClock);
        MessageTimerMonitor testSubject = MessageTimerMonitor.buildMonitor(PROCESSOR_NAME, meterRegistry, testClock);
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.addSeconds(1);
        monitorCallback.reportIgnored();

        Timer all = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".allTimer").timer());
        Timer successTimer = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".successTimer").timer());
        Timer failureTimer = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".failureTimer").timer());
        Timer ignoredTimer = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".ignoredTimer").timer());

        assertEquals(1, all.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(0, successTimer.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(0, failureTimer.totalTime(TimeUnit.SECONDS), 0);
        assertEquals(1, ignoredTimer.totalTime(TimeUnit.SECONDS), 0);
    }
}
