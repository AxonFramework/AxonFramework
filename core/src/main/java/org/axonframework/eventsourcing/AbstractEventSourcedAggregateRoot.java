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

package org.axonframework.eventsourcing;

import org.axonframework.common.Assert;
import org.axonframework.domain.AbstractAggregateRoot;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;

import javax.persistence.MappedSuperclass;

/**
 * Abstract convenience class to be extended by all aggregate roots. The AbstractEventSourcedAggregateRoot tracks all
 * uncommitted events. It also provides convenience methods to initialize the state of the aggregate root based on a
 * {@link org.axonframework.domain.DomainEventStream}, which can be used for event sourcing.
 *
 * @param <I> The type of the identifier of this aggregate
 * @author Allard Buijze
 * @since 0.1
 */
@MappedSuperclass
public abstract class AbstractEventSourcedAggregateRoot<I> extends AbstractAggregateRoot<I>
        implements EventSourcedAggregateRoot<I> {

    private static final long serialVersionUID = 5868786029296883724L;

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation is aware of a special type of <code>DomainEvents</code>: the <code>AggregateSnapshot</code>,
     * which is a snapshot event, containing the actual aggregate inside.
     * <p/>
     * <code>AggregateSnapshot</code> events are used to initialize the aggregate with the correct version ({@link
     * #getVersion()}).
     *
     * @throws IllegalStateException if this aggregate was already initialized.
     */
    @Override
    public void initializeState(DomainEventStream domainEventStream) {
        Assert.state(getUncommittedEventCount() == 0, "Aggregate is already initialized");
        long lastSequenceNumber = -1;
        while (domainEventStream.hasNext()) {
            DomainEventMessage event = domainEventStream.next();
            lastSequenceNumber = event.getSequenceNumber();
            handleRecursively(event);
        }
        initializeEventStream(lastSequenceNumber);
    }

    /**
     * Apply the provided event. Applying events means they are added to the uncommitted event queue and forwarded to
     * the {@link #handle(org.axonframework.domain.DomainEventMessage)} event handler method} for processing.
     * <p/>
     * The event is applied on all entities part of this aggregate.
     *
     * @param eventPayload The payload of the event to apply
     */
    protected void apply(Object eventPayload) {
        apply(eventPayload, MetaData.emptyInstance());
    }

    /**
     * Apply the provided event. Applying events means they are added to the uncommitted event queue and forwarded to
     * the {@link #handle(org.axonframework.domain.DomainEventMessage)} event handler method} for processing.
     * <p/>
     * The event is applied on all entities part of this aggregate.
     *
     * @param eventPayload The payload of the event to apply
     * @param metaData     any meta-data that must be registered with the Event
     */
    protected void apply(Object eventPayload, MetaData metaData) {
        if (getIdentifier() == null) {
            // workaround for aggregates that set the aggregate identifier in an Event Handler
            if (getUncommittedEventCount() > 0 || getVersion() != null) {
                throw new IncompatibleAggregateException("The Aggregate Identifier has not been initialized. "
                                                                 + "It must be initialized at the latest when the "
                                                                 + "first event is applied.");
            }
            handleRecursively(new GenericDomainEventMessage<Object>(null, 0, eventPayload, metaData));
            registerEvent(metaData, eventPayload);
        } else {
            DomainEventMessage event = registerEvent(metaData, eventPayload);
            handleRecursively(event);
        }
    }

    private void handleRecursively(DomainEventMessage event) {
        handle(event);
        Iterable<? extends EventSourcedEntity> childEntities = getChildEntities();
        if (childEntities != null) {
            for (EventSourcedEntity entity : childEntities) {
                if (entity != null) {
                    entity.registerAggregateRoot(this);
                    entity.handleRecursively(event);
                }
            }
        }
    }

    /**
     * Returns a collection of event sourced entities directly referenced by this entity. May return null or an empty
     * list to indicate no child entities are available. The collection may also contain null values.
     * <p/>
     * Events are propagated to the children in the order that the iterator of the return value provides.
     *
     * @return a list of event sourced entities contained in this aggregate
     */
    protected abstract Iterable<? extends EventSourcedEntity> getChildEntities();

    /**
     * Apply state changes based on the given event.
     * <p/>
     * Note: Implementations of this method should *not* perform validation.
     *
     * @param event The event to handle
     */
    protected abstract void handle(DomainEventMessage event);

    @Override
    public Long getVersion() {
        return getLastCommittedEventSequenceNumber();
    }
}
