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

        testSubject.onMessageIngested(null).reportSuccess();
        testSubject.onMessageIngested(null).reportFailure(null);
        testSubject.onMessageIngested(null).reportIgnored();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Counter ingestedCounter = (Counter) metricSet.get("ingestedCounter");
        Counter processedCounter = (Counter) metricSet.get("processedCounter");
        Counter successCounter = (Counter) metricSet.get("successCounter");
        Counter failureCounter = (Counter) metricSet.get("failureCounter");
        Counter ignoredCounter = (Counter) metricSet.get("ignoredCounter");

        assertEquals(3, ingestedCounter.getCount());
        assertEquals(2, processedCounter.getCount());
        assertEquals(1, successCounter.getCount());
        assertEquals(1, failureCounter.getCount());
        assertEquals(1, ignoredCounter.getCount());
    }
}