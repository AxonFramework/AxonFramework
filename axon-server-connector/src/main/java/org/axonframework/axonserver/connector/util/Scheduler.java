package org.axonframework.axonserver.connector.util;

import java.util.concurrent.TimeUnit;

/**
 * Schedules tasks to be executed in future.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 */
public interface Scheduler {

    /**
     * Schedules a {@code command} to be executed periodically after {@code initialDelay}. Frequency of execution is
     * represented by {@code delay}. New task is scheduled after execution of current one.
     *
     * @param command      the task to be executed
     * @param initialDelay for how long the first execution is delayed
     * @param delay        frequency of task execution
     * @param timeUnit     the time unit
     * @return information about the schedule, and means to cancel it
     */
    ScheduledTask scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit timeUnit);

    /**
     * Cancels all scheduled tasks.
     */
    void shutdownNow();

    interface ScheduledTask {

        /**
         * Cancels the scheduled registration.
         *
         * @param mayInterruptIfRunning {@code true} if the thread executing this task should be interrupted; otherwise,
         *                              in-progress tasks are allowed to complete
         */
        void cancel(boolean mayInterruptIfRunning);
    }
}
