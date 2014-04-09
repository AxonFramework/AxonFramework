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

package org.axonframework.eventsourcing;

import org.axonframework.common.Assert;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.MetaData;

import java.util.Collection;

/**
 * Base class for Event Sourced entities that are not at the root of the aggregate. Instead of keeping track of
 * uncommitted events themselves, these entities refer to their aggregate root to do that for them. A DomainEvent
 * published in any of the entities in an Aggregate is published to all entities in the entire aggregate.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractEventSourcedEntity implements EventSourcedEntity {

    private volatile AbstractEventSourcedAggregateRoot aggregateRoot;

    @Override
    public void registerAggregateRoot(AbstractEventSourcedAggregateRoot aggregateRootToRegister) {
        if (this.aggregateRoot != null && this.aggregateRoot != aggregateRootToRegister) {
            throw new IllegalStateException("Cannot register new aggregate. "
                                                    + "This entity is already part of another aggregate");
        }
        this.aggregateRoot = aggregateRootToRegister;
    }

    @Override
    public void handleRecursively(DomainEventMessage event) {
        handle(event);
        Collection<? extends EventSourcedEntity> childEntities = getChildEntities();
        if (childEntities != null) {
            for (EventSourcedEntity entity : childEntities) {
                if (entity != null) {
                    entity.registerAggregateRoot(aggregateRoot);
                    entity.handleRecursively(event);
                }
            }
        }
    }

    /**
     * Returns a collection of event sourced entities directly referenced by this entity. May return null or an empty
     * list to indicate no child entities are available. The collection may also contain null values.
     *
     * @return a list of event sourced entities contained in this aggregate
     */
    protected abstract Collection<? extends EventSourcedEntity> getChildEntities();

    /**
     * Apply state changes based on the given event.
     * <p/>
     * Note: Implementations of this method should *not* perform validation.
     *
     * @param event The event to handle
     */
    protected abstract void handle(DomainEventMessage event);

    /**
     * Apply the provided event. Applying events means they are added to the uncommitted event queue and forwarded to
     * the {@link #handle(org.axonframework.domain.DomainEventMessage)} event handler method} for processing.
     * <p/>
     * The event is applied on all entities part of this aggregate.
     *
     * @param event The payload of the event to apply
     */
    protected void apply(Object event) {
        apply(event, MetaData.emptyInstance());
    }

    /**
     * Apply the provided event and attaching the given <code>metaData</code>. Applying events means they are added to
     * the uncommitted event queue and forwarded to the {@link #handle(org.axonframework.domain.DomainEventMessage)}
     * event handler method} for processing.
     * <p/>
     * The event is applied on all entities part of this aggregate.
     *
     * @param event    The payload of the event to apply
     * @param metaData any meta-data that must be registered with the Event
     */
    protected void apply(Object event, MetaData metaData) {
        Assert.notNull(aggregateRoot, "The aggregate root is unknown. "
                + "Is this entity properly registered as the child of an aggregate member?");
        aggregateRoot.apply(event, metaData);
    }

    /**
     * Returns the reference to the root of the aggregate this entity is a member of.
     *
     * @return the reference to the root of the aggregate this entity is a member of
     */
    protected AbstractEventSourcedAggregateRoot getAggregateRoot() {
        return aggregateRoot;
    }
}
