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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitorCallback;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link MessageMonitor} implementation dedicated to {@link EventMessage EventMessages}.
 * <p>
 * This monitor defines the latency between the {@link EventMessage#getTimestamp()} and the {@link Clock#wallTime()}.
 * Doing so, it depicts the latency from when an event was published compared to when an
 * {@link org.axonframework.eventhandling.EventProcessor} processes the event to clarify how far behind an
 * {@code EventProcessor} is.
 * <p>
 * Do note that a replay (as triggered through {@link StreamingEventProcessor#resetTokens()}, for example) will cause
 * this metric to bump up due to the processor handling old events.
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

    /**
     * Instantiate a Builder to be able to create a {@link EventProcessorLatencyMonitor}.
     * <p>
     * The {@code tagsBuilder} is defaulted to a {@link Function} returning {@link Tags#empty()} and the {@link Clock}
     * to a a {@link Clock#SYSTEM}. The {@code meterNamePrefix} and {@link MeterRegistry} are <b>hard requirements</b>
     * and as such should be provided.
     *
     * @return a Builder to be able to create a {@link EventProcessorLatencyMonitor}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link EventProcessorLatencyMonitor} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@code meterNamePrefix} and {@link MeterRegistry} are not {@code null} and will throw an
     * {@link org.axonframework.common.AxonConfigurationException} if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link EventProcessorLatencyMonitor} instance
     */
    protected EventProcessorLatencyMonitor(Builder builder) {
        builder.validate();
        this.meterNamePrefix = builder.meterNamePrefix;
        this.meterRegistry = builder.meterRegistry;
        this.tagsBuilder = builder.tagsBuilder;
        this.clock = builder.clock;
    }

    /**
     * Build an {@link EventProcessorLatencyMonitor}
     *
     * @param meterNamePrefix the prefix for the meter name that will be created in the given {@code meterRegistry}
     * @param meterRegistry   the meter registry used to create and register the meters
     * @return the created {@link EventProcessorLatencyMonitor}
     * @deprecated in favor of using the {@link #builder()}
     */
    @Deprecated
    public static EventProcessorLatencyMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {
        return builder().meterNamePrefix(meterNamePrefix)
                        .meterRegistry(meterRegistry)
                        .build();
    }

    /**
     * Build an {@link EventProcessorLatencyMonitor}.
     *
     * @param meterNamePrefix the prefix for the meter name that will be created in the given {@code meterRegistry}
     * @param meterRegistry   the meter registry used to create and register the meters
     * @param tagsBuilder     the function used to construct the list of micrometer {@link Tag}, based on the ingested
     *                        message
     * @return the created {@link EventProcessorLatencyMonitor}
     * @deprecated in favor of using the {@link #builder()}
     */
    @Deprecated
    public static EventProcessorLatencyMonitor buildMonitor(String meterNamePrefix,
                                                            MeterRegistry meterRegistry,
                                                            Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return builder().meterNamePrefix(meterNamePrefix)
                        .meterRegistry(meterRegistry)
                        .tagsBuilder(tagsBuilder)
                        .build();
    }

    @SuppressWarnings("PackageAccessibility")
    @Override
    public MonitorCallback onMessageIngested(@Nonnull EventMessage<?> message) {
        //noinspection ConstantConditions
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

    /**
     * Builder class to instantiate a {@link EventProcessorLatencyMonitor}.
     * <p>
     * The {@code tagsBuilder} is defaulted to a {@link Function} returning {@link Tags#empty()} and the {@link Clock}
     * to a a {@link Clock#SYSTEM}. The {@code meterNamePrefix} and {@link MeterRegistry} are <b>hard requirements</b>
     * and as such should be provided.
     */
    public static class Builder {

        private String meterNamePrefix;
        private MeterRegistry meterRegistry;
        private Function<Message<?>, Iterable<Tag>> tagsBuilder = message -> Tags.empty();
        private Clock clock = Clock.SYSTEM;

        /**
         * Sets the name used to prefix the names of the {@link Gauge} instances created by this
         * {@link MessageMonitor}.
         *
         * @param meterNamePrefix a {@link String} used to prefix the names of the {@link Gauge} instances created by
         *                        this {@link MessageMonitor}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder meterNamePrefix(String meterNamePrefix) {
            assertNonEmpty(meterNamePrefix, "The meter name prefix may not be null or empty");
            this.meterNamePrefix = meterNamePrefix;
            return this;
        }

        /**
         * Specifies the {@link MeterRegistry} used to registered the {@link Gauge} instances to.
         *
         * @param meterRegistry the {@link MeterRegistry} used to registered the {@link Gauge} instances to
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder meterRegistry(MeterRegistry meterRegistry) {
            assertNonNull(meterRegistry, "MeterRegistry may not be null");
            this.meterRegistry = meterRegistry;
            return this;
        }

        /**
         * Sets the {@link Clock} used to define the processing duration of a given message being pushed through this
         * {@link MessageMonitor}. Defaults to the {@link Clock#SYSTEM}.
         *
         * @param clock the {@link Clock} used to define the processing duration of a given message
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder clock(Clock clock) {
            assertNonNull(clock, "Clock may not be null");
            this.clock = clock;
            return this;
        }

        /**
         * Configures the {@link Function} used to deduce what the {@link Tag}s should be for a message being monitored.
         * Defaults to a {@link Function} returning {@link Tags#empty()}.
         *
         * @param tagsBuilder a {@link Function} used to deduce what the {@link Tag}s should be for a message being
         *                    monitored
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder tagsBuilder(Function<Message<?>, Iterable<Tag>> tagsBuilder) {
            assertNonNull(tagsBuilder, "TagsBuilder may not be null");
            this.tagsBuilder = tagsBuilder;
            return this;
        }

        /**
         * Initializes a {@link MessageTimerMonitor} as specified through this Builder.
         *
         * @return a {@link MessageTimerMonitor} as specified through this Builder
         */
        public EventProcessorLatencyMonitor build() {
            return new EventProcessorLatencyMonitor(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws org.axonframework.common.AxonConfigurationException if one field is asserted to be incorrect
         *                                                             according to the Builder's specifications
         */
        protected void validate() {
            assertNonEmpty(meterNamePrefix, "The meter name prefix is a hard requirement and should be provided");
            assertNonNull(meterRegistry, "The MeterRegistry is a hard requirement and should be provided");
        }
    }
}
