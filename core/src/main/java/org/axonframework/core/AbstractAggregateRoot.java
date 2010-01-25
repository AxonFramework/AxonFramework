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

import java.util.UUID;

/**
 * @author Allard Buijze
 */
public abstract class AbstractAggregateRoot implements VersionedAggregateRoot {

    private final EventContainer uncommittedEvents;
    private final UUID identifier;
    private volatile Long lastCommitted;

    /**
     * Initializes the aggregate root using a random aggregate identifier.
     */
    protected AbstractAggregateRoot() {
        this(UUID.randomUUID());
    }

    /**
     * Initializes the aggregate root using the provided aggregate identifier.
     *
     * @param identifier the identifier of this aggregate
     */
    protected AbstractAggregateRoot(UUID identifier) {
        this.identifier = identifier;
        uncommittedEvents = new EventContainer(identifier);
    }

    /**
     * Registers an event to be published when the aggregate is saved
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
    public EventStream getUncommittedEvents() {
        return uncommittedEvents.getInputStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID getIdentifier() {
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
        uncommittedEvents.setFirstSequenceNumber(lastSequenceNumber + 1);
        lastCommitted = lastSequenceNumber >= 0 ? lastSequenceNumber : null;
    }

    /**
     * {@inheritDoc}
     */
    public Long getLastCommittedEventSequenceNumber() {
        return lastCommitted;
    }
}
