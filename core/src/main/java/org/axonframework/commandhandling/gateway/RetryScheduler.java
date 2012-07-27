package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandMessage;

import java.util.List;

/**
 * Interface towards a mechanism that decides whether to schedule a command for execution when a previous attempts
 * resulted in an exception.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface RetryScheduler {

    /**
     * Inspect the given <code>commandMessage</code> that failed with given <code>lastFailure</code>. The given
     * <code>failures</code> provides a list of previous failures known for this command. The
     * <code>commandDispatch</code> task can be used to schedule the command for dispatching.
     * <p/>
     * The return value of this method indicates whether the command has been scheduled for a retry. When
     * <code>true</code>, the original callbacks should not be invoked, as command execution is subject to a retry.
     * When <code>false</code>, the failure is interpreted as terminal and the callback will be invoked with the last
     * failure recorded.
     * <p/>
     * If the implementation throws an Exception, that exception is passed as the failure to the original callback.
     *
     * @param commandMessage  The Command Message being dispatched
     * @param lastFailure     The last failure recorded for this command
     * @param failures        A condensed view of all known failures of this command. Each element in the array
     *                        represents the cause of the element preceding it.
     * @param commandDispatch The task to be executed to retry a command
     * @return <code>true</code> if the command has been rescheduled, otherwise <code>false</code>
     */
    boolean scheduleRetry(CommandMessage commandMessage, RuntimeException lastFailure,
                          List<Class<? extends Throwable>[]> failures, Runnable commandDispatch);
}
