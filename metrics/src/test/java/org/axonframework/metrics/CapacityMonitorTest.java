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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class CapacityMonitorTest {

    @Test
    void testSingleThreadedCapacity() {
        TestClock testClock = new TestClock();
        CapacityMonitor testSubject = new CapacityMonitor(1, TimeUnit.SECONDS, testClock);
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportSuccess();

        Map<String, Metric> metricSet = testSubject.getMetrics();
        Gauge<Double> capacityGauge = (Gauge<Double>) metricSet.get("capacity");
        assertEquals(1, capacityGauge.getValue(), 0);
    }

    @Test
    void testMultithreadedCapacity() {
        TestClock testClock = new TestClock();
        CapacityMonitor testSubject = new CapacityMonitor(1, TimeUnit.SECONDS, testClock);
        EventMessage<Object> foo = asEventMessage("foo");
        EventMessage<Object> bar = asEventMessage("bar");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar));
        testClock.increase(1000);
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);

        Map<String, Metric> metricSet = testSubject.getMetrics();
        Gauge<Double> capacityGauge = (Gauge<Double>) metricSet.get("capacity");
        assertEquals(2, capacityGauge.getValue(), 0);
    }

    @Test
    void testEmptyCapacity() {
        TestClock testClock = new TestClock();
        CapacityMonitor testSubject = new CapacityMonitor(1, TimeUnit.SECONDS, testClock);
        Map<String, Metric> metricSet = testSubject.getMetrics();
        Gauge<Double> capacityGauge = (Gauge<Double>) metricSet.get("capacity");
        assertEquals(0, capacityGauge.getValue(), 0);
    }
}
