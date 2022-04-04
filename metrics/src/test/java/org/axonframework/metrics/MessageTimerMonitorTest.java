/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.Timer;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link MessageTimerMonitor}.
 *
 * @author Marijn van Zelst
 */
class MessageTimerMonitorTest {

    private TestClock testClock;
    private MessageTimerMonitor testSubject;

    @BeforeEach
    void setUp() {
        testClock = new TestClock();
        testSubject = MessageTimerMonitor.builder()
                                         .clock(testClock)
                                         .build();
    }

    @Test
    void testSuccessMessage() {
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportSuccess();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Timer all = (Timer) metricSet.get("allTimer");
        Timer successTimer = (Timer) metricSet.get("successTimer");
        Timer failureTimer = (Timer) metricSet.get("failureTimer");
        Timer ignoredTimer = (Timer) metricSet.get("ignoredTimer");

        assertArrayEquals(new long[]{1000000}, all.getSnapshot().getValues());
        assertArrayEquals(new long[]{1000000}, successTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, failureTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, ignoredTimer.getSnapshot().getValues());
    }

    @Test
    void testFailureMessage() {
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportFailure(null);

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Timer all = (Timer) metricSet.get("allTimer");
        Timer successTimer = (Timer) metricSet.get("successTimer");
        Timer failureTimer = (Timer) metricSet.get("failureTimer");
        Timer ignoredTimer = (Timer) metricSet.get("ignoredTimer");

        assertArrayEquals(new long[]{1000000}, all.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, successTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, ignoredTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{1000000}, failureTimer.getSnapshot().getValues());
    }

    @Test
    void testIgnoredMessage() {
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportIgnored();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Timer all = (Timer) metricSet.get("allTimer");
        Timer successTimer = (Timer) metricSet.get("successTimer");
        Timer failureTimer = (Timer) metricSet.get("failureTimer");
        Timer ignoredTimer = (Timer) metricSet.get("ignoredTimer");

        assertArrayEquals(new long[]{1000000}, all.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, successTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{1000000}, ignoredTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, failureTimer.getSnapshot().getValues());
    }

    /**
     * A different {@link com.codahale.metrics.Reservoir} is used, which simply means the storage approach of the metric
     * histogram is adjusted. Within the test space, this still renders the same results, hence the similar assertion as
     * in {@link #testSuccessMessage()}.
     */
    @Test
    void testCustomReservoir() {
        MessageTimerMonitor customReservoirTestSubject =
                MessageTimerMonitor.builder()
                                   .clock(testClock)
                                   .reservoirFactory(() -> new SlidingTimeWindowReservoir(2000, TimeUnit.MILLISECONDS))
                                   .build();

        MessageMonitor.MonitorCallback result = customReservoirTestSubject.onMessageIngested(null);
        testClock.increase(1000);
        result.reportSuccess();

        Map<String, Metric> metrics = customReservoirTestSubject.getMetrics();
        Timer all = (Timer) metrics.get("allTimer");
        Timer successTimer = (Timer) metrics.get("successTimer");
        Timer failureTimer = (Timer) metrics.get("failureTimer");
        Timer ignoredTimer = (Timer) metrics.get("ignoredTimer");

        assertArrayEquals(new long[]{1000000}, all.getSnapshot().getValues());
        assertArrayEquals(new long[]{1000000}, successTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, failureTimer.getSnapshot().getValues());
        assertArrayEquals(new long[]{}, ignoredTimer.getSnapshot().getValues());
    }

    @Test
    void testBuildWithNullClockThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.clock(null));
    }

    @Test
    void testBuildWithNullReservoirFactoryThrowsAxonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.reservoirFactory(null));
    }
}
