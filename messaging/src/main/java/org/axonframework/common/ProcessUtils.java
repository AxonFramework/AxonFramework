package org.axonframework.common;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Processing utilities.
 *
 * @author Marc Gathier
 * @since 4.2
 */
public abstract class ProcessUtils {

    private ProcessUtils() {
    }

    /**
     * Executes an action, with potential retry in case of an exception.
     *
     * @param runnable       action to execute
     * @param retryPredicate predicate to determine if the action should be retried based on the exception
     * @param timeout        timeout for the retries
     * @param timeUnit       unit for the timeout
     * @param retryInterval  time to wait between retries of the action
     */
    public static void executeWithRetry(Runnable runnable, Predicate<RuntimeException> retryPredicate,
                                        long timeout, TimeUnit timeUnit,
                                        long retryInterval) {
        long completeBefore = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        RuntimeException lastException = new RuntimeException();
        while (completeBefore > System.currentTimeMillis()) {
            try {
                runnable.run();
                return;
            } catch (RuntimeException re) {
                if (!retryPredicate.test(re)) {
                    throw re;
                }

                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw re;
                }
                lastException = re;
            }
        }

        throw lastException;
    }

    /**
     * Executes an action, with potential retry in case the result is false. Exception handling should be taken care of
     * within the action if needed.
     *
     * @param runnable      action to execute, will be executed till the result is true, or max tries is reached
     * @param retryInterval time to wait between retries of the action
     * @param maxTries      maximum number of times the action is invoked
     */
    public static void executeUntilTrue(BooleanSupplier runnable, long retryInterval, long maxTries) {
        AtomicLong totalTriesCounter = new AtomicLong();
        boolean result = runnable.getAsBoolean();
        while (!result) {
            if (totalTriesCounter.incrementAndGet() >= maxTries){
                throw new ProcessRetriesExhaustedException(String.format(
                        "Tried invoking the action for %d times, without the result being true",
                        maxTries));
            }
            try {
                Thread.sleep(retryInterval);
                result = runnable.getAsBoolean();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
