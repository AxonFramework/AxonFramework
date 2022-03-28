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

import java.util.List;
import javax.annotation.Nonnull;

/**
 * Describes the context of an error.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class ErrorContext {

    private final String eventProcessor;
    private final Throwable error;
    private final List<? extends EventMessage<?>> failedEvents;

    /**
     * Instantiate an ErrorContext for the given {@code eventProcessor}, due to the given {@code error}, with the given
     * {@code failedEvents}.
     *
     * @param eventProcessor the name of the event processor that failed to process the given events
     * @param error          the error that was raised during processing
     * @param failedEvents   the list of events that triggered the error
     */
    public ErrorContext(@Nonnull String eventProcessor, @Nonnull Throwable error,
                        @Nonnull List<? extends EventMessage<?>> failedEvents) {
        this.eventProcessor = eventProcessor;
        this.error = error;
        this.failedEvents = failedEvents;
    }

    /**
     * Returns the name of the Event Processor where the error occurred.
     *
     * @return the name of the Event Processor where the error occurred
     */
    public String eventProcessor() {
        return eventProcessor;
    }

    /**
     * Returns the error that was raised in the processor
     *
     * @return the error that was raised in the processor
     */
    public Throwable error() {
        return error;
    }

    /**
     * The events part of the batch that failed. May be empty if an error occurred outside of the scope of processing a
     * batch (e.g. while preparing the next batch).
     *
     * @return events part of the batch that failed, if any
     */
    public List<? extends EventMessage<?>> failedEvents() {
        return failedEvents;
    }
}
