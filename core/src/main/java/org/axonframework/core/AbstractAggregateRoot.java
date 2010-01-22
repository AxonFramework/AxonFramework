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

    protected AbstractAggregateRoot() {
        this(UUID.randomUUID());
    }

    protected AbstractAggregateRoot(UUID identifier) {
        this.identifier = identifier;
        uncommittedEvents = new EventContainer(identifier);
    }

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
