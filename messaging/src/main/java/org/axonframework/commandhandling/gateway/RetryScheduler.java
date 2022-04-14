/*
 * Copyright (c) 2010-2022. Axon Framework
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

import java.util.List;
import javax.annotation.Nonnull;

/**
 * Interface towards a mechanism that decides whether to schedule a command for execution when a previous attempts
 * resulted in an exception.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface RetryScheduler {

    /**
     * Inspect the given {@code commandMessage} that failed with given {@code lastFailure}. The given {@code failures}
     * provides a list of previous failures known for this command. The {@code commandDispatch} task can be used to
     * schedule the command for dispatching.
     * <p/>
     * The return value of this method indicates whether the command has been scheduled for a retry. When {@code true},
     * the original callbacks should not be invoked, as command execution is subject to a retry. When {@code false}, the
     * failure is interpreted as terminal and the callback will be invoked with the last failure recorded.
     * <p/>
     * If the implementation throws an Exception, that exception is passed as the failure to the original callback.
     *
     * @param commandMessage  The Command Message being dispatched
     * @param lastFailure     The last failure recorded for this command
     * @param failures        A condensed view of all known failures of this command. Each element in the array
     *                        represents the cause of the element preceding it.
     * @param commandDispatch The task to be executed to retry a command
     * @return {@code true} if the command has been rescheduled, otherwise {@code false}
     */
    boolean scheduleRetry(@Nonnull CommandMessage commandMessage, @Nonnull RuntimeException lastFailure,
                          @Nonnull List<Class<? extends Throwable>[]> failures, @Nonnull Runnable commandDispatch);
}
