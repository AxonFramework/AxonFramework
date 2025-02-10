package org.axonframework.messaging.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Represents a task with a timeout. The task will be interrupted when the {@code timeout} is reached. If the
 * {@code warningThreshold} is lower than the timeout, warnings will be logged at the configured {@code warningInterval}
 * until the timeout is reached.
 * <p>
 * You can use {@link AxonTaskUtils} to run a task with a timeout.
 *
 * @param <T> The result type of the task
 * @author Mitchell Herrijgers
 * @see AxonTaskUtils
 * @since 4.11
 */
class AxonTaskWithTimeout<T> {

    private static final Logger logger = LoggerFactory.getLogger(AxonTaskWithTimeout.class);

    private final Thread thread;
    private final int timeout;
    private final int warningThreshold;
    private final int warningInterval;
    private final String task;
    private final ScheduledExecutorService scheduledExecutorService;
    private boolean completed = false;
    private long startTimeMs = -1;
    private Future<?> currentScheduledFuture = null;

    /**
     * Creates a new {@link AxonTaskWithTimeout} for the given {@code task} with the given {@code timeout},
     * {@code warningThreshold} and {@code warningInterval}.
     * <p>
     * Runs the provided task on the current thread after scheduling a timeout and warnings on the provided
     * {@code scheduledExecutorService}.
     *
     * @param taskName                 The task's name to be included in the logging
     * @param timeout                  The timeout in milliseconds
     * @param warningThreshold         The threshold in milliseconds after which a warning is logged. Setting this to a
     *                                 value higher than {@code timeout} will disable warnings.
     * @param warningInterval          The interval in milliseconds between warnings.
     * @param scheduledExecutorService The executor service to schedule the timeout and warnings
     */
    AxonTaskWithTimeout(String taskName,
                        int timeout,
                        int warningThreshold,
                        int warningInterval,
                        ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.thread = Thread.currentThread();
        this.timeout = timeout;
        this.warningThreshold = warningThreshold;
        this.warningInterval = warningInterval;
        this.task = taskName;
    }

    /**
     * Runs the provided task with the configured timeout and warning settings. You can only run one task with this
     * instance of {@link AxonTaskWithTimeout}.
     *
     * @param task The task to run
     * @return The result of the task
     * @throws Exception Throws exceptions thrown by the {@code task} or a {@link TimeoutException} when the task times
     *                   out.
     */
    public T runWithTimeout(Callable<T> task) throws Exception {
        if (startTimeMs != -1) {
            throw new IllegalStateException("Task can only be run once");
        }
        startTimeMs = System.currentTimeMillis();
        if (warningThreshold < 0 || warningThreshold > timeout) {
            scheduleImmediateInterrupt();
        } else {
            scheduleFirstWarning();
        }

        try {
            return task.call();
        } catch (InterruptedException e) {
            throw new TimeoutException(String.format("%s has timed out", task));
        } finally {
            markCompleted();
        }
    }

    /**
     * When a warning has not been configured, this method schedules an interrupt immediately for the timeout.
     */
    private void scheduleImmediateInterrupt() {
        if (logger.isTraceEnabled()) {
            logger.trace("{} will be interrupted after [{}ms]",
                         task,
                         timeout);
        }
        scheduleInterrupt(timeout);
    }

    /**
     * Schedules the first warning for the task. This warning will be issued after the configured
     * {@code warningThreshold}
     */
    private void scheduleFirstWarning() {
        if (logger.isTraceEnabled()) {
            logger.trace(
                    "{} will be interrupted in [{}ms]. First warning will be issued in [{}ms].",
                    task,
                    timeout,
                    warningThreshold);
        }
        scheduleWarning(warningThreshold);
    }

    /**
     * Marks the task as completed. Cancels the current scheduled future if it exists.
     */
    private void markCompleted() {
        completed = true;
        if (currentScheduledFuture != null) {
            currentScheduledFuture.cancel(false);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("{} completed", task);
        }
    }

    /**
     * Schedule a subsequent warning for the task after the configured {@code timeout}. Once the warning time is
     * reached, it will log a warning (if the task is not completed yet) and schedule the next warning or the timeout
     * interrupt.
     *
     * @param timeout The time in milliseconds before the warning should be scheduled
     */
    private void scheduleWarning(long timeout) {
        currentScheduledFuture = scheduledExecutorService.schedule(() -> {
            if (!completed) {
                scheduleWarningOrInterrupt();
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedules either a warning or interrupt, after the first warning has been issued. Which of the two is scheduled
     * depends on the time taken so far. If the remaining time is less than the warning interval, an interrupt is
     * scheduled. Otherwise, a warning is scheduled.
     */
    private void scheduleWarningOrInterrupt() {
        long takenTime = System.currentTimeMillis() - startTimeMs;
        logger.warn(
                "{} is taking long time to process. Current time: [{}ms]. Will be interrupted in [{}ms].\nStacktrace of current thread:\n{}",
                task,
                takenTime,
                timeout - takenTime,
                getCurrentStackTrace());
        if (takenTime + warningInterval < timeout) {
            scheduleWarning(warningInterval);
        } else {
            scheduleInterrupt(timeout - takenTime);
        }
    }

    /**
     * Schedules an interrupt to the thread handling the message.
     *
     * @param remainingTimeout The time in milliseconds before the interrupt should be scheduled
     */
    private void scheduleInterrupt(long remainingTimeout) {
        currentScheduledFuture = scheduledExecutorService.schedule(() -> {
            if (!completed) {
                logger.error(
                        "{} has exceeded its timeout of [{}ms]. Interrupting thread.\nStacktrace of current thread:\n{}",
                        task,
                        timeout,
                        getCurrentStackTrace());
                thread.interrupt();
            }
        }, remainingTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the current stack trace of the thread handling the message. Cuts off the stack trace at the point where
     * the framework internals are calling the method.
     *
     * @return The current stack trace of the thread handling the message
     */
    private String getCurrentStackTrace() {
        StackTraceElement[] stackTrace = thread.getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            // This is the start of the stack trace of the framework internals calling the method
            if (element.toString().startsWith("java.base/jdk.internal.reflect.DirectMethodHandleAccessor")) {
                break;
            }
            sb.append(element).append("\n");
        }
        return sb.toString();
    }
}
