/*
 * Copyright (c) 2010-2023. Axon Framework
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

import com.codahale.metrics.Clock;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.Snapshot;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

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
    private final Metric capacity;

    /**
     * Creates a capacity monitor with the default time window 10 minutes
     */
    public CapacityMonitor() {
        this(10, TimeUnit.MINUTES);
    }

    /**
     * Creates a capacity monitor with the default time window 10 minutes
     *
     * @param window The length of the window to measure the capacity over
     * @param timeUnit The time unit of the time window
     */
    public CapacityMonitor(long window, TimeUnit timeUnit) {
        this(window, timeUnit, Clock.defaultClock());
    }

    /**
     * Creates a capacity monitor with the given time window. Uses the provided clock
     * to measure process time per message.
     *
     * @param window The length of the window to measure the capacity over
     * @param timeUnit The time unit of the time window
     * @param clock The clock used to measure the process time per message
     */
    public CapacityMonitor(long window, TimeUnit timeUnit, Clock clock) {
        SlidingTimeWindowReservoir slidingTimeWindowReservoir = new SlidingTimeWindowReservoir(window, timeUnit, clock);
        this.processedDurationHistogram = new Histogram(slidingTimeWindowReservoir);
        this.timeUnit = timeUnit;
        this.window = window;
        this.clock = clock;
        this.capacity = new CapacityGauge();
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message<?> message) {
        final long start = clock.getTime();
        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                processedDurationHistogram.update(clock.getTime() - start);
            }

            @Override
            public void reportFailure(Throwable cause) {
                processedDurationHistogram.update(clock.getTime() - start);
            }

            @Override
            public void reportIgnored() {
                processedDurationHistogram.update(clock.getTime() - start);
            }
        };
    }

    @Override
    public Map<String, Metric> getMetrics() {
        Map<String, Metric> metrics = new HashMap<>();
        metrics.put("capacity", capacity);
        return metrics;
    }

    private class CapacityGauge implements Gauge<Double> {
        @Override
        public Double getValue() {
            Snapshot snapshot = processedDurationHistogram.getSnapshot();
            double meanProcessTime = snapshot.getMean();
            int numProcessed = snapshot.getValues().length;
            return  (numProcessed * meanProcessTime) / timeUnit.toMillis(window);
        }
    }
}
