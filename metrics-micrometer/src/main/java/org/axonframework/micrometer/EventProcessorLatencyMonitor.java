/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitorCallback;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Measures the difference in message timestamps between the last ingested and the last processed message.
 *
 * @author Marijn van Zelst
 * @author Ivan Dugalic
 * @author Allard Buijze
 * @since 4.1
 */
public class EventProcessorLatencyMonitor implements MessageMonitor<EventMessage<?>> {


    private final String meterNamePrefix;
    private final MeterRegistry meterRegistry;
    private final Function<Message<?>, Iterable<Tag>> tagsBuilder;
    private final Clock clock;
    private final ConcurrentMap<Tags, AtomicLong> gauges = new ConcurrentHashMap<>();

    private EventProcessorLatencyMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {
        this(meterNamePrefix,
             meterRegistry,
             message -> Tags.empty(),
             Clock.SYSTEM);
    }

    /**
     * Initialize the monitor with given properties.
     *
     * @param meterNamePrefix The prefix to use on this meter. It will be suffixed with ".latency".
     * @param meterRegistry   The registry to register this meter with
     * @param tagsBuilder     The function that provides the Tags to measure latency under for each message
     * @param clock           The clock to use to measure time
     */
    protected EventProcessorLatencyMonitor(String meterNamePrefix,
                                           MeterRegistry meterRegistry,
                                           Function<Message<?>, Iterable<Tag>> tagsBuilder,
                                           Clock clock) {
        this.meterNamePrefix = meterNamePrefix;
        this.meterRegistry = meterRegistry;
        this.tagsBuilder = tagsBuilder;
        this.clock = clock;
    }

    /**
     * Creates an event processor latency monitor
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @return The created event processor latency monitor
     */
    public static EventProcessorLatencyMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {
        return new EventProcessorLatencyMonitor(meterNamePrefix, meterRegistry);
    }

    /**
     * Creates an event processor latency monitor.
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @param tagsBuilder     The function used to construct the list of micrometer {@link Tag}, based on the ingested
     *                        message
     * @return The created event processor latency monitor
     */
    public static EventProcessorLatencyMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry,
                                                            Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return new EventProcessorLatencyMonitor(meterNamePrefix, meterRegistry, tagsBuilder, Clock.SYSTEM);
    }

    @SuppressWarnings("PackageAccessibility")
    @Override
    public MonitorCallback onMessageIngested(EventMessage<?> message) {
        if (message != null) {
            Tags tags = Tags.of(tagsBuilder.apply(message));
            AtomicLong actualCounter = gauges.computeIfAbsent(tags, k -> new AtomicLong());
            actualCounter.set(clock.wallTime() - message.getTimestamp().toEpochMilli());

            Gauge.builder(meterNamePrefix + ".latency", actualCounter::get)
                 .tags(tags)
                 .register(meterRegistry);

        }
        return NoOpMessageMonitorCallback.INSTANCE;
    }

}
