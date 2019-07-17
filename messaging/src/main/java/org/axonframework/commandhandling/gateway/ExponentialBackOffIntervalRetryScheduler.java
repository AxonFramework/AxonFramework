/*
 * Copyright (c) 2010-2019. Axon Framework
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
        builder.validate();

        this.backoffFactor = builder.backoffFactor;
    }

    /**
     * Instantiate a Builder to be able to create a {@link ExponentialBackOffIntervalRetryScheduler}.
     * <p>
     * The default for {@link Builder#backoffFactor} is set to 100ms, and must be at least 1.
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
     * A builder class for an exponential backoff retry scheduler.
     * <p>
     * The default for {@link Builder#backoffFactor} is set to 100ms, and must be at least 1.
     */
    public static class Builder extends AbstractRetryScheduler.Builder<Builder> {

        private long backoffFactor = DEFAULT_BACKOFF_FACTOR;

        /**
         * Sets the backoff factor in milliseconds at which to schedule a retry, defaulted to 100ms.
         *
         * @param backoffFactor an {@code int} specifying the interval in milliseconds at which to schedule a retry.
         * @return the current Builder instance, for fluent interfacing.
         */
        public Builder backoffFactor(long backoffFactor) {
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

        /**
         * Validate the input, in this case asserting that the backoff factor is strictly positive (>= 1).
         */
        protected void validate() {
            super.validate();

            assertStrictPositive(backoffFactor, "The backoff factor is a hard requirement and must be at least 1.");
        }
    }
}
