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

import org.axonframework.core.util.Assert;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Container for events related to a single aggregate. All events added to this container will automatically be assigned
 * the aggregate identifier and a sequence number.
 *
 * @author Allard Buijze
 * @see org.axonframework.core.DomainEvent
 * @see org.axonframework.core.AbstractAggregateRoot
 * @since 0.1
 */
class EventContainer {

    private final List<DomainEvent> events = new LinkedList<DomainEvent>();
    private final UUID aggregateIdentifier;
    private Long lastSequenceNumber;
    private long firstSequenceNumber = 0;

    /**
     * Initialize an EventContainer for an aggregate with the given <code>aggregateIdentifier</code>. This identifier
     * will be attached to all incoming events.
     *
     * @param aggregateIdentifier the aggregate identifier to assign to this container
     */
    public EventContainer(UUID aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Add an event to this container.
     * <p/>
     * Events should either be already assigned to the aggregate with the same identifier as this container, or have no
     * aggregate assigned yet. If an event has a sequence number assigned, it must follow directly upon the sequence
     * number of the event that was previously added.
     *
     * @param event the event to add to this container
     */
    public void addEvent(DomainEvent event) {
        Assert.isTrue(event.getSequenceNumber() == null
                || lastSequenceNumber == null
                || event.getSequenceNumber().equals(lastSequenceNumber + 1),
                      "The given event's sequence number is discontinuous");

        Assert.isTrue(event.getAggregateIdentifier() == null
                || aggregateIdentifier.equals(event.getAggregateIdentifier()),
                      "The Identifier of the event does not match the Identifier of the EventContainer");

        if (event.getAggregateIdentifier() == null) {
            event.setAggregateIdentifier(aggregateIdentifier);
        }

        if (event.getSequenceNumber() == null) {
            event.setSequenceNumber(newSequenceNumber());
        } else {
            lastSequenceNumber = event.getSequenceNumber();
        }

        events.add(event);
    }

    /**
     * Read the events inside this container using an {@link org.axonframework.core.EventStream}.
     *
     * @return an EventStream providing access to the events in this container
     */
    public EventStream getInputStream() {
        return new SimpleEventStream(events, aggregateIdentifier);
    }

    /**
     * Returns the aggregate identifier assigned to this container.
     *
     * @return the aggregate identifier assigned to this container
     */
    public UUID getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * Sets the first sequence number that should be assigned to an incoming event.
     *
     * @param firstSequenceNumber the sequence number to assign to the first incoming event
     */
    public void setFirstSequenceNumber(long firstSequenceNumber) {
        Assert.state(events.size() == 0, "Cannot set first sequence number if events have already been added");
        this.firstSequenceNumber = firstSequenceNumber;
    }

    /**
     * Returns the sequence number of the event last added to this container
     *
     * @return the sequence number of the last event
     */
    public Long getLastSequenceNumber() {
        return lastSequenceNumber;
    }

    /**
     * Clears the events in this container. The sequence number is not modified by this call.
     */
    public void clear() {
        events.clear();
    }

    /**
     * Returns the number of events currently inside this container
     *
     * @return the number of events in this container
     */
    public int size() {
        return events.size();
    }

    private long newSequenceNumber() {
        if (lastSequenceNumber == null) {
            lastSequenceNumber = firstSequenceNumber;
        } else {
            lastSequenceNumber++;
        }
        return lastSequenceNumber;
    }
}
