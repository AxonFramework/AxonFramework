/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.core.eventhandler.annotation;

import org.axonframework.core.Event;

/**
 * Exception indicating that an event handler refused to process an event. A typical case is that the event handler is
 * being shut down.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class EventHandlingRejectedException extends RuntimeException {

    private Event rejectedEvent;

    /**
     * Initialize the exception with given <code>message</code>, <code>cause</code> and <code>event</code>
     *
     * @param message description of the cause of the exception
     * @param cause   The exception that caused the event handling rejection
     * @param event   the rejected event
     */
    public EventHandlingRejectedException(String message, Throwable cause, Event event) {
        super(message, cause);
        this.rejectedEvent = event;
    }

    /**
     * Returns the event for which handling was refused
     *
     * @return the refused event
     */
    public Event getRejectedEvent() {
        return rejectedEvent;
    }
}
