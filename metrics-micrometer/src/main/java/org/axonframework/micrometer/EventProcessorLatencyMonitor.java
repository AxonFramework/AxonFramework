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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitorCallback;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Measures the difference in message timestamps between the last ingested and the last processed message.
 *
 * @author Marijn van Zelst
 * @author Ivan Dugalic
 * @since 4.1
 */
public class EventProcessorLatencyMonitor implements MessageMonitor<EventMessage<?>> {


    private final String meterNamePrefix;
    private final MeterRegistry meterRegistry;
    private final Function<Message<?>, Iterable<Tag>> tagsBuilder;
    private final AtomicLong lastReceivedTime = new AtomicLong(-1);
    private final AtomicLong lastProcessedTime = new AtomicLong(-1);

    private EventProcessorLatencyMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {
        this(meterNamePrefix,
             meterRegistry,
             message -> Tags.empty());
    }

    private EventProcessorLatencyMonitor(String meterNamePrefix,
                                         MeterRegistry meterRegistry,
                                         Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        this.meterNamePrefix = meterNamePrefix;
        this.meterRegistry = meterRegistry;
        this.tagsBuilder = tagsBuilder;
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
        return new EventProcessorLatencyMonitor(meterNamePrefix, meterRegistry, tagsBuilder);
    }

    @Override
    public MonitorCallback onMessageIngested(EventMessage<?> message) {
        if (message == null) {
            return NoOpMessageMonitorCallback.INSTANCE;
        }
        final Iterable<Tag> tags = tagsBuilder.apply(message);
        Gauge.builder(meterNamePrefix + ".latency",
                      this,
                      EventProcessorLatencyMonitor::calculateLatency)
             .tags(tags)
             .register(meterRegistry);

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

            @Override
            public void reportIgnored() {
                update();
            }

            private void update() {
                updateIfMaxValue(lastProcessedTime, message.getTimestamp().toEpochMilli());
            }
        };
    }

    private long calculateLatency() {
        long lastProcessedTime = this.lastProcessedTime.longValue();
        long lastReceivedTime = this.lastReceivedTime.longValue();
        long processTime;
        if (lastReceivedTime == -1 || lastProcessedTime == -1) {
            processTime = 0;
        } else {
            processTime = lastReceivedTime - lastProcessedTime;
        }
        return processTime;
    }

    private void updateIfMaxValue(AtomicLong atomicLong, long timestamp) {
        atomicLong.accumulateAndGet(timestamp, (currentValue, newValue) ->
                newValue > currentValue ? newValue : currentValue);
    }
}
