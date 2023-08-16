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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.AxonConfigurationException;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.axonframework.common.BuilderUtils.assertPositive;

/**
 * RetryScheduler implementation that retries commands at regular intervals when they fail because of an exception that
 * is not explicitly non-transient. Checked exceptions are considered non-transient and will not result in a retry.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class IntervalRetryScheduler extends AbstractRetryScheduler {

    private static final long DEFAULT_RETRY_INTERVAL = 100L;

    private final long retryInterval;

    /**
     * Instantiate a {@link IntervalRetryScheduler} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@code retryInterval} and {@code maxRetryCount} are positive numbers, and that the
     * {@link ScheduledExecutorService} is not {@code null}. If any of these does not hold, an
     * {@link AxonConfigurationException} will be thrown.
     *
     * @param builder the {@link Builder} used to instantiate a {@link IntervalRetryScheduler} instance
     */
    protected IntervalRetryScheduler(Builder builder) {
        super(builder);

        this.retryInterval = builder.retryInterval;
    }

    @Override
    protected long computeRetryInterval(CommandMessage commandMessage,
                                        RuntimeException lastFailure,
                                        List<Class<? extends Throwable>[]> failures) {
        return retryInterval;
    }

    /**
     * Instantiate a Builder to be able to create a {@link IntervalRetryScheduler}.
     * <p>
     * The default for {@code maxRetryCount} is set to a single retry, the {@code backoffFactor} defaults to 100ms and
     * the {@code nonTransientFailurePredicate} defaults to {@link AxonNonTransientExceptionClassesPredicate}.
     * The {@link ScheduledExecutorService} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link IntervalRetryScheduler}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class to instantiate a {@link IntervalRetryScheduler}.
     * <p>
     * The default for {@code maxRetryCount} is set to a single retry, the {@code backoffFactor} defaults to 100ms and
     * the {@code nonTransientFailurePredicate} defaults to {@link AxonNonTransientExceptionClassesPredicate}.
     * The {@link ScheduledExecutorService} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder extends AbstractRetryScheduler.Builder<Builder> {

        private long retryInterval = DEFAULT_RETRY_INTERVAL;

        /**
         * Sets the retry interval in milliseconds at which to schedule a retry. This field defaults to 100ms and is
         * required to be a positive number.
         *
         * @param retryInterval an {@code int} specifying the retry interval in milliseconds at which to schedule a
         *                      retry
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder retryInterval(int retryInterval) {
            assertPositive(retryInterval, "The retryInterval must be a positive number");
            this.retryInterval = retryInterval;
            return this;
        }

        /**
         * Initializes a {@link IntervalRetryScheduler} as specified through this Builder.
         *
         * @return a {@link IntervalRetryScheduler} as specified through this Builder
         */
        public IntervalRetryScheduler build() {
            return new IntervalRetryScheduler(this);
        }
    }
}
