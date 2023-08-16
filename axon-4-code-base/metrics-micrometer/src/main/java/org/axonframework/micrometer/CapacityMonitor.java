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

package org.axonframework.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.axonframework.messaging.Message;
import org.axonframework.micrometer.reservoir.SlidingTimeWindowReservoir;
import org.axonframework.monitoring.MessageMonitor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Calculates capacity by tracking, within the configured time window, the average message processing time
 * and multiplying that by the amount of messages processed.
 * <p>
 * The capacity can be more than 1 if the monitored
 * message handler processes the messages in parallel. The capacity for a single threaded message handler will be
 * a value between 0 and 1.
 * <p>
 * If the value for a single threaded message handler is 1 the component is active 100% of the time. This means
 * that messages will have to wait to be processed.
 *
 * @author Marijn van Zelst
 * @author Ivan Dugalic
 * @since 4.1
 */
public class CapacityMonitor implements MessageMonitor<Message<?>> {

    private final Map<String, SlidingTimeWindowReservoir> timeWindowedDurationMeasurementsMap;
    private final TimeUnit timeUnit;
    private final Clock clock;
    private final long window;
    private final String meterNamePrefix;
    private final MeterRegistry meterRegistry;
    private final Function<Message<?>, Iterable<Tag>> tagsBuilder;


    /**
     * Creates a capacity monitor with the default time window 10 minutes
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @return The created capacity monitor
     */
    public static CapacityMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {
        return buildMonitor(meterNamePrefix, meterRegistry, 10, TimeUnit.MINUTES);
    }

    /**
     * Creates a capacity monitor with the default time window 10 minutes
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @param tagsBuilder     The function used to construct the list of micrometer {@link Tag}, based on the ingested
     *                        message
     * @return The created capacity monitor
     */
    public static CapacityMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry,
                                               Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return buildMonitor(meterNamePrefix, meterRegistry, 10, TimeUnit.MINUTES, tagsBuilder);
    }

    /**
     * Creates a capacity monitor with the default system clock.
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @param window          The length of the window to measure the capacity over
     * @param timeUnit        The temporal unit of the time window
     * @return The created capacity monitor
     */
    public static CapacityMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry, long window,
                                               TimeUnit timeUnit) {
        return buildMonitor(meterNamePrefix, meterRegistry, window, timeUnit, Clock.SYSTEM);
    }

    /**
     * Creates a capacity monitor with the default system clock.
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @param window          The length of the window to measure the capacity over
     * @param timeUnit        The temporal unit of the time window
     * @param tagsBuilder     The function used to construct the list of micrometer {@link Tag}, based on the ingested
     *                        message
     * @return The created capacity monitor
     */
    public static CapacityMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry, long window,
                                               TimeUnit timeUnit, Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return buildMonitor(meterNamePrefix, meterRegistry, window, timeUnit, Clock.SYSTEM, tagsBuilder);
    }

    /**
     * Creates a capacity monitor with the given time window. Uses the provided clock
     * to measure process time per message.
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @param window          The length of the window to measure the capacity over
     * @param timeUnit        The temporal unit of the time window
     * @param clock           The clock used to measure the process time per message
     * @return The created capacity monitor
     */
    public static CapacityMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry, long window,
                                               TimeUnit timeUnit, Clock clock) {

        return new CapacityMonitor(window, timeUnit, clock, meterNamePrefix, meterRegistry);
    }

    /**
     * Creates a capacity monitor with the given time window. Uses the provided clock
     * to measure process time per message.
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @param window          The length of the window to measure the capacity over
     * @param timeUnit        The temporal unit of the time window
     * @param clock           The clock used to measure the process time per message
     * @param tagsBuilder     The function used to construct the list of micrometer {@link Tag}, based on the ingested
     *                        message
     * @return The created capacity monitor
     */
    public static CapacityMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry, long window,
                                               TimeUnit timeUnit, Clock clock,
                                               Function<Message<?>, Iterable<Tag>> tagsBuilder) {

        return new CapacityMonitor(window, timeUnit, clock, meterNamePrefix, meterRegistry, tagsBuilder);
    }


    private CapacityMonitor(long window, TimeUnit timeUnit, Clock clock, String meterNamePrefix,
                            MeterRegistry meterRegistry) {
        this(window,
             timeUnit,
             clock,
             meterNamePrefix,
             meterRegistry,
             message -> Tags.empty());
    }

    private CapacityMonitor(long window, TimeUnit timeUnit, Clock clock, String meterNamePrefix,
                            MeterRegistry meterRegistry,
                            Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        this.timeWindowedDurationMeasurementsMap = new ConcurrentHashMap<>();
        this.timeUnit = timeUnit;
        this.clock = clock;
        this.window = window;
        this.meterNamePrefix = meterNamePrefix;
        this.meterRegistry = meterRegistry;
        this.tagsBuilder = tagsBuilder;
    }

    private SlidingTimeWindowReservoir createIfAbsent(String meterNamePrefix, Tags tags, long window, TimeUnit timeUnit,
                                                      Clock clock) {
        String key = meterNamePrefix + tags.stream()
                                           .map(tag -> tag.getKey() + tag.getValue())
                                           .reduce(String::concat)
                                           .orElse("");
        timeWindowedDurationMeasurementsMap.putIfAbsent(key, new SlidingTimeWindowReservoir(window, timeUnit, clock));
        return timeWindowedDurationMeasurementsMap.get(key);
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message<?> message) {
        final Iterable<Tag> tags = tagsBuilder.apply(message);
        final SlidingTimeWindowReservoir timeWindowedDurationMeasurements = createIfAbsent(meterNamePrefix,
                                                                                           Tags.of(tags),
                                                                                           this.window,
                                                                                           this.timeUnit,
                                                                                           this.clock);
        meterRegistry.gauge(meterNamePrefix + ".capacity",
                            tags,
                            this,
                            value -> calculateCapacity(timeWindowedDurationMeasurements));

        final long start = clock.monotonicTime();

        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                timeWindowedDurationMeasurements.update(clock.monotonicTime() - start);
            }

            @Override
            public void reportFailure(Throwable cause) {
                timeWindowedDurationMeasurements.update(clock.monotonicTime() - start);
            }

            @Override
            public void reportIgnored() {
                timeWindowedDurationMeasurements.update(clock.monotonicTime() - start);
            }
        };
    }

    private double calculateCapacity(SlidingTimeWindowReservoir timeWindowedDurationMeasurements) {
        long totalProcessTime = timeWindowedDurationMeasurements.getMeasurements().stream().reduce(0L, Long::sum);
        return (double) totalProcessTime / timeUnit.toNanos(window);
    }
}
