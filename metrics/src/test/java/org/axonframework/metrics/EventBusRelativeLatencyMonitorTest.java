package org.axonframework.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import org.axonframework.eventhandling.EventMessage;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventBusRelativeLatencyMonitorTest {

    @Test
    public void testMessages(){
        EventBusRelativeLatencyMonitor testSubject = new EventBusRelativeLatencyMonitor();
        EventMessage<?> firstEventMessage = mock(EventMessage.class);
        when(firstEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(0));

        EventMessage<?> secondEventMessage = mock(EventMessage.class);
        when(secondEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(1000));

        testSubject.onMessageIngested(firstEventMessage).onSuccess();
        testSubject.onMessageIngested(secondEventMessage);

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Gauge<Long> latency = (Gauge<Long>) metricSet.get("latency");

        assertEquals(1000, latency.getValue(), 0);
    }

    @Test
    public void testFailureMessage(){
        EventBusRelativeLatencyMonitor testSubject = new EventBusRelativeLatencyMonitor();
        EventMessage<?> firstEventMessage = mock(EventMessage.class);
        when(firstEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(0));

        EventMessage<?> secondEventMessage = mock(EventMessage.class);
        when(secondEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(1000));

        testSubject.onMessageIngested(firstEventMessage).onFailure(null);
        testSubject.onMessageIngested(secondEventMessage);

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Gauge<Long> latency = (Gauge<Long>) metricSet.get("latency");

        assertEquals(1000, latency.getValue(), 0);
    }

    @Test
    public void testNullMessage(){
        EventBusRelativeLatencyMonitor testSubject = new EventBusRelativeLatencyMonitor();
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        monitorCallback.onSuccess();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Gauge<Long> latency = (Gauge<Long>) metricSet.get("latency");

        assertEquals(0, latency.getValue(), 0);
    }
}