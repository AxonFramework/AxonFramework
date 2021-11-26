/*
 * Copyright (c) 2010-2021. Axon Framework
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

import org.axonframework.messaging.HandlerExecutionException;

/**
 * An {@link HandlerExecutionException} implementation indicating an exception occurred during event handling. This
 * exception is typically used to wrap checked exceptions thrown from an Event Handler.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class EventExecutionException extends HandlerExecutionException {

    private static final long serialVersionUID = -7002989047107235088L;

    // TODO: 26-11-21 group into ProcessingIdentifier?
    private final String sequenceIdentifier;
    private final String processingGroup;

    /**
     * Constructs an {@link EventExecutionException} using the provided {@code message}, {@code cause}, {@code
     * sequenceIdentifier} and {@code processingGroup}. Combines the {@code sequenceIdentifier} and {@code
     * processingGroup} with a dash ({@code `-`}) as the {@code details} of this {@link HandlerExecutionException}.
     *
     * @param message            the message describing the exception
     * @param cause              the cause of the exception
     * @param sequenceIdentifier the identifier defining the sequence of the event for which execution failed
     * @param processingGroup    the name of the processing group the exception occurred in
     */
    public EventExecutionException(String message,
                                   Throwable cause,
                                   String sequenceIdentifier,
                                   String processingGroup) {
        super(message, cause, sequenceIdentifier + "-" + processingGroup);
        this.sequenceIdentifier = sequenceIdentifier;
        this.processingGroup = processingGroup;
    }

    /**
     * Returns the sequence identifier of the event for which execution failed.
     *
     * @return the sequence identifier of the event for which execution failed
     */
    public String getSequenceIdentifier() {
        return sequenceIdentifier;
    }

    /**
     * Returns the name of processing group this exception occurred in.
     *
     * @return the name of processing group this exception occurred in
     */
    public String getProcessingGroup() {
        return processingGroup;
    }
}
