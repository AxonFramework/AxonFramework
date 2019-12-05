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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

class CapacityMonitorTest {

    @Test
    void testSingleThreadedCapacity() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(new SimpleConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public CountingMode mode() {
                return CountingMode.STEP;
            }
        }, testClock);
        CapacityMonitor testSubject = CapacityMonitor.buildMonitor("1", meterRegistry, 1, TimeUnit.SECONDS, testClock);

        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);

        testClock.addSeconds(1);

        monitorCallback.reportSuccess();

        Gauge capacityGauge = meterRegistry.get("1.capacity").gauge();
        assertEquals(1, capacityGauge.value(), 0);
    }

    @Test
    void testMultithreadedCapacity() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CapacityMonitor testSubject = CapacityMonitor.buildMonitor("1", meterRegistry, 1, TimeUnit.SECONDS, testClock);

        EventMessage<Object> foo = asEventMessage("foo");
        EventMessage<Object> bar = asEventMessage("bar");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar));

        testClock.addSeconds(1);

        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);

        Gauge capacityGauge = meterRegistry.get("1.capacity").gauge();
        assertEquals(2, capacityGauge.value(), 0);
    }

    @Test
    void testEmptyCapacity() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CapacityMonitor.buildMonitor("1", meterRegistry, 1, TimeUnit.SECONDS, testClock);
        Gauge capacityGauge = meterRegistry.get("1.capacity").gauge();
        assertEquals(0, capacityGauge.value(), 0);
    }
}
