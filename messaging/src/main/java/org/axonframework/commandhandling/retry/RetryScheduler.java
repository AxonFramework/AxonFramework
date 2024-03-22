/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.commandhandling.retry;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface towards a mechanism that decides whether to schedule a command for execution when a previous attempts
 * resulted in an exception.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface RetryScheduler {

    /**
     * Schedules the given {@code commandMessage} to retry dispatching using the given {@code dispatching} function.
     * <p>
     * Implementations may execute the retries immediately or schedule them later retry. The returned
     * {@code CompletableFuture} may complete after several retry attempts or immediately, depending on whether retries
     * are configured or if the cause indicates that a retry could possibly provide a different outcome.
     * <p>
     * If the scheduler determines that a retry can not lead to a different outcome, it must return immediately with a
     * failed {@code CompletableFuture} holding the original given {@code cause} as its failure.
     *
     * @param commandMessage    The Command Message being dispatched
     * @param processingContext The context under which the message is dispatched
     * @param cause             The cause of the failure.
     * @param dispatcher        The function to execute individual retries
     * @return a CompletableFuture providing the result of the last retry
     */
    CompletableFuture<? extends Message<?>> scheduleRetry(
            @Nonnull CommandMessage<?> commandMessage,
            @Nullable ProcessingContext processingContext,
            @Nonnull Throwable cause,
            @Nonnull Dispatcher dispatcher);

    interface Dispatcher {

        CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> message,
                                                         @Nullable ProcessingContext processingContext);
    }
}
