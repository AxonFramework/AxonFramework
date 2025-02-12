package org.axonframework.messaging.timeout;

import org.slf4j.Logger;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Represents a task with a timeout. The task will be interrupted when the {@code timeout} is reached. If the
 * {@code warningThreshold} is lower than the timeout, warnings will be logged at the configured {@code warningInterval}
 * until the timeout is reached. All times are in milliseconds.
 * <p>
 * Warning logging will include the task's name, the current time taken by the task and its remaining time to execute.
 * The stack trace of the thread handling the message will also be included in the log, up to the point where the task was started.
 * <p>
 * Once the {@code timeout} is reached, a message will be logged with the current stack trace of the thread handling the message,
 * and the thread will be interrupted. If the task is completed before the timeout, the task should be marked as completed.
 *
 * @author Mitchell Herrijgers
 * @since 4.11.0
 */
class AxonTimeLimitedTask {

    private final Thread thread;
    private final int timeout;
    private final int warningThreshold;
    private final int warningInterval;
    private final String taskName;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Logger logger;
    private boolean completed = false;
    private boolean interrupted = false;
    private long startTimeMs = -1;
    private Future<?> currentScheduledFuture = null;
    private String startStackTrace;


    /**
     * Creates a new {@link AxonTimeLimitedTask} for the given {@code task} with the given {@code timeout},
     * {@code warningThreshold} and {@code warningInterval}. Runs the provided task on the current thread after
     * scheduling a timeout and warnings on another thread.
     * <p>
     * If you wish to provide a logger of your own, or your own {@code scheduledExecutorService}, use
     * {@link #AxonTimeLimitedTask(String, int, int, int, ScheduledExecutorService, Logger)}.
     *
     * @param taskName         The task's name to be included in the logging
     * @param timeout          The timeout in milliseconds
     * @param warningThreshold The threshold in milliseconds after which a warning is logged. Setting this to a value
     *                         equal or higher than {@code timeout} will disable warnings.
     * @param warningInterval  The interval in milliseconds between warnings.
     */
    public AxonTimeLimitedTask(String taskName,
                               int timeout,
                               int warningThreshold,
                               int warningInterval) {
        this(taskName,
             timeout,
             warningThreshold,
             warningInterval,
             AxonTaskJanitor.INSTANCE,
             AxonTaskJanitor.LOGGER
        );
    }

    /**
     * Creates a new {@link AxonTimeLimitedTask} for the given {@code task} with the given {@code timeout},
     * {@code warningThreshold} and {@code warningInterval}. For scheduling, the provided
     * {@code scheduledExecutorService} will be used. To log warnings and errors, the provided {@code logger} will be
     * used. Runs the provided task on the current thread after scheduling a timeout and warnings on the provided
     * {@code scheduledExecutorService}.
     * <p>
     * If you do not wish to provide a logger of your own {@code scheduledExecutorService}, use
     * {@link #AxonTimeLimitedTask(String, int, int, int)}.
     * <p>
     *
     * @param taskName                 The task's name to be included in the logging
     * @param timeout                  The timeout in milliseconds
     * @param warningThreshold         The threshold in milliseconds after which a warning is logged. Setting this to a
     *                                 value equal or higher than {@code timeout} will disable warnings.
     * @param warningInterval          The interval in milliseconds between warnings.
     * @param scheduledExecutorService The executor service to schedule the timeout and warnings
     * @param logger                   The logger to log the warnings and errors
     */
    public AxonTimeLimitedTask(String taskName,
                               int timeout,
                               int warningThreshold,
                               int warningInterval,
                               ScheduledExecutorService scheduledExecutorService,
                               Logger logger) {
        this.taskName = taskName;
        this.timeout = timeout;
        this.warningThreshold = warningThreshold;
        this.warningInterval = warningInterval;
        this.scheduledExecutorService = scheduledExecutorService;
        this.logger = logger;
        this.thread = Thread.currentThread();
    }

    /**
     * Starts the task, scheduling the first warning or immediate interrupt. Once the task is completed, the
     * {@link #complete()} method should be called.
     * Once started, the task cannot be started again.
     */
    public void start() {
        if (startTimeMs != -1) {
            throw new IllegalStateException("Task can only be run once");
        }
        startTimeMs = System.currentTimeMillis();
        startStackTrace = thread.getStackTrace()[2].getClassName();

        if (warningThreshold < 0 || warningThreshold >= timeout) {
            scheduleImmediateInterrupt();
        } else {
            scheduleFirstWarning();
        }
    }

    /**
     * Marks the task as completed. Cancels the current future warning or interrupt if any exists.
     */
    public void complete() {
        completed = true;
        if (currentScheduledFuture != null) {
            currentScheduledFuture.cancel(false);
            currentScheduledFuture = null;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("{} completed", taskName);
        }
    }

    /**
     * When a warning has not been configured, this method schedules an interrupt immediately for the timeout.
     */
    private void scheduleImmediateInterrupt() {
        if (logger.isTraceEnabled()) {
            logger.trace("{} will be interrupted after [{}ms]",
                         taskName,
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
                    taskName,
                    timeout,
                    warningThreshold);
        }
        scheduleWarning(warningThreshold);
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
                taskName,
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
                        taskName,
                        timeout,
                        getCurrentStackTrace());
                thread.interrupt();
                interrupted = true;
            }
        }, remainingTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the current stack trace of the thread handling the message. Cuts off the stack trace at the point where
     * the original {@link #start()} method was called.
     *
     * @return The current stack trace of the thread handling the message
     */
    private String getCurrentStackTrace() {
        StackTraceElement[] stackTrace = thread.getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : stackTrace) {
            sb.append(element).append("\n");
            // This is the start of the stack trace of the framework internals calling the method
            if (element.toString().contains(startStackTrace)) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Returns whether the task has been completed. If the task was still running, or was interrupted, this will return
     * {@code false}.
     *
     * @return {@code true} if the task has been completed successfully, {@code false} otherwise
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Returns whether the task has been interrupted. If the task was still running, or was completed, this will return
     * {@code false}.
     *
     * @return {@code true} if the task has been interrupted, {@code false} otherwise
     */
    public boolean isInterrupted() {
        return interrupted;
    }
}
