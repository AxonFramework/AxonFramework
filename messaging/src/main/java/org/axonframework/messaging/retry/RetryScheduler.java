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

package org.axonframework.messaging.retry;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface towards a mechanism that decides whether to schedule a message for dispatching when a previous attempts
 * failed.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface RetryScheduler {

    /**
     * Schedules the given {@code message} to retry dispatching using the given {@code dispatching} function.
     * <p>
     * Implementations may execute the retries immediately or schedule them later retry. The returned
     * {@code MessageStream} may complete after several retry attempts or immediately, depending on whether retries are
     * configured or if the cause indicates that a retry could possibly provide a different outcome.
     * <p>
     * If the scheduler determines that a retry can not lead to a different outcome, it must return immediately with a
     * failed {@code MessageStream} holding the original given {@code cause} as its failure.
     *
     * @param message           The Message being dispatched
     * @param processingContext The context under which the message is dispatched
     * @param cause             The cause of the failure.
     * @param dispatcher        The function to execute individual retries
     * @param <M>               The type of message to (re)dispatch
     * @param <R>               The type of message expected as a result of dispatching
     * @return a MessageStream representing the result of the last attempt
     */
    <M extends Message<?>, R extends Message<?>> MessageStream<R> scheduleRetry(
            @Nonnull M message,
            @Nullable ProcessingContext processingContext,
            @Nonnull Throwable cause,
            @Nonnull Dispatcher<M, R> dispatcher);

    /**
     * Represents the logic to dispatch a message and attempt to retrieve the result
     *
     * @param <M> The type of Message to dispatch
     * @param <R> The expected type of Message returned
     */
    interface Dispatcher<M extends Message<?>, R extends Message<?>> {

        /**
         * @param message           The Message to dispatch
         * @param processingContext The processing context to dispatch the Message under
         * @return a Stream containing the result Messages
         */
        MessageStream<R> dispatch(@Nonnull M message,
                                  @Nullable ProcessingContext processingContext);
    }
}
