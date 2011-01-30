/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.domain;

import org.axonframework.util.Assert;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;

/**
 * Container for events related to a single aggregate. All events added to this container will automatically be assigned
 * the aggregate identifier and a sequence number.
 *
 * @author Allard Buijze
 * @see org.axonframework.domain.DomainEvent
 * @see org.axonframework.domain.AbstractAggregateRoot
 * @since 0.1
 */
class EventContainer implements Serializable {

    private static final long serialVersionUID = -3981639335939587822L;

    private final Deque<DomainEvent> events = new LinkedList<DomainEvent>();
    private final AggregateIdentifier aggregateIdentifier;
    private Long lastCommittedSequenceNumber;

    /**
     * Initialize an EventContainer for an aggregate with the given <code>aggregateIdentifier</code>. This identifier
     * will be attached to all incoming events.
     *
     * @param aggregateIdentifier the aggregate identifier to assign to this container
     */
    public EventContainer(AggregateIdentifier aggregateIdentifier) {
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
                              || getLastSequenceNumber() == null
                              || event.getSequenceNumber().equals(getLastSequenceNumber() + 1),
                      "The given event's sequence number is discontinuous");

        Assert.isTrue(event.getAggregateIdentifier() == null
                              || aggregateIdentifier.equals(event.getAggregateIdentifier()),
                      "The Identifier of the event does not match the Identifier of the EventContainer");

        if (event.getAggregateIdentifier() == null) {
            event.setAggregateIdentifier(aggregateIdentifier);
        }

        if (event.getSequenceNumber() == null) {
            event.setSequenceNumber(newSequenceNumber());
        }

        events.add(event);
    }

    /**
     * Read the events inside this container using a {@link org.axonframework.domain.DomainEventStream}. The returned
     * stream is a snapshot of the uncommitted events in the aggregate at the time of the invocation. Once returned,
     * newly applied events are not accessible from the returned event stream.
     *
     * @return a DomainEventStream providing access to the events in this container
     */
    public DomainEventStream getEventStream() {
        return new SimpleDomainEventStream(new ArrayList<DomainEvent>(events));
    }

    /**
     * Returns the aggregate identifier assigned to this container.
     *
     * @return the aggregate identifier assigned to this container
     */
    public AggregateIdentifier getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * Sets the first sequence number that should be assigned to an incoming event.
     *
     * @param lastKnownSequenceNumber the sequence number of the last known event
     */
    public void initializeSequenceNumber(Long lastKnownSequenceNumber) {
        Assert.state(events.size() == 0, "Cannot set first sequence number if events have already been added");
        lastCommittedSequenceNumber = lastKnownSequenceNumber;
    }

    /**
     * Returns the sequence number of the event last added to this container.
     *
     * @return the sequence number of the last event
     */
    public Long getLastSequenceNumber() {
        if (events.isEmpty()) {
            return lastCommittedSequenceNumber;
        } else {
            return events.peekLast().getSequenceNumber();
        }
    }

    /**
     * Returns the sequence number of the last committed event, or <code>null</code> if no events have been committed.
     *
     * @return the sequence number of the last committed event
     */
    public Long getLastCommittedSequenceNumber() {
        return lastCommittedSequenceNumber;
    }

    /**
     * Clears the events in this container. The sequence number is not modified by this call.
     */
    public void commit() {
        lastCommittedSequenceNumber = getLastSequenceNumber();
        events.clear();
    }

    /**
     * Returns the number of events currently inside this container.
     *
     * @return the number of events in this container
     */
    public int size() {
        return events.size();
    }

    private long newSequenceNumber() {
        Long lastSequenceNumber = getLastSequenceNumber();
        if (lastSequenceNumber == null) {
            return 0;
        }
        return lastSequenceNumber + 1;
    }
}
