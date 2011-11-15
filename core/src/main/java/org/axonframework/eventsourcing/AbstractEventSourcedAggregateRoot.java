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

package org.axonframework.eventsourcing;

import org.axonframework.common.Assert;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.domain.AbstractAggregateRoot;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.MetaData;

import java.util.Collection;
import javax.persistence.MappedSuperclass;

/**
 * Abstract convenience class to be extended by all aggregate roots. The AbstractEventSourcedAggregateRoot tracks all
 * uncommitted events. It also provides convenience methods to initialize the state of the aggregate root based on a
 * {@link org.axonframework.domain.DomainEventStream}, which can be used for event sourcing.
 *
 * @author Allard Buijze
 * @since 0.1
 */
@MappedSuperclass
public abstract class AbstractEventSourcedAggregateRoot extends AbstractAggregateRoot
        implements EventSourcedAggregateRoot {

    private static final long serialVersionUID = 5868786029296883724L;

    /**
     * Initializes the aggregate root using a random aggregate identifier.
     */
    protected AbstractEventSourcedAggregateRoot() {
        super();
    }

    /**
     * Initializes the aggregate root using the provided aggregate identifier.
     *
     * @param identifier the identifier of this aggregate
     */
    protected AbstractEventSourcedAggregateRoot(AggregateIdentifier identifier) {
        super(identifier);
    }

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
            if (!(event instanceof AggregateSnapshot)) {
                handleRecursively(event);
            }
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
        DomainEventMessage event = registerEvent(metaData, eventPayload);
        handleRecursively(event);
    }

    private void handleRecursively(DomainEventMessage event) {
        handle(event);
        Collection<AbstractEventSourcedEntity> childEntities = getChildEntities();
        if (childEntities != null) {
            for (AbstractEventSourcedEntity entity : childEntities) {
                entity.registerAggregateRoot(this);
                entity.handleRecursively(event);
            }
        }
    }

    /**
     * Returns a list of event sourced entities directly referenced by the aggregate root.
     * <p/>
     * The default implementation uses reflection to find references to {@link AbstractEventSourcedEntity}
     * implementations.
     * <p/>
     * It will look for entities: <ul><li> directly referenced in a field;<li> inside fields containing an {@link
     * Iterable};<li>inside both they keys and the values of fields containing a {@link java.util.Map}</ul>
     * <p/>
     * This method may be overridden by subclasses. A <code>null</code> may be returned if this entity does not have
     * any
     * child entities.
     *
     * @return a list of event sourced entities contained in this aggregate
     */
    protected Collection<AbstractEventSourcedEntity> getChildEntities() {
        return ReflectionUtils.findFieldValuesOfType(this, AbstractEventSourcedEntity.class);
    }

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
