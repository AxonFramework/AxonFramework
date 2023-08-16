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
import io.micrometer.core.instrument.Timer;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link MessageMonitor} which introduces a {@link Timer} for the overall timer of all {@link Message}s being
 * ingested, as well as a success, failure and ignored {@code Timer}.
 *
 * @author Marijn van Zelst
 * @author Ivan Dugalic
 * @since 4.1
 */
public class MessageTimerMonitor implements MessageMonitor<Message<?>> {

    private final String meterNamePrefix;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Function<Message<?>, Iterable<Tag>> tagsBuilder;
    private final UnaryOperator<Timer.Builder> timerCustomization;

    /**
     * Instantiate a Builder to be able to create a {@link MessageTimerMonitor}.
     * <p>
     * The {@link Clock} is defaulted to a {@link Clock#SYSTEM}, the {@code tagsBuilder} to a {@link Function} returning
     * {@link Tags#empty()} and the {@code timerCustomization} to a no-op. The {@code meterNamePrefix} and {@link
     * MeterRegistry} are <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link MessageTimerMonitor}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link MessageTimerMonitor} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@code meterNamePrefix} and {@link MeterRegistry} are not {@code null} and will throw an
     * {@link AxonConfigurationException} if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MessageTimerMonitor} instance
     */
    protected MessageTimerMonitor(Builder builder) {
        builder.validate();
        this.meterNamePrefix = builder.meterNamePrefix;
        this.meterRegistry = builder.meterRegistry;
        this.clock = builder.clock;
        this.tagsBuilder = builder.tagsBuilder;
        this.timerCustomization = builder.timerCustomization;
    }

    /**
     * Creates a message timer monitor.
     *
     * @param meterNamePrefix the prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   the meter registry used to create and register the meters
     * @return the message timer monitor
     * @deprecated in favor of using the {@link #builder()}
     */
    @Deprecated
    public static MessageTimerMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {
        return buildMonitor(meterNamePrefix, meterRegistry, Clock.SYSTEM);
    }

    /**
     * Creates a message timer monitor.
     *
     * @param meterNamePrefix the prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   the meter registry used to create and register the meters
     * @param tagsBuilder     the function used to construct the list of micrometer {@link Tag}, based on the ingested
     *                        message
     * @return the message timer monitor
     * @deprecated in favor of using the {@link #builder()}
     */
    @Deprecated
    public static MessageTimerMonitor buildMonitor(String meterNamePrefix,
                                                   MeterRegistry meterRegistry,
                                                   Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return buildMonitor(meterNamePrefix, meterRegistry, Clock.SYSTEM, tagsBuilder);
    }

