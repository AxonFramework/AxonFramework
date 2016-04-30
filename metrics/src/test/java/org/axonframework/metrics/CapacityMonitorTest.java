package org.axonframework.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class CapacityMonitorTest {

    @Test
    public void testSingleThreadedCapacity() {
        TestClock testClock = new TestClock();
        CapacityMonitor testSubject = new CapacityMonitor(1, TimeUnit.SECONDS, testClock);
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.onSuccess();

        Map<String, Metric> metricSet = testSubject.getMetrics();
        Gauge<Double> capacityGauge = (Gauge<Double>) metricSet.get("ratio");
        assertEquals(1, capacityGauge.getValue(), 0);
    }

    @Test
    public void testMultithreadedCapacity(){
        TestClock testClock = new TestClock();
        CapacityMonitor testSubject = new CapacityMonitor(1, TimeUnit.SECONDS, testClock);
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        MessageMonitor.MonitorCallback monitorCallback2 = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.onSuccess();
        monitorCallback2.onFailure(null);

        Map<String, Metric> metricSet = testSubject.getMetrics();
        Gauge<Double> capacityGauge = (Gauge<Double>) metricSet.get("ratio");
        assertEquals(2, capacityGauge.getValue(), 0);
    }

    @Test
    public void testEmptyCapacity(){
        TestClock testClock = new TestClock();
        CapacityMonitor testSubject = new CapacityMonitor(1, TimeUnit.SECONDS, testClock);
        Map<String, Metric> metricSet = testSubject.getMetrics();
        Gauge<Double> capacityGauge = (Gauge<Double>) metricSet.get("ratio");
        assertEquals(0, capacityGauge.getValue(), 0);
    }

}