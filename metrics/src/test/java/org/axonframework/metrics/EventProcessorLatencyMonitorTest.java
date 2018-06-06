/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class EventProcessorLatencyMonitorTest {

    @Test
    public void testMessages(){
        EventProcessorLatencyMonitor testSubject = new EventProcessorLatencyMonitor();
        EventMessage<?> firstEventMessage = mock(EventMessage.class);
        when(firstEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(0));

        EventMessage<?> secondEventMessage = mock(EventMessage.class);
        when(secondEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(1000));

        Map<? super EventMessage<?>, MessageMonitor.MonitorCallback> callbacks = testSubject.onMessagesIngested(Arrays.asList(firstEventMessage, secondEventMessage));
        callbacks.get(firstEventMessage).reportSuccess();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Gauge<Long> latency = (Gauge<Long>) metricSet.get("latency");

        assertEquals(1000, latency.getValue(), 0);
    }

    @Test
    public void testFailureMessage(){
        EventProcessorLatencyMonitor testSubject = new EventProcessorLatencyMonitor();
        EventMessage<?> firstEventMessage = mock(EventMessage.class);
        when(firstEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(0));

        EventMessage<?> secondEventMessage = mock(EventMessage.class);
        when(secondEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(1000));

        Map<? super EventMessage<?>, MessageMonitor.MonitorCallback> callbacks = testSubject.onMessagesIngested(Arrays.asList(firstEventMessage, secondEventMessage));
        callbacks.get(firstEventMessage).reportFailure(null);

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Gauge<Long> latency = (Gauge<Long>) metricSet.get("latency");

        assertEquals(1000, latency.getValue(), 0);
    }

    @Test
    public void testNullMessage(){
        EventProcessorLatencyMonitor testSubject = new EventProcessorLatencyMonitor();
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        monitorCallback.reportSuccess();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Gauge<Long> latency = (Gauge<Long>) metricSet.get("latency");

        assertEquals(0, latency.getValue(), 0);
    }
}
