package org.axonframework.messaging.timeout;

import org.axonframework.common.AxonThreadFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for running tasks with a timeout. It does this without switching threads for the running task, to not
 * interfere with thread local state. Instead, it uses a ScheduledExecutorService to schedule a task that interrupts the
 * running task when the timeout is reached.
 *
 * @author Mitchell Herrijgers
 * @see AxonTaskWithTimeout
 * @since 4.11
 */
public class AxonTaskUtils {

    public static final ScheduledExecutorService INSTANCE = createJanitorExecutorService();

    private AxonTaskUtils() {
        // Utility class
    }

    /**
     * Run a taskName with a timeout. The task will be interrupted when the timeout is reached. Uses the
     * {@link #INSTANCE} ScheduledExecutorService.
     *
     * @param taskName         The task's name to be included in the logging
     * @param task             The task to run
     * @param timeout          The timeout in milliseconds
     * @param warningThreshold The threshold in milliseconds after which a warning is logged. Setting this to a value
     *                         higher than {@code timeout} will disable warnings.
     * @param warningInterval  The interval in milliseconds between warnings.
     * @param <T>              The type of the result
     * @return The result of the task
     * @throws Exception Throws exceptions thrown by the {@code task} or a {@link TimeoutException} when the task times
     *                   out.
     */
    public static <T> T runTaskWithTimeout(String taskName,
                                           Callable<T> task,
                                           int timeout,
                                           int warningThreshold,
                                           int warningInterval
    ) throws Exception {
        return runTaskWithTimeout(taskName, task, timeout, warningThreshold, warningInterval, INSTANCE);
    }


    /**
     * Run a taskName with a timeout on a specific executor. The task will be interrupted when the timeout is reached.
     *
     * @param taskName         The task's name to be included in the logging
     * @param task             The task to run
     * @param timeout          The timeout in milliseconds
     * @param warningThreshold The threshold in milliseconds after which a warning is logged. Setting this to a value
     *                         higher than {@code timeout} will disable warnings.
     * @param warningInterval  The interval in milliseconds between warnings.
     * @param <T>              The type of the result
     * @return The result of the task
     * @throws Exception Throws exceptions thrown by the {@code task} or a {@link TimeoutException} when the task times
     *                   out.
     */
    public static <T> T runTaskWithTimeout(String taskName,
                                           Callable<T> task,
                                           int timeout,
                                           int warningThreshold,
                                           int warningInterval,
                                           ScheduledExecutorService executorService) throws Exception {
        return new AxonTaskWithTimeout<T>(
                taskName,
                timeout,
                warningThreshold,
                warningInterval,
                executorService
        ).runWithTimeout(task);
    }

    /**
     * Creates the ScheduledExecutorService used for scheduling the interrupting task. It only has one thread as the
     * load is very low. Cancelling the tasks will clean it up to reduce memory pressure.
     *
     * @return The ScheduledExecutorService
     */
    private static ScheduledThreadPoolExecutor createJanitorExecutorService() {
        ScheduledThreadPoolExecutor housekeeper = new ScheduledThreadPoolExecutor(1,
                                                                                  new AxonThreadFactory("axon-janitor"));
        // Clean up tasks in the queue when canceled. Performance is equal but reduces memory pressure.
        housekeeper.setRemoveOnCancelPolicy(true);
        return housekeeper;
    }
}
