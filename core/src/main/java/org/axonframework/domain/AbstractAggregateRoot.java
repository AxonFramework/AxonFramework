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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

/**
 * Very basic implementation of the AggregateRoot interface. It provides the mechanism to keep track of uncommitted
 * events and maintains a version number based on the number of events generated.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class AbstractAggregateRoot implements AggregateRoot, Serializable {

    private static final long serialVersionUID = -1617322323158336719L;

    private final AggregateIdentifier identifier;
    private transient EventContainer uncommittedEvents;
    private volatile transient Long lastCommitted;

    /**
     * Initializes the aggregate root using a random aggregate identifier.
     */
    protected AbstractAggregateRoot() {
        this(new UUIDAggregateIdentifier());
    }

    /**
     * Initializes the aggregate root using the provided aggregate identifier.
     *
     * @param identifier the identifier of this aggregate
     */
    protected AbstractAggregateRoot(AggregateIdentifier identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("Aggregate identifier may not be null.");
        }
        this.identifier = identifier;
        uncommittedEvents = new EventContainer(identifier);
    }

    /**
     * Registers an event to be published when the aggregate is saved.
     *
     * @param event the event to register
     */
    protected void registerEvent(DomainEvent event) {
        uncommittedEvents.addEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DomainEventStream getUncommittedEvents() {
        return uncommittedEvents.getEventStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AggregateIdentifier getIdentifier() {
        return identifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commitEvents() {
        lastCommitted = uncommittedEvents.getLastSequenceNumber();
        uncommittedEvents.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUncommittedEventCount() {
        return uncommittedEvents.size();
    }

    /**
     * Initialize the event stream using the given sequence number of the last known event. This will cause the new
     * events to be attached to this aggregate to be assigned a continuous sequence number.
     *
     * @param lastSequenceNumber The sequence number of the last event from this aggregate
     */
    protected void initializeEventStream(long lastSequenceNumber) {
        uncommittedEvents.initializeSequenceNumber(lastSequenceNumber);
        lastCommitted = lastSequenceNumber >= 0 ? lastSequenceNumber : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getVersion() {
        return lastCommitted;
    }

    @SuppressWarnings({"unchecked"})
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        lastCommitted = (Long) in.readObject();
        uncommittedEvents = new EventContainer(identifier);
        uncommittedEvents.initializeSequenceNumber(lastCommitted);
        List<DomainEvent> uncommitted = (List<DomainEvent>) in.readObject();
        for (DomainEvent uncommittedEvent : uncommitted) {
            uncommittedEvents.addEvent(uncommittedEvent);
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(lastCommitted);
        out.writeObject(uncommittedEvents.getEvents());
    }
}
