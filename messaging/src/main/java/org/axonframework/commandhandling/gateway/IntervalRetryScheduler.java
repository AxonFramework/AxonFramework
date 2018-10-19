/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.common.AxonNonTransientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * RetryScheduler implementation that retries commands at regular intervals when they fail because of an exception that
 * is not explicitly non-transient. Checked exceptions are considered non-transient and will not result in a retry.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class IntervalRetryScheduler implements RetryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(IntervalRetryScheduler.class);

    private final int retryInterval;
    private final int maxRetryCount;
    private final ScheduledExecutorService retryExecutor;

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
        builder.validate();
        this.retryInterval = builder.retryInterval;
        this.maxRetryCount = builder.maxRetryCount;
        this.retryExecutor = builder.retryExecutor;
    }

    /**
     * Instantiate a Builder to be able to create a {@link IntervalRetryScheduler}.
     * <p>
     * The {@code retryInterval}, {@code maxRetryCount} and {@link ScheduledExecutorService} are
     * <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link IntervalRetryScheduler}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean scheduleRetry(CommandMessage commandMessage,
                                 RuntimeException lastFailure,
                                 List<Class<? extends Throwable>[]> failures,
                                 Runnable dispatchTask) {
        int failureCount = failures.size();
        if (!isExplicitlyNonTransient(lastFailure) && failureCount <= maxRetryCount) {
            if (logger.isInfoEnabled()) {
                logger.info("Processing of Command [{}] resulted in an exception. Will retry {} more time(s)... "
                                    + "Exception was {}, {}",
                            commandMessage.getPayloadType().getSimpleName(),
                            maxRetryCount - failureCount,
                            lastFailure.getClass().getName(),
                            lastFailure.getMessage()
                );
            }
            return scheduleRetry(dispatchTask, retryInterval);
        } else {
            if (failureCount >= maxRetryCount && logger.isInfoEnabled()) {
                logger.info("Processing of Command [{}] resulted in an exception {} times. Giving up permanently. ",
                            commandMessage.getPayloadType().getSimpleName(), failureCount, lastFailure);
            } else if (logger.isInfoEnabled()) {
                logger.info("Processing of Command [{}] resulted in an exception and will not be retried. ",
                            commandMessage.getPayloadType().getSimpleName(), lastFailure);
            }
            return false;
        }
    }

    /**
     * Indicates whether the given {@code failure} is clearly non-transient. That means, whether the
     * {@code failure} explicitly states that a retry of the same Command would result in the same failure to
     * occur
     * again.
     *
     * @param failure The exception that occurred while processing a command
     * @return {@code true} if the exception is clearly non-transient and the command should <em>not</em> be
     * retried, or {@code false} when the command has a chance of succeeding if it retried.
     */
    protected boolean isExplicitlyNonTransient(Throwable failure) {
        return failure instanceof AxonNonTransientException
                || (failure.getCause() != null && isExplicitlyNonTransient(failure.getCause()));
    }

    private boolean scheduleRetry(Runnable commandDispatch, int interval) {
        try {
            retryExecutor.schedule(commandDispatch, interval, TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    /**
     * Builder class to instantiate a {@link IntervalRetryScheduler}.
     * <p>
     * The {@code retryInterval}, {@code maxRetryCount} and {@link ScheduledExecutorService} are
     * <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private int retryInterval;
        private int maxRetryCount;
        private ScheduledExecutorService retryExecutor;

        /**
         * Sets the retry interval in milliseconds at which to schedule a retry.
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
         * Sets the maximum number of retries allowed for a single command.
         *
         * @param maxRetryCount an {@code int} specifying the maximum number of retries allowed for a single command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder maxRetryCount(int maxRetryCount) {
            assertPositive(maxRetryCount, "The maxRetryCount must be a positive number");
            this.maxRetryCount = maxRetryCount;
            return this;
        }

        /**
         * Sets the {@link ScheduledExecutorService} used to schedule a command retry.
         *
         * @param retryExecutor a {@link ScheduledExecutorService} used to schedule a command retry
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder retryExecutor(ScheduledExecutorService retryExecutor) {
            assertNonNull(retryExecutor, "ScheduledExecutorService may not be null");
            this.retryExecutor = retryExecutor;
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

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertPositive(retryInterval, "The retryInterval is a hard requirement and should be provided");
            assertPositive(maxRetryCount, "The maxRetryCount is a hard requirement and should be provided");
            assertNonNull(retryExecutor, "The ScheduledExecutorService is a hard requirement and should be provided");
        }

        private void assertPositive(int integer, String exceptionDescription) {
            assertThat(integer, number -> number >= 0, exceptionDescription);
        }
    }
}
