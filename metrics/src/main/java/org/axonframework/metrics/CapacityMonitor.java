package org.axonframework.metrics;

import com.codahale.metrics.*;
import org.axonframework.messaging.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Calculates capacity by tracking, within the configured time window, the average message processing time
 * and multiplying that by the amount of messages processed.
 *
 * The capacity can be more than 1 if the monitored
 * message handler processes the messages in parallel. The capacity for a single threaded message handler will be
 * a value between 0 and 1.
 *
 * If the value for a single threaded message handler is 1 the component is active 100% of the time. This means
 * that messages will have to wait to be processed.
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public class CapacityMonitor implements MessageMonitor<Message<?>>, MetricSet {

    private final Histogram processedDurationHistogram;
    private final TimeUnit timeUnit;
    private final long window;
    private final Clock clock;
    private final Metric ratio;

    public CapacityMonitor(long window, TimeUnit timeUnit) {
        this(window, timeUnit, Clock.defaultClock());
    }

    CapacityMonitor(long window, TimeUnit timeUnit, Clock clock) {
        SlidingTimeWindowReservoir slidingTimeWindowReservoir = new SlidingTimeWindowReservoir(window, timeUnit, clock);
        this.processedDurationHistogram = new Histogram(slidingTimeWindowReservoir);
        this.timeUnit = timeUnit;
        this.window = window;
        this.clock = clock;
        this.ratio = new RatioGauge();
    }

    @Override
    public MonitorCallback onMessageIngested(Message<?> message) {
        final long start = clock.getTime();
        return new MonitorCallback() {
            @Override
            public void onSuccess() {
                processedDurationHistogram.update(clock.getTime() - start);
            }

            @Override
            public void onFailure(Optional<Throwable> cause) {
                processedDurationHistogram.update(clock.getTime() - start);
            }
        };
    }

    @Override
    public Map<String, Metric> getMetrics() {
        Map<String, Metric> metrics = new HashMap<>();
        metrics.put("ratio", ratio);
        return metrics;
    }

    private class RatioGauge implements Gauge<Double> {
        @Override
        public Double getValue() {
            Snapshot snapshot = processedDurationHistogram.getSnapshot();
            double meanProcessTime = snapshot.getMean();
            int numProcessed = snapshot.getValues().length;
            return  (numProcessed * meanProcessTime) / timeUnit.toMillis(window);
        }
    }
}