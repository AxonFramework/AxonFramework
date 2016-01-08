package org.axonframework.saga.annotation;

import org.axonframework.common.AxonNonTransientException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.async.RetryPolicy;
import org.axonframework.saga.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * ErrorHandler implementation that retries Events on non-transient exceptions.
 *
 * @author Allard Buijze
 * @since 2.4.2
 */
public class RetryingErrorHandler implements ErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(RetryingErrorHandler.class);

    private final TimeoutConfiguration[] timeoutConfigurations;

    /**
     * Initialize an instance of the RetryingErrorHandler that indefinitely retries each 2 seconds.
     */
    public RetryingErrorHandler() {
        this(new TimeoutConfiguration(2, TimeUnit.SECONDS));
    }

    /**
     * Initialize an instance of the RetryingErrorHandler that uses the given <code>TimeoutConfiguration</code>s that
     * describe which retry timeout should be used for each number of retries.
     *
     * @param timeoutConfigurations The definitions of the timeouts to apply to each retry
     */
    public RetryingErrorHandler(TimeoutConfiguration... timeoutConfigurations) {
        this.timeoutConfigurations = Arrays.copyOf(timeoutConfigurations, timeoutConfigurations.length);
    }

    @Override
    public RetryPolicy onErrorPreparing(Class<? extends Saga> sagaType, EventMessage<?> publishedEvent,
                                        int invocationCount, Exception e) {
        return policyFor(invocationCount, sagaType, publishedEvent, e, "prepare");
    }

    @Override
    public RetryPolicy onErrorInvoking(Saga saga, EventMessage publishedEvent, int invocationCount, Exception e) {
        return policyFor(invocationCount, saga.getClass(), publishedEvent, e, "invoke");
    }

    /**
     * Indicates whether the given <code>exception</code> is transient (i.e. could produce a different result when
     * retried). Exceptions that are non-transient will not be eligible for a retry.
     * <p/>
     * This implementation will check if the exception or one of its causes is an instance of {@link
     * AxonNonTransientException}.
     *
     * @param exception The exception to inspect
     * @return <code>true</code> if the exception is transient, otherwise <code>false</code>.
     */
    protected boolean isTransient(Throwable exception) {
        return !AxonNonTransientException.isCauseOf(exception);
    }

    private RetryPolicy policyFor(int invocationCount, Class<?> sagaType, EventMessage publishedEvent, Throwable e,
                                  final String task) {
        if (!isTransient(e)) {
            logger.error("An non-recoverable error occurred while trying to prepare sagas of type [{}] for invocation "
                                 + "of event [{}]. Proceeding with the next event.",
                         sagaType.getName(), publishedEvent.getPayloadType().getName(), e);
            return RetryPolicy.proceed();
        }

        int c = invocationCount;
        for (TimeoutConfiguration configuration : timeoutConfigurations) {
            if (c <= configuration.count() || configuration.count() < 0) {
                return configuration.retryPolicy();
            }
            c -= configuration.count();
        }
        logger.error("Giving up on retrying after {} attempts to " + task + " sagas of type [{}] for event [{}]. "
                             + "Proceeding with the next event.",
                     invocationCount, sagaType.getName(), publishedEvent.getPayloadType().getName(), e);
        return RetryPolicy.proceed();
    }

    /**
     * Definition of a timeout to use for a specific range of retries
     */
    public static class TimeoutConfiguration {

        private final int count;
        private final RetryPolicy retryPolicy;

        /**
         * Initialize a configuration to indefinitely retry, applying the given <code>timeout</code> between retries.
         *
         * @param timeout  The amount of time to wait before the next retry
         * @param timeUnit The unit of the timeout
         */
        public TimeoutConfiguration(long timeout, TimeUnit timeUnit) {
            this.count = -1;
            retryPolicy = RetryPolicy.retryAfter(timeout, timeUnit);
        }

        /**
         * Initialize a configuration to apply the given <code>timeout</code> between retries for the given
         * <code>count</code> number of retries.
         *
         * @param count    The number of retries to apply the given timeout for
         * @param timeout  The amount of time to wait before the next retry
         * @param timeUnit The unit of the timeout
         */
        public TimeoutConfiguration(int count, long timeout, TimeUnit timeUnit) {
            this.count = count;
            retryPolicy = RetryPolicy.retryAfter(timeout, timeUnit);
        }

        /**
         * The number of times to apply the {@link #retryPolicy retryPolicy}.
         *
         * @return number of times to apply the {@link #retryPolicy retryPolicy}
         */
        public int count() {
            return count;
        }

        /**
         * The retry policy to use between retries
         *
         * @return the retry policy to use between retries
         */
        public RetryPolicy retryPolicy() {
            return retryPolicy;
        }
    }
}
