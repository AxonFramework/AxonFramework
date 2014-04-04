/*
 * Copyright (c) 2010-2012. Axon Framework
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

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.persistence.Version;

/**
 * Very basic implementation of the AggregateRoot interface. It provides the mechanism to keep track of uncommitted
 * events and maintains a version number based on the number of events generated.
 *
 * @param <I> The type of the identifier of this aggregate
 * @author Allard Buijze
 * @since 0.6
 */
@MappedSuperclass
public abstract class AbstractAggregateRoot<I> implements AggregateRoot<I>, Serializable {

    private static final long serialVersionUID = 6330592271927197888L;

    @Transient
    private transient volatile EventContainer eventContainer;

    @Transient
    private transient boolean deleted = false;

    @Basic(optional = true)
    private Long lastEventSequenceNumber;

    @SuppressWarnings({"UnusedDeclaration"})
    @Version
    private Long version;

    /**
     * Registers an event to be published when the aggregate is saved, containing the given <code>payload</code> and no
     * (additional) meta-data.
     *
     * @param payload the payload of the event to register
     * @param <T>     The type of payload
     * @return The Event holding the given <code>payload</code>
     */
    protected <T> DomainEventMessage<T> registerEvent(T payload) {
        return registerEvent(MetaData.emptyInstance(), payload);
    }

    /**
     * Registers an event to be published when the aggregate is saved.
     *
     * @param metaData The meta data of the event to register
     * @param payload  the payload of the event to register
     * @param <T>      The type of payload
     * @return The Event holding the given <code>payload</code>
     */
    protected <T> DomainEventMessage<T> registerEvent(MetaData metaData, T payload) {
        return getEventContainer().addEvent(metaData, payload);
    }

    /**
     * Marks this aggregate as deleted, instructing a Repository to remove that aggregate at an appropriate time.
     * <p/>
     * Note that different Repository implementation may react differently to aggregates marked for deletion.
     * Typically,
     * Event Sourced Repositories will ignore the marking and expect deletion to be provided as part of Event
     * information.
     */
    protected void markDeleted() {
        this.deleted = true;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public void addEventRegistrationCallback(EventRegistrationCallback eventRegistrationCallback) {
        getEventContainer().addEventRegistrationCallback(eventRegistrationCallback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DomainEventStream getUncommittedEvents() {
        if (eventContainer == null) {
            return SimpleDomainEventStream.emptyStream();
        }
        return eventContainer.getEventStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commitEvents() {
        if (eventContainer != null) {
            lastEventSequenceNumber = eventContainer.getLastSequenceNumber();
            eventContainer.commit();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUncommittedEventCount() {
        return eventContainer != null ? eventContainer.size() : 0;
    }

    /**
     * Initialize the event stream using the given sequence number of the last known event. This will cause the new
     * events to be attached to this aggregate to be assigned a continuous sequence number.
     *
     * @param lastSequenceNumber The sequence number of the last event from this aggregate
     */
    protected void initializeEventStream(long lastSequenceNumber) {
        getEventContainer().initializeSequenceNumber(lastSequenceNumber);
        lastEventSequenceNumber = lastSequenceNumber >= 0 ? lastSequenceNumber : null;
    }

    /**
     * Returns the sequence number of the last committed event, or <code>null</code> if no events have been committed
     * before.
     *
     * @return the sequence number of the last committed event
     */
    protected Long getLastCommittedEventSequenceNumber() {
        if (eventContainer == null) {
            return lastEventSequenceNumber;
        }
        return eventContainer.getLastCommittedSequenceNumber();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getVersion() {
        return version;
    }

    private EventContainer getEventContainer() {
        if (eventContainer == null) {
            Object identifier = getIdentifier();
            if (identifier == null) {
                throw new AggregateIdentifierNotInitializedException(
                        "AggregateIdentifier is unknown in [" + getClass().getName() + "]. "
                                + "Make sure the Aggregate Identifier is initialized before registering events.");
            }
            eventContainer = new EventContainer(identifier);
            eventContainer.initializeSequenceNumber(lastEventSequenceNumber);
        }
        return eventContainer;
    }
}
