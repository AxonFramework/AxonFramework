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

package org.axonframework.domain;

import javax.persistence.Basic;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PostLoad;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;
import javax.persistence.Version;

/**
 * Implementation of the AggregateRoot interface that allows an aggregate to be stored by a JPA EntityManager. It
 * provides the mechanism to keep track of uncommitted events and maintains a version number based on the number of
 * events generated.
 * <p/>
 * The version number is automatically increased by 1 each time an aggregate is stored. Note that since there is no
 * guarantee that a relationally stored aggregate has an event for each state change, the version of this type of
 * aggregate does not have to be equal to the sequence number of the last Domain Event.
 * <p/>
 * This entity stores the {@link #getIdentifier() Aggregate Identifier} as a String and the {@link
 * #getLastEventSequenceNumber() last event sequence number} and {@link #getVersion() version} as a Long. The Aggregate
 * Identifier is assigned as the primary key.
 *
 * @author Allard Buijze
 * @since 0.6
 */
@MappedSuperclass
public abstract class AbstractJpaAggregateRoot implements AggregateRoot {

    @Transient
    private EventContainer uncommittedEvents;

    @Transient
    private AggregateIdentifier identifier;

    @Id
    private final String aggregateId;

    @Basic(optional = true)
    private volatile Long lastEventSequenceNumber;

    @SuppressWarnings({"UnusedDeclaration"})
    @Version
    private volatile Long version;

    /**
     * Initializes the aggregate root using a random aggregate identifier.
     */
    protected AbstractJpaAggregateRoot() {
        this(new UUIDAggregateIdentifier());
    }

    /**
     * Initializes the aggregate root using the provided aggregate identifier.
     *
     * @param identifier the identifier of this aggregate
     */
    protected AbstractJpaAggregateRoot(AggregateIdentifier identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("Aggregate identifier may not be null.");
        }
        this.aggregateId = identifier.asString();
        initializeEventContainer();
    }

    /**
     * Initializes the EventContainer, which keeps track of uncommitted events in this aggregate.
     * <p/>
     * This method is annotated with @PostLoad and is expected to be invoked by the persistence framework after it has
     * been loaded.
     */
    @PostLoad
    protected void initializeEventContainer() {
        identifier = new StringAggregateIdentifier(aggregateId);
        uncommittedEvents = new EventContainer(identifier);
        uncommittedEvents.initializeSequenceNumber(lastEventSequenceNumber);
    }

    /**
     * Updates the last event sequence number (see {@link #getLastEventSequenceNumber()} to the sequence number of the
     * last uncommitted event.
     * <p/>
     * This method is annotated with @PreUpdate and @PrePersist and is expected to be called by the persistence
     * framework prior to Update or Persist.
     */
    @PreUpdate
    @PrePersist
    protected void updateLastEventSequenceNumber() {
        lastEventSequenceNumber = uncommittedEvents.getLastSequenceNumber();
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
        lastEventSequenceNumber = uncommittedEvents.getLastSequenceNumber();
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
     * Returns the sequence number of the last event registered by this Aggregate.
     *
     * @return the sequence number of the last event registered by this Aggregate.
     */
    public Long getLastEventSequenceNumber() {
        return lastEventSequenceNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getVersion() {
        return version;
    }
}
