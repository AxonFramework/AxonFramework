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

package org.axonframework.core;

import org.joda.time.LocalDateTime;

import java.util.UUID;

/**
 * Base class for all types of events. Contains the event identifier and timestamp.
 *
 * @author Allard Buijze
 * @since 0.4
 */
public abstract class EventBase implements Event {

    private final LocalDateTime timestamp;
    private final UUID eventIdentifier;

    /**
     * Initialize a new event. This constructor will set the event identifier to a random UUID and the timestamp to the
     * current date and time.
     */
    protected EventBase() {
        timestamp = new LocalDateTime();
        eventIdentifier = UUID.randomUUID();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID getEventIdentifier() {
        return eventIdentifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Checks the equality of two events. Events are equal if they have the same type and identifier.
     *
     * @param o the object to compare this event to
     * @return <code>true</code> if <code>o</code> is equal to this event instance
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EventBase that = (EventBase) o;

        return eventIdentifier.equals(that.eventIdentifier);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return eventIdentifier.hashCode();
    }
}
