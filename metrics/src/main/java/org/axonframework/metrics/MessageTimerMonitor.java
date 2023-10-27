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
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Timer;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link MessageMonitor} which creates {@link Timer} instances for the overall, success, failure and ignored time an
 * ingested {@link Message} takes.
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public class MessageTimerMonitor implements MessageMonitor<Message<?>>, MetricSet {

    private final Timer allTimer;
    private final Timer successTimer;
    private final Timer failureTimer;
    private final Timer ignoredTimer;

    /**
     * Instantiate a Builder to be able to create a {@link MessageTimerMonitor}.
     * <p>
     * The {@link Clock} is defaulted to a {@link Clock#defaultClock()} and the {@code reservoirFactory} defaults to
     * creating a {@link ExponentiallyDecayingReservoir}.
     *
     * @return a Builder to be able to create a {@link MessageTimerMonitor}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link MessageTimerMonitor} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MessageTimerMonitor} instance
     */
    protected MessageTimerMonitor(Builder builder) {
        builder.validate();

        Clock clock = builder.clock;
        Supplier<Reservoir> reservoirFactory = builder.reservoirFactory;

        allTimer = new Timer(reservoirFactory.get(), clock);
        successTimer = new Timer(reservoirFactory.get(), clock);
        failureTimer = new Timer(reservoirFactory.get(), clock);
        ignoredTimer = new Timer(reservoirFactory.get(), clock);
    }

    /**
     * Creates a MessageTimerMonitor using a default clock
     *
     * @deprecated in favor of the {@link #builder()}
     */
    @Deprecated
    public MessageTimerMonitor() {
        this(Clock.defaultClock());
    }

    /**
     * Creates a MessageTimerMonitor using the provided clock
     *
     * @param clock the clock used to measure the process time of each message
     * @deprecated in favor of the {@link #builder()}
     */
    @Deprecated
    public MessageTimerMonitor(Clock clock) {
        allTimer = new Timer(new ExponentiallyDecayingReservoir(), clock);
        successTimer = new Timer(new ExponentiallyDecayingReservoir(), clock);
        failureTimer = new Timer(new ExponentiallyDecayingReservoir(), clock);
        ignoredTimer = new Timer(new ExponentiallyDecayingReservoir(), clock);
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message<?> message) {
        final Timer.Context allTimerContext = this.allTimer.time();
        final Timer.Context successTimerContext = this.successTimer.time();
        final Timer.Context failureTimerContext = this.failureTimer.time();
        final Timer.Context ignoredTimerContext = this.ignoredTimer.time();
        return new MessageMonitor.MonitorCallback() {
            @Override
            public void reportSuccess() {
                allTimerContext.stop();
                successTimerContext.stop();
            }

            @Override
            public void reportFailure(Throwable cause) {
                allTimerContext.stop();
                failureTimerContext.stop();
            }

            @Override
            public void reportIgnored() {
                allTimerContext.stop();
                ignoredTimerContext.stop();
            }
        };
    }

    @Override
    public Map<String, Metric> getMetrics() {
        Map<String, Metric> metrics = new HashMap<>();
        metrics.put("allTimer", allTimer);
        metrics.put("successTimer", successTimer);
        metrics.put("failureTimer", failureTimer);
        metrics.put("ignoredTimer", ignoredTimer);
        return metrics;
    }

    /**
     * Builder class to instantiate a {@link MessageTimerMonitor}.
     * <p>
     * The {@link Clock} is defaulted to a {@link Clock#defaultClock()} and the {@code reservoirFactory} defaults to
     * creating a {@link ExponentiallyDecayingReservoir}.
     */
    public static class Builder {

        private Clock clock = Clock.defaultClock();
        private Supplier<Reservoir> reservoirFactory = ExponentiallyDecayingReservoir::new;

        /**
         * Sets the {@link Clock} used to define the processing duration of a given message being pushed through this
         * {@link MessageMonitor}. Defaults to the {@link Clock#defaultClock}.
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
         * Sets factory method creating a {@link Reservoir} to be used by the {@link Timer} instances created by this
         * {@link MessageMonitor}. Defaults to a {@link Supplier} of {@link ExponentiallyDecayingReservoir}.
         *
         * @param reservoirFactory a factory method creating a {@link Reservoir} to be used by the {@link Timer}
         *                         instances created by this {@link MessageMonitor}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder reservoirFactory(Supplier<Reservoir> reservoirFactory) {
            assertNonNull(reservoirFactory, "ReservoirFactory may not be null");
            this.reservoirFactory = reservoirFactory;
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
            // No assertions required, kept for overriding
        }
    }
}
