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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * An abstract base class for {@link RetryScheduler}s. This class provides methods to do the actual rescheduling and
 * decides if a given {@link Throwable} is explicitly transient.
 *
 * @author Bert Laverman
 * @since 4.2
 */
public abstract class AbstractRetryScheduler implements RetryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int DEFAULT_MAX_RETRIES = 1;

    private final ScheduledExecutorService retryExecutor;
    private final int maxRetryCount;

    /**
     * Construct the {@link AbstractRetryScheduler} from its builder.
     *
     * @param builder the {@link Builder}
     */
    protected AbstractRetryScheduler(Builder builder) {
        builder.validate();
        this.retryExecutor = builder.retryExecutor;
        this.maxRetryCount = builder.maxRetryCount;
    }

    /**
     * Schedule the provided task to run after the given interval.
     *
     * @param commandDispatch the {@link Runnable} to schedule.
     * @param interval        the number of milliseconds delay.
     * @return {@code true} if the task was accepted for scheduling, {@code false} otherwise.
     */
    protected boolean scheduleRetry(Runnable commandDispatch, long interval) {
        try {
            retryExecutor.schedule(commandDispatch, interval, TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    /**
     * Compute the amount of milliseconds delay until the next retry, given the information passed.
     *
     * @param commandMessage the command that was sent (and failed).
     * @param lastFailure    the last failure that caused this retry scheduler to be called.
     * @param failures       a {@link List} of all failures up to now.
     * @return the number of milliseconds to wait until retrying.
     */
    protected abstract long computeRetryInterval(CommandMessage commandMessage,
                                                 RuntimeException lastFailure,
                                                 List<Class<? extends Throwable>[]> failures);

    /**
     * This is the entrypoint of the {@link RetryScheduler}. This default implementation checks if the last failure was
     * transient, and if so reschedules a command dispatch.
     *
     * @param commandMessage The Command Message being dispatched
     * @param lastFailure    The last failure recorded for this command
     * @param failures       A condensed view of all known failures of this command. Each element in the array
     *                       represents the cause of the element preceding it.
     * @param dispatchTask   the task that performs the actual dispatching.
     * @return {@code true} if rescheduling succeeded, {@code false} if otherwise.
     */
    @Override
    public boolean scheduleRetry(CommandMessage commandMessage,
                                 RuntimeException lastFailure,
                                 List<Class<? extends Throwable>[]> failures,
                                 Runnable dispatchTask) {
        int failureCount = failures.size();
        if (!ExceptionUtils.isExplicitlyNonTransient(lastFailure) && failureCount <= maxRetryCount) {
            if (logger.isInfoEnabled()) {
                logger.info("Processing of Command [{}] resulted in an exception. Will retry {} more time(s)... "
                                    + "Exception was {}, {}",
                            commandMessage.getPayloadType().getSimpleName(),
                            maxRetryCount - failureCount,
                            lastFailure.getClass().getName(),
                            lastFailure.getMessage()
                );
            }
            return scheduleRetry(dispatchTask, computeRetryInterval(commandMessage, lastFailure, failures));
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
     * A builder class for the {@link RetryScheduler} implementations.
     * <p>
     * The default for {@code maxRetryCount} is set to a single retry.
     * The {@link ScheduledExecutorService} is a <b>hard requirement</b> and as such should be provided.
     */
    public abstract static class Builder<B extends Builder> {

        private ScheduledExecutorService retryExecutor;
        private int maxRetryCount = DEFAULT_MAX_RETRIES;

        /**
         * Sets the {@link ScheduledExecutorService} used to schedule a command retry.
         *
         * @param retryExecutor a {@link ScheduledExecutorService} used to schedule a command retry
         * @return the current Builder instance, for fluent interfacing
         */
        public B retryExecutor(ScheduledExecutorService retryExecutor) {
            assertNonNull(retryExecutor, "ScheduledExecutorService may not be null");
            this.retryExecutor = retryExecutor;
            // noinspection unchecked
            return (B) this;
        }

        /**
         * Sets the maximum number of retries allowed for a single command. Defaults to 1.
         *
         * @param maxRetryCount an {@code int} specifying the maximum number of retries allowed for a single command
         * @return the current Builder instance, for fluent interfacing
         */
        public B maxRetryCount(int maxRetryCount) {
            assertStrictPositive(maxRetryCount, "The maxRetryCount must be a positive number");
            this.maxRetryCount = maxRetryCount;
            // noinspection unchecked
            return (B) this;
        }

        /**
         * Validate the fields. This method is called in the {@link AbstractRetryScheduler}'s constructor.
         *
         * @throws AxonConfigurationException if validation fails.
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(retryExecutor, "The ScheduledExecutorService is a hard requirement and should be provided");
        }
    }
}
