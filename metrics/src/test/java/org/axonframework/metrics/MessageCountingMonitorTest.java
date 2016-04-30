package org.axonframework.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MessageCountingMonitorTest {

    @Test
    public void testMessages(){
        MessageCountingMonitor testSubject = new MessageCountingMonitor();

        testSubject.onMessageIngested(null).onSuccess();
        testSubject.onMessageIngested(null).onFailure(null);

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Counter ingestedCounter = (Counter) metricSet.get("ingestedCounter");
        Counter processedCounter = (Counter) metricSet.get("processedCounter");
        Counter successCounter = (Counter) metricSet.get("successCounter");
        Counter failureCounter = (Counter) metricSet.get("failureCounter");

        assertEquals(2, ingestedCounter.getCount());
        assertEquals(2, processedCounter.getCount());
        assertEquals(1, successCounter.getCount());
        assertEquals(1, failureCounter.getCount());
    }
}