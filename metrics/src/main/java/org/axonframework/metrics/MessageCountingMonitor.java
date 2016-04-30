package org.axonframework.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import org.axonframework.messaging.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Counts the number of ingested, successful, failed and processed messages
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public class MessageCountingMonitor implements MessageMonitor<Message<?>>, MetricSet {

    private final Counter ingestedCounter = new Counter();
    private final Counter successCounter = new Counter();
    private final Counter failureCounter = new Counter();
    private final Counter processedCounter = new Counter();

    @Override
    public MonitorCallback onMessageIngested(Message<?> message) {
        ingestedCounter.inc();
        return new MessageMonitor.MonitorCallback() {
            @Override
            public void onSuccess() {
                processedCounter.inc();
                successCounter.inc();
            }

            @Override
            public void onFailure(Optional<Throwable> cause) {
                processedCounter.inc();
                failureCounter.inc();
            }
        };
    }

    @Override
    public Map<String, Metric> getMetrics() {
        Map<String, Metric> metricSet = new HashMap<>();
        metricSet.put("ingestedCounter", ingestedCounter);
        metricSet.put("processedCounter", processedCounter);
        metricSet.put("successCounter", successCounter);
        metricSet.put("failureCounter", failureCounter);
        return metricSet;
    }


}