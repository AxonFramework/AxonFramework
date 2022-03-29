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

package org.axonframework.eventhandling;

import javax.annotation.Nonnull;

/**
 * Interface of the error handler that will be invoked if event processing fails. The error handler is generally invoked
 * by an EventProcessor when the UnitOfWork created to coordinate the event processing was rolled back.
 *
 * @author Rene de Waele
 */
public interface ErrorHandler {

    /**
     * Handle an error raised during event processing. Generally this means that the UnitOfWork created to coordinate
     * the event processing was rolled back. Using default configuration the ErrorHandler is only invoked when there is
     * a serious error, for instance when the database transaction connected to the UnitOfWork can not be committed.
     * <p>
     * The error handler has the option to simply log or ignore the error. Depending on the type of EventProcessor this
     * will put an end to the processing of any further events (in case of a {@link TrackingEventProcessor}) or simply
     * skip over the list of given {@code failedEvents}.
     * <p>
     * Note that although the UnitOfWork and hence any related database transactions have been rolled back when the
     * error handler is invoked, the processing of one or more of the failedEvents may in fact have caused other side
     * effects which could not be reverted.
     *
     * @param errorContext Contextual information describing the error
     * @throws Exception if the handler decides to propagate the error
     */
    void handleError(@Nonnull ErrorContext errorContext) throws Exception;

}