    /**
     * Creates a message timer monitor.
     *
     * @param meterNamePrefix the prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   the meter registry used to create and register the meters
     * @param clock           the clock used to measure the process time per message
     * @return the message timer monitor
     * @deprecated in favor of using the {@link #builder()}
     */
    @Deprecated
    public static MessageTimerMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry, Clock clock) {
        return buildMonitor(meterNamePrefix, meterRegistry, clock, message -> Tags.empty());
    }

    /**
     * Creates a message timer monitor.
     *
     * @param meterNamePrefix the prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   the meter registry used to create and register the meters
     * @param tagsBuilder     the function used to construct the list of micrometer {@link Tag}, based on the ingested
     *                        message
     * @param clock           the clock used to measure the process time per message
     * @return the message timer monitor
     * @deprecated in favor of using the {@link #builder()}
     */
    @Deprecated
    public static MessageTimerMonitor buildMonitor(String meterNamePrefix,
                                                   MeterRegistry meterRegistry,
                                                   Clock clock,
                                                   Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        return builder().meterNamePrefix(meterNamePrefix)
                        .meterRegistry(meterRegistry)
                        .clock(clock)
                        .tagsBuilder(tagsBuilder)
                        .build();
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message<?> message) {
        Iterable<Tag> tags = tagsBuilder.apply(message);
        Timer allTimer = buildTimer(meterNamePrefix, "allTimer", meterRegistry, tags, timerCustomization);
        Timer successTimer = buildTimer(meterNamePrefix, "successTimer", meterRegistry, tags, timerCustomization);
        Timer failureTimer = buildTimer(meterNamePrefix, "failureTimer", meterRegistry, tags, timerCustomization);
        Timer ignoredTimer = buildTimer(meterNamePrefix, "ignoredTimer", meterRegistry, tags, timerCustomization);

        long startTime = clock.monotonicTime();

        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                long duration = clock.monotonicTime() - startTime;
                allTimer.record(duration, TimeUnit.NANOSECONDS);
                successTimer.record(duration, TimeUnit.NANOSECONDS);
            }

            @Override
            public void reportFailure(Throwable cause) {
                long duration = clock.monotonicTime() - startTime;
                allTimer.record(duration, TimeUnit.NANOSECONDS);
                failureTimer.record(duration, TimeUnit.NANOSECONDS);
            }

            @Override
            public void reportIgnored() {
                long duration = clock.monotonicTime() - startTime;
                allTimer.record(duration, TimeUnit.NANOSECONDS);
                ignoredTimer.record(duration, TimeUnit.NANOSECONDS);
            }
        };
    }

    private static Timer buildTimer(String meterNamePrefix,
                                    String timerName,
                                    MeterRegistry meterRegistry,
                                    Iterable<Tag> tags,
                                    UnaryOperator<Timer.Builder> timerCustomization) {
        Timer.Builder timerBuilder = Timer.builder(meterNamePrefix + "." + timerName)
                                          .distributionStatisticExpiry(Duration.of(10, ChronoUnit.MINUTES))
                                          .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                                          .tags(tags);
        return timerCustomization.apply(timerBuilder).register(meterRegistry);
    }

    /**
     * Builder class to instantiate a {@link MessageTimerMonitor}.
     * <p>
     * The {@link Clock} is defaulted to a {@link Clock#SYSTEM}, the {@code tagsBuilder} to a {@link Function} returning
     * {@link Tags#empty()} and the {@code timerCustomization} to a no-op. The {@code meterNamePrefix} and {@link
     * MeterRegistry} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private String meterNamePrefix;
        private MeterRegistry meterRegistry;
        private Clock clock = Clock.SYSTEM;
        private Function<Message<?>, Iterable<Tag>> tagsBuilder = message -> Tags.empty();
        private UnaryOperator<Timer.Builder> timerCustomization = timerBuilder -> timerBuilder;

        /**
         * Sets the name used to prefix the names of the {@link Timer} instances created by this {@link
         * MessageMonitor}.
         *
         * @param meterNamePrefix a {@link String} used to prefix the names of the {@link Timer} instances created by
         *                        this {@link MessageMonitor}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder meterNamePrefix(String meterNamePrefix) {
            assertNonEmpty(meterNamePrefix, "The meter name prefix may not be null or empty");
            this.meterNamePrefix = meterNamePrefix;
            return this;
        }

        /**
         * Specifies the {@link MeterRegistry} used to registered the {@link Timer} instances to.
         *
         * @param meterRegistry the {@link MeterRegistry} used to registered the {@link Timer} instances to
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
         * Allows for specifying a customization which will be added during the creation of the {@link Timer}. Defaults
         * to a no-op. This for example allows more fine grained control over the published percentiles used by the
         * {@link Timer}.
         * <p>
         * Without any customization, a {@link Timer} define the {@link Timer.Builder#distributionStatisticExpiry(Duration)}
         * with a {@link Duration} of 10 minutes and {@link Timer.Builder#publishPercentiles(double...)} with the
         * percentiles {@code 0.5}, {@code 0.75}, {@code 0.95}, {@code 0.98}, {@code 0.99} and {@code 0.999}.
         *
         * @param timerCustomization a {@link UnaryOperator} taking in and returning a {@link Timer.Builder} forming a
         *                           customization on the {@link Timer} being created
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder timerCustomization(UnaryOperator<Timer.Builder> timerCustomization) {
            assertNonNull(timerCustomization, "TimerCustomization may not be null");
            this.timerCustomization = timerCustomization;
            return this;
        }

        /**
         * Initializes a {@link MessageTimerMonitor} as specified through this Builder.
         *
         * @return a {@link MessageTimerMonitor} as specified through this Builder
         */
        public MessageTimerMonitor build() {
            return new MessageTimerMonitor(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonEmpty(meterNamePrefix, "The meter name prefix is a hard requirement and should be provided");
            assertNonNull(meterRegistry, "The MeterRegistry is a hard requirement and should be provided");
        }
    }
}
