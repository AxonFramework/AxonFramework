/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.errorhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Interface of the error handler that will be invoked if event processing fails.
 * <p>
 * The error handler is generally invoked by an {@link EventProcessor} when
 * the {@link ProcessingContext} created to coordinate the event processing was
 * rolled back.
 *
 * @author Rene de Waele
 * @since 3.0.0
 */
@FunctionalInterface
public interface ErrorHandler {

    /**
     * Handle an error raised during event processing.
     * <p>
     * Generally this means that the {@link ProcessingContext} created to
     * coordinate the event processing was rolled back. Using default configuration the {@code ErrorHandler} is only
     * invoked when there is a serious error, for instance when the database transaction connected to the
     * {@code ProcessingContext} can not be committed.
     * <p>
     * The error handler has the option to simply log or ignore the error. Depending on the type of
     * {@code EventProcessor} this will put an end to the processing of any further events (in case of a
     * {@link PooledStreamingEventProcessor}) or simply skip over the list of {@code failedEvents} in the given
     * {@code errorContext}.
     * <p>
     * Note that although the {@code ProcessingContext} and hence any related database transactions have been rolled
     * back when the error handler is invoked, the processing of one or more of the {@link ErrorContext#failedEvents()}
     * may in fact have caused other side effects which could not be reverted.
     *
     * @param errorContext Contextual information describing the error.
     * @throws Exception If this handler decides to propagate the error.
     */
    void handleError(@Nonnull ErrorContext errorContext) throws Exception;
}
