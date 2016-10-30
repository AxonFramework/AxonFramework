/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonNonTransientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
     * Initializes the retry scheduler to schedule retries on the given {@code executor} using the given
     * {@code interval} and allowing {@code maxRetryCount} retries before giving up permanently.
     *
     * @param executor      The executor on which to schedule retry execution
     * @param interval      The interval in milliseconds at which to schedule a retry
     * @param maxRetryCount The maximum number of retries allowed for a single command
     */
    public IntervalRetryScheduler(ScheduledExecutorService executor, int interval, int maxRetryCount) {
        Assert.notNull(executor, () -> "executor may not be null");
        this.retryExecutor = executor;
        this.retryInterval = interval;
        this.maxRetryCount = maxRetryCount;
    }

    @Override
    public boolean scheduleRetry(CommandMessage commandMessage, RuntimeException lastFailure,
                                 List<Class<? extends Throwable>[]> failures, Runnable dispatchTask) {
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
}
