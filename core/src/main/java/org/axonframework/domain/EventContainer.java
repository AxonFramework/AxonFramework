/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.domain;

import org.axonframework.common.Assert;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container for events related to a single aggregate. The container will wrap registered event (payload) and
 * metadata in an DomainEventMessage and automatically assign the aggregate identifier and the next sequence number.
 * <p/>
 * The EventContainer also takes care of the invocation of EventRegistrationCallbacks once events are registered with
 * this aggregate for publication.
 * <p/>
 * This implementation is <em>not</em> thread safe and should only be used with proper locking in place. Generally,
 * only a single thread will be modifying an aggregate at any given time.
 *
 * @author Allard Buijze
 * @see DomainEventMessage
 * @see org.axonframework.domain.AbstractAggregateRoot
 * @since 0.1
 */
public class EventContainer implements Serializable {

    private static final long serialVersionUID = -39816393359395878L;

    private final List<DomainEventMessage> events = new ArrayList<DomainEventMessage>();
    private final Object aggregateIdentifier;
    private Long lastCommittedSequenceNumber;
    private transient Long lastSequenceNumber; // NOSONAR (intentionally not set by deserialization)
    private transient List<EventRegistrationCallback> registrationCallbacks; // NOSONAR

    /**
     * Initialize an EventContainer for an aggregate with the given <code>aggregateIdentifier</code>. This identifier
     * will be attached to all incoming events.
     *
     * @param aggregateIdentifier the aggregate identifier to assign to this container
     */
    public EventContainer(Object aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Add an event to this container.
     * <p/>
     * Events should either be already assigned to the aggregate with the same identifier as this container, or have no
     * aggregate assigned yet. If an event has a sequence number assigned, it must follow directly upon the sequence
     * number of the event that was previously added.
     *
     * @param metaData the metaData of the event to add to this container
     * @param payload  the payload of the event to add to this container
     * @param <T>      the type of payload contained in the event
     * @return the DomainEventMessage added to the container
     */
    public <T> DomainEventMessage<T> addEvent(MetaData metaData, T payload) {
        DomainEventMessage<T> event = new GenericDomainEventMessage<T>(aggregateIdentifier, newSequenceNumber(),
                                                                       payload, metaData);
        if (registrationCallbacks != null) {
            for (EventRegistrationCallback callback : registrationCallbacks) {
                event = callback.onRegisteredEvent(event);
            }
        }
        lastSequenceNumber = event.getSequenceNumber();
        events.add(event);
        return event;
    }

    /**
     * Read the events inside this container using a {@link org.axonframework.domain.DomainEventStream}. The returned
     * stream is a snapshot of the uncommitted events in the aggregate at the time of the invocation. Once returned,
     * newly applied events are not accessible from the returned event stream.
     *
     * @return a DomainEventStream providing access to the events in this container
     */
    public DomainEventStream getEventStream() {
        return new SimpleDomainEventStream(events);
    }

    /**
     * Returns the aggregate identifier assigned to this container.
     *
     * @return the aggregate identifier assigned to this container
     */
    public Object getAggregateIdentifier() {
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
        } else if (lastSequenceNumber == null) {
            lastSequenceNumber = events.get(events.size() - 1).getSequenceNumber();
        }
        return lastSequenceNumber;
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
        if (registrationCallbacks != null) {
            registrationCallbacks.clear();
        }
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
        Long currentSequenceNumber = getLastSequenceNumber();
        if (currentSequenceNumber == null) {
            return 0;
        }
        return currentSequenceNumber + 1;
    }

    /**
     * Returns an unmodifiable version of the backing list of events.
     *
     * @return a list containing the events in this container
     */
    public List<DomainEventMessage> getEventList() {
        return Collections.unmodifiableList(events);
    }

    /**
     * Adds an EventRegistrationCallback, which is invoked when the aggregate owning this container registers an Event.
     * These callbacks are cleared when the Events are committed.
     *
     * @param eventRegistrationCallback The callback to notify when an Event is registered.
     */
    public void addEventRegistrationCallback(EventRegistrationCallback eventRegistrationCallback) {
        if (registrationCallbacks == null) {
            this.registrationCallbacks = new ArrayList<EventRegistrationCallback>();
        }
        this.registrationCallbacks.add(eventRegistrationCallback);
        for (int i = 0; i < events.size(); i++) {
            events.set(i, eventRegistrationCallback.onRegisteredEvent(events.get(i)));
        }
    }
}
