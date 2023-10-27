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

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * A RetryScheduler that uses a backoff strategy, retrying commands at increasing intervals when they fail because of
 * an exception that is not explicitly non-transient. Checked exceptions are considered non-transient and will not
 * result in a retry.
 *
 * @author Bert Laverman
 * @since 4.2
 */
public class ExponentialBackOffIntervalRetryScheduler extends AbstractRetryScheduler {

    private static final long DEFAULT_BACKOFF_FACTOR = 100L;

    private final long backoffFactor;

    /**
     * Instantiate an {@link ExponentialBackOffIntervalRetryScheduler}. the settings are copied from the
     * {@link Builder} and validated.
     *
     * @param builder
     */
    protected ExponentialBackOffIntervalRetryScheduler(Builder builder) {
        super(builder);

        this.backoffFactor = builder.backoffFactor;
    }

    /**
     * Instantiate a Builder to be able to create a {@link ExponentialBackOffIntervalRetryScheduler}.
     * <p>
     * The default for {@code maxRetryCount} is set to a single retry, the {@code backoffFactor} defaults to 100ms
     * and the {@code nonTransientFailurePredicate} defaults to {@link AxonNonTransientExceptionClassesPredicate}.
     * The {@link ScheduledExecutorService} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link ExponentialBackOffIntervalRetryScheduler}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected long computeRetryInterval(CommandMessage commandMessage,
                                        RuntimeException lastFailure,
                                        List<Class<? extends Throwable>[]> failures) {
        final int retryCount = failures.size();
        return backoffFactor * (1L << (retryCount - 1));
    }

    /**
     * Builder class to instantiate an {@link ExponentialBackOffIntervalRetryScheduler}.
     * <p>
     * The default for {@code maxRetryCount} is set to a single retry, the {@code backoffFactor} defaults to 100ms
     * and the {@code nonTransientFailurePredicate} defaults to {@link AxonNonTransientExceptionClassesPredicate}.
     * The {@link ScheduledExecutorService} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder extends AbstractRetryScheduler.Builder<Builder> {

        private long backoffFactor = DEFAULT_BACKOFF_FACTOR;

        /**
         * Sets the backoff factor in milliseconds at which to schedule a retry. This field defaults to 100ms and is
         * required to be a positive number.
         *
         * @param backoffFactor an {@code int} specifying the interval in milliseconds at which to schedule a retry
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder backoffFactor(long backoffFactor) {
            assertStrictPositive(backoffFactor, "The backoffFactor must be a positive number");
            this.backoffFactor = backoffFactor;
            return this;
        }

        /**
         * Initializes a {@link ExponentialBackOffIntervalRetryScheduler} as specified through this Builder.
         *
         * @return a {@link ExponentialBackOffIntervalRetryScheduler} as specified through this Builder
         */
        public ExponentialBackOffIntervalRetryScheduler build() {
            return new ExponentialBackOffIntervalRetryScheduler(this);
        }
    }
}
