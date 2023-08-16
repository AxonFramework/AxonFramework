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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

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
    private final Predicate<Throwable> nonTransientFailurePredicate;
    private final int maxRetryCount;

    /**
     * Construct the {@link AbstractRetryScheduler} from its builder.
     *
     * @param builder the {@link Builder}
     */
    @SuppressWarnings("unchecked")
    protected AbstractRetryScheduler(Builder builder) {
        builder.validate();
        this.retryExecutor = builder.retryExecutor;
        this.nonTransientFailurePredicate = builder.nonTransientFailurePredicate;
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
     * Indicates whether the given {@code failure} is clearly non-transient. That means, whether the
     * {@code failure} explicitly states that a retry of the same Command would result in the same failure to
     * occur again.
     * <p/>
     * In this implementation, given {@code failure} (and its causes) is tested against configured composable
     * {@code nonTransientFailurePredicate}. By default, {@code nonTransientFailurePredicate} is configured only
     * with {@link AxonNonTransientExceptionClassesPredicate}.
     *
     * @param failure the exception that occurred while processing a command
     * @return {@code true} if the exception is clearly non-transient and the command should <em>not</em> be
     * retried, or {@code false} when the command has a chance of succeeding if it retried.
     */
    protected boolean isExplicitlyNonTransient(Throwable failure) {
        boolean isNonTransientFailure = nonTransientFailurePredicate.test(failure);
        return isNonTransientFailure || (failure.getCause() != null && isExplicitlyNonTransient(failure.getCause()));
    }

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
    public boolean scheduleRetry(@Nonnull CommandMessage commandMessage,
                                 @Nonnull RuntimeException lastFailure,
                                 @Nonnull List<Class<? extends Throwable>[]> failures,
                                 @Nonnull Runnable dispatchTask) {
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
            return scheduleRetry(dispatchTask, computeRetryInterval(commandMessage, lastFailure, failures));
        } else {
            if (failureCount >= maxRetryCount && logger.isInfoEnabled()) {
                logger.warn("Processing of Command [{}] resulted in an exception {} times. Giving up permanently.",
                            commandMessage.getPayloadType().getSimpleName(), failureCount, lastFailure);
            } else if (logger.isInfoEnabled()) {
                logger.debug(
                        "Processing of Command [{}] resulted in an exception and will not be retried. Exception was {}, {}",
                        commandMessage.getPayloadType().getSimpleName(),
                        lastFailure.getClass().getName(),
                        lastFailure.getMessage()
                );
            }
            return false;
        }
    }

    /**
     * A builder class for the {@link RetryScheduler} implementations.
     * <p>
     * The default for {@code maxRetryCount} is set to a single retry and the {@code nonTransientFailurePredicate} defaults to {@link AxonNonTransientExceptionClassesPredicate}.
     * The {@link ScheduledExecutorService} is a <b>hard requirement</b> and as such should be provided.
     */
    public abstract static class Builder<B extends Builder> {

        private ScheduledExecutorService retryExecutor;
        private int maxRetryCount = DEFAULT_MAX_RETRIES;
        private Predicate<Throwable> nonTransientFailurePredicate = new AxonNonTransientExceptionClassesPredicate();

        /**
         * Sets the {@link ScheduledExecutorService} used to schedule a command retry.
         *
         * @param retryExecutor a {@link ScheduledExecutorService} used to schedule a command retry
         * @return the current Builder instance, for fluent interfacing
         */
        public B retryExecutor(@Nonnull ScheduledExecutorService retryExecutor) {
            assertNonNull(retryExecutor, "ScheduledExecutorService may not be null");
            this.retryExecutor = retryExecutor;

            //noinspection unchecked
            return (B) this;
        }

        /**
         * Resets the {@link Predicate} chain used for checking non-transiency of a failure. Throws away any previously
         * existing predicate chain and replaces it with the provided predicate.
         * <p/>
         * Provided {@code nonTransientFailurePredicate} checks whether a failure is transient (predicate returns
         * {@code false}) or not (predicate returns {@code true}). Non-transient failures are never retried.
         *
         * @param nonTransientFailurePredicate a {@link Predicate} (accepting a {@link Throwable} parameter) used for
         *                                     testing transiency of provided failure
         * @return the current Builder instance, for fluent interfacing
         */
        public B nonTransientFailurePredicate(@Nonnull Predicate<Throwable> nonTransientFailurePredicate) {
            assertNonNull(nonTransientFailurePredicate, "Non-transient failure predicate may not be null");
            this.nonTransientFailurePredicate = nonTransientFailurePredicate;

            // noinspection unchecked
            return (B) this;
        }

        /**
         * Resets the {@link Predicate} chain used for checking non-transiency of a failure. Throws away any previously
         * existing predicate chain and replaces it with the provided predicate.
         * <p/>
         * Provided {@code nonTransientFailurePredicate} checks whether a failure is transient (predicate returns
         * {@code false}) or not (predicate returns {@code true}). Non-transient failures are never retried.
         * <p/>
         * In essence, this is just a failure typed variant of {@link #nonTransientFailurePredicate(Predicate)} method.
         *
         * @param failureType concrete class of the failure to handle
         * @param nonTransientFailurePredicate a {@link Predicate} (accepting a {@code failureType} parameter) used
         *                                     for testing transiency of provided failure
         * @param <E> type of the failure to handle
         * @return the current Builder instance, for fluent interfacing
         */
        public <E extends Throwable> B nonTransientFailurePredicate(@Nonnull Class<E> failureType,
                                                                    @Nonnull Predicate<? super E> nonTransientFailurePredicate) {
            assertNonNull(failureType, "Class of failure type may not be null");
            assertNonNull(nonTransientFailurePredicate, "Non-transient failure predicate may not be null");

            //noinspection Convert2MethodRef
            Predicate<E> typeCheckPredicate = (failureAtRuntime) -> failureType.isInstance(failureAtRuntime);

            // noinspection unchecked
            this.nonTransientFailurePredicate = ((Predicate<Throwable>) typeCheckPredicate.and(
                    nonTransientFailurePredicate));

            // noinspection unchecked
            return (B) this;
        }

        /**
         * Adds additional predicate into the predicate chain for checking non-transiency of a failure.
         * <p/>
         * Provided {@code nonTransientFailurePredicate} is set on the beginning of the predicate chain with
         * the {@code Predicate#or} operator.
         *
         * @param nonTransientFailurePredicate an additional {@link Predicate} (accepting a {@link Throwable}
         *                                     parameter) to be added into the predicate chain used for
         *                                     testing transiency of provided failure
         * @return the current Builder instance, for fluent interfacing
         */
        public B addNonTransientFailurePredicate(@Nonnull Predicate<Throwable> nonTransientFailurePredicate) {
            assertNonNull(nonTransientFailurePredicate, "Non-transient failure predicate may not be null");
            this.nonTransientFailurePredicate = nonTransientFailurePredicate.or(this.nonTransientFailurePredicate);

            // noinspection unchecked
            return (B) this;
        }

        /**
         * Adds additional predicate into the predicate chain for checking non-transiency of a failure.
         * <p/>
         * Provided {@code nonTransientFailurePredicate} is set on the beginning of the predicate chain with
         * {@code or} operator.
         * <p/>
         * In essence, this is just a failure typed variant of {@link #addNonTransientFailurePredicate(Predicate)}
         * method.
         *
         * @param failureType concrete class of the failure to handle
         * @param nonTransientFailurePredicate an additional {@link Predicate} (accepting a {@code failure}
         *                                     parameter) to be added into the predicate chain used for testing
         *                                     transiency of provided failure
         * @param <E> type of the failure to handle
         * @return the current Builder instance, for fluent interfacing
         */
        public <E extends Throwable> B addNonTransientFailurePredicate(@Nonnull Class<E> failureType,
                                                                       @Nonnull Predicate<? super E> nonTransientFailurePredicate) {
            assertNonNull(failureType, "Class of failure type may not be null");
            assertNonNull(nonTransientFailurePredicate, "Non-transient failure predicate may not be null");

            // noinspection Convert2MethodRef
            Predicate<E> typeCheckPredicate = (failureAtRuntime) -> failureType.isInstance(failureAtRuntime);

            //noinspection unchecked
            Predicate<Throwable> typeCheckingNonTransientFailurePredicate = ((Predicate<Throwable>) typeCheckPredicate.and(
                    nonTransientFailurePredicate));

            this.nonTransientFailurePredicate = typeCheckingNonTransientFailurePredicate.or(this.nonTransientFailurePredicate);

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
