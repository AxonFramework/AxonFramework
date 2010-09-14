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

/**
 * Base class for all Domain Events. This class contains the basic behavior expected from any event to be processed by
 * event sourcing engines and aggregates.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public abstract class DomainEvent extends EventBase {

    private volatile Long sequenceNumber;
    private volatile AggregateIdentifier aggregateIdentifier;

    /**
     * Initialize the domain event. Will set the current time stamp and generate a random event identifier. Use this
     * constructor when using the {@link org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot#apply(DomainEvent)}
     * method. The <code>sequenceNumber</code> and <code>aggregateIdentifier</code> are automatically set to the correct
     * values for that aggregate.
     * <p/>
     * If you do not use the {@link org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot#apply(DomainEvent)}
     * method, but need the <code>sequenceNumber</code> and <code>aggregateIdentifier</code> to be set to specific
     * values, use the {@link DomainEvent#DomainEvent(long, AggregateIdentifier)} constructor.
     */
    protected DomainEvent() {
        super();
    }

    /**
     * Initialize the domain event. Will set the current time stamp and generate a random event identifier. Use this
     * constructor when you need the <code>sequenceNumber</code> and <code>aggregateIdentifier</code> to be set to
     * specific values.
     * <p/>
     * Two use cases for this constructor are<ul><li>the generation of events when <em>not</em> using event sourcing
     * </li><li>the generation of snapshot events.</li></ul>
     * <p/>
     * When creating a DomainEvent using this constructor, all calls to {@link #setAggregateIdentifier(AggregateIdentifier)}
     * and {@link #setSequenceNumber(long)} will result in an exception.
     *
     * @param sequenceNumber      The sequence number to assign to this event
     * @param aggregateIdentifier The identifier of the aggregate this event applies to
     */
    protected DomainEvent(long sequenceNumber, AggregateIdentifier aggregateIdentifier) {
        super();
        this.sequenceNumber = sequenceNumber;
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Returns the sequence number of this event, if available. Will return null if this event has not been added to an
     * {@link org.axonframework.domain.EventContainer}.
     *
     * @return the sequence number of this event, or null if unknown.
     */
    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the identifier of the aggregate that reported this event.
     *
     * @return the identifier of the aggregate that reported this event
     */
    public AggregateIdentifier getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * Sets the sequence number of this event. May only be set once.
     *
     * @param sequenceNumber the sequence number to assign to this event
     * @throws IllegalStateException if a sequence number was already assigned
     */
    void setSequenceNumber(long sequenceNumber) {
        if (this.sequenceNumber != null) {
            throw new IllegalStateException("Sequence number may not be applied more than once.");
        }
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Sets the aggregate identifier. May only be set once.
     *
     * @param aggregateIdentifier the aggregate identifier
     * @throws IllegalStateException if an aggregate identifier was already assigned
     */
    void setAggregateIdentifier(AggregateIdentifier aggregateIdentifier) {
        if (this.aggregateIdentifier != null) {
            throw new IllegalStateException("An aggregateIdentifier can not be applied more than once.");
        }
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Checks for equality of two events. Two events are equal when they have the same type, aggregate identifier, time
     * stamp and sequence number. This allows to test for equality after one or more instances have been serialized and
     * deserialized.
     *
     * @param o the other DomainEvent
     * @return true when equals, otherwise false
     */
    @SuppressWarnings({"RedundantIfStatement"})
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DomainEvent that = (DomainEvent) o;

        if (aggregateIdentifier != null
                ? !aggregateIdentifier.equals(that.aggregateIdentifier)
                : that.aggregateIdentifier != null) {
            return false;
        }
        if (sequenceNumber != null ? !sequenceNumber.equals(that.sequenceNumber) : that.sequenceNumber != null) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return getEventIdentifier().hashCode();
    }
}
