/*
 * Copyright (c) 2010-2022. Axon Framework
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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitorCallback;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;

/**
 * A {@link MessageMonitor} implementation dedicated to {@link EventMessage EventMessages}.
 * <p>
 * This monitor defines the latency between the {@link EventMessage#getTimestamp()} and the {@link Clock#instant()}.
 * Doing so, it depicts the latency from when an event was published compared to when an
 * {@link org.axonframework.eventhandling.EventProcessor} processes the event to clarify how far behind an
 * {@code EventProcessor} is.
 * <p>
 * Do note that a replay (as triggered through {@link StreamingEventProcessor#resetTokens()}, for example) will cause
 * this metric to bump up due to the processor handling old events.
 *
 * @author Marijn van Zelst
 * @author Allard Buijze
 * @since 3.0
 */
public class EventProcessorLatencyMonitor implements MessageMonitor<EventMessage<?>>, MetricSet {

    private final Clock clock;
    private final AtomicLong processTime = new AtomicLong();

    /**
     * Construct an {@link EventProcessorLatencyMonitor} using a {@link Clock#systemUTC()}.
     */
    public EventProcessorLatencyMonitor() {
        this(Clock.systemUTC());
    }

    /**
     * Construct an {@link EventProcessorLatencyMonitor} using the given {@code clock}.
     *
     * @param clock defines the {@link Clock} used by this {@link MessageMonitor} implementation
     */
    public EventProcessorLatencyMonitor(Clock clock) {
        this.clock = clock;
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull EventMessage<?> message) {
        //noinspection ConstantConditions
        if (message != null) {
            this.processTime.set(Duration.between(message.getTimestamp(), clock.instant()).toMillis());
        }
        return NoOpMessageMonitorCallback.INSTANCE;
    }

    @Override
    public Map<String, Metric> getMetrics() {
        Map<String, Metric> metrics = new HashMap<>();
        metrics.put("latency", (Gauge<Long>) processTime::get); // NOSONAR
        return metrics;
    }
}
