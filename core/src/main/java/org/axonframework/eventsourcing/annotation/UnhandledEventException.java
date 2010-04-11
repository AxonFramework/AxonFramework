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

package org.axonframework.eventsourcing.annotation;

import org.axonframework.domain.Event;

/**
 * Raised when an event could not be handled by an Aggregate. This is an exceptional situation, as an aggregate is
 * responsible for generating the events that it should be able to handle. This typically means an event handler method
 * is missing.
 * <p/>
 * To prevent this exception, make a method that accepts a {@link org.axonframework.domain.DomainEvent} as sole
 * parameter and annotate it with {@link org.axonframework.eventhandling.annotation.EventHandler}.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public class UnhandledEventException extends RuntimeException {

    private final Event unhandledEvent;

    /**
     * Initialize the exception with the given <code>message</code> and <code>unhandledEvent</code>.
     *
     * @param message        a descriptive message of the cause of the exception
     * @param unhandledEvent The event for which no handler could be found
     */
    public UnhandledEventException(String message, Event unhandledEvent) {
        super(message);
        this.unhandledEvent = unhandledEvent;
    }

    /**
     * Returns the events that could not be handled
     *
     * @return the unhandled event
     */
    public Event getUnhandledEvent() {
        return unhandledEvent;
    }
}
