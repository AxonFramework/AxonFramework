package org.axonframework.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import org.axonframework.eventhandling.EventMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures the difference in message timestamps between the last ingested and the last processed message.
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public class EventProcessorLatencyMonitor implements MessageMonitor<EventMessage<?>>, MetricSet {

    private final AtomicLong lastReceivedTime = new AtomicLong(-1);
    private final AtomicLong lastProcessedTime = new AtomicLong(-1);

    private final static NoOpMessageMonitorCallback NO_OP_MESSAGE_MONITOR_CALLBACK = new NoOpMessageMonitorCallback();

    @Override
    public MonitorCallback onMessageIngested(EventMessage<?> message) {
        if(message == null){
            return NO_OP_MESSAGE_MONITOR_CALLBACK;
        }
        updateIfMaxValue(lastReceivedTime, message.getTimestamp().toEpochMilli());
        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                update();
            }

            @Override
            public void reportFailure(Throwable cause) {
                update();
            }

            private void update(){
                updateIfMaxValue(lastProcessedTime, message.getTimestamp().toEpochMilli());
            }
        };
    }

    @Override
    public Map<String, Metric> getMetrics() {
        long lastProcessedTime = this.lastProcessedTime.longValue();
        long lastReceivedTime = this.lastReceivedTime.longValue();
        long processTime;
        if(lastReceivedTime == -1 || lastProcessedTime == -1){
            processTime = 0;
        } else {
            processTime = lastReceivedTime - lastProcessedTime;
        }
        Map<String, Metric> metrics = new HashMap<>();
        metrics.put("latency", (Gauge<Long>) () -> processTime);
        return metrics;
    }

    private void updateIfMaxValue(AtomicLong atomicLong, long timestamp){
        atomicLong.accumulateAndGet(timestamp, (currentValue, newValue) -> newValue > currentValue ? newValue : currentValue);
    }
}
