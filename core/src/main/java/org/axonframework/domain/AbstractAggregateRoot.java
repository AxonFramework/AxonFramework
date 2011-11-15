/*
 * Copyright (c) 2010-2011. Axon Framework
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
import java.util.Collection;
import javax.persistence.Basic;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import javax.persistence.Version;

/**
 * Very basic implementation of the AggregateRoot interface. It provides the mechanism to keep track of uncommitted
 * events and maintains a version number based on the number of events generated.
 *
 * @author Allard Buijze
 * @since 0.6
 */
@MappedSuperclass
public abstract class AbstractAggregateRoot implements AggregateRoot, Serializable {

    private static final long serialVersionUID = 6330592271927197888L;
    private static final IdentifierFactory IDENTIFIER_FACTORY = IdentifierFactory.getInstance();

    @Transient
    private EventContainer eventContainer;

    @Transient
    private boolean deleted = false;

    @Id
    private String id;

    @Basic(optional = true)
    private Long lastEventSequenceNumber;

    @SuppressWarnings({"UnusedDeclaration"})
    @Version
    private Long version;

    /**
     * Initializes the aggregate root using a random aggregate identifier.
     */
    protected AbstractAggregateRoot() {
        this(new StringAggregateIdentifier(IDENTIFIER_FACTORY.generateIdentifier()));
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
        this.id = identifier.asString();
        eventContainer = new EventContainer(identifier);
    }

    /**
     * Registers an event to be published when the aggregate is saved, containing the given <code>payload</code> and no
     * (additional) meta-data.
     *
     * @param payload the payload of the event to register
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
     * @return The Event holding the given <code>payload</code>
     */
    protected <T> DomainEventMessage<T> registerEvent(MetaData metaData, T payload) {
        return eventContainer.addEvent(metaData, payload);
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
    public void registerEventRegistrationCallback(EventRegistrationCallback eventRegistrationCallback) {
        this.eventContainer.registerEventRegistrationCallback(eventRegistrationCallback);
    }

    /**
     * Shorthand helper method to easily return the aggregate identifier as a String value. To get the actual
     * identifier, use {@link #getIdentifier()}.
     *
     * @return the String representation of this aggregate's identifier
     */
    protected String id() {
        return getIdentifier().asString();
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
     * <p/>
     * Callers should not expect the exact same instance, nor an instance of the same class as provided in the
     * constructor. When this aggregate has been serialized or persisted using JPA, the identifier returned here is an
     * instance of {@link StringAggregateIdentifier}.
     */
    @Override
    public AggregateIdentifier getIdentifier() {
        return eventContainer.getAggregateIdentifier();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commitEvents() {
        lastEventSequenceNumber = eventContainer.getLastSequenceNumber();
        eventContainer.commit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUncommittedEventCount() {
        return eventContainer != null ? eventContainer.size() : 0;
    }

    /**
     * Appends all the uncommitted events to the given <code>collection</code>. This method is a high-performance
     * alternative to {@link #getUncommittedEvents()}.
     *
     * @param collection the collection to append uncommitted events to
     */
    public void appendUncommittedEventsTo(Collection<DomainEventMessage> collection) {
        collection.addAll(eventContainer.getEventList());
    }

    /**
     * Initialize the event stream using the given sequence number of the last known event. This will cause the new
     * events to be attached to this aggregate to be assigned a continuous sequence number.
     *
     * @param lastSequenceNumber The sequence number of the last event from this aggregate
     */
    protected void initializeEventStream(long lastSequenceNumber) {
        eventContainer.initializeSequenceNumber(lastSequenceNumber);
        lastEventSequenceNumber = lastSequenceNumber >= 0 ? lastSequenceNumber : null;
    }

    /**
     * Returns the sequence number of the last committed event, or <code>null</code> if no events have been committed
     * before.
     *
     * @return the sequence number of the last committed event
     */
    protected Long getLastCommittedEventSequenceNumber() {
        return eventContainer.getLastCommittedSequenceNumber();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getVersion() {
        return version;
    }

    /**
     * JPA / EJB3 @PostLoad annotated method used to initialize the fields in this class after an instance has been
     * loaded from persistent storage.
     * <p/>
     * Subclasses are responsible for invoking this method if they provide their own {@link @PostLoad} annotated
     * method.
     * Failure to do so will inevitably result in <code>NullPointerException</code>.
     *
     * @see PostLoad
     */
    @PostLoad
    protected void performPostLoadInitialization() {
        eventContainer = new EventContainer(new StringAggregateIdentifier(id));
        eventContainer.initializeSequenceNumber(lastEventSequenceNumber);
    }
}
