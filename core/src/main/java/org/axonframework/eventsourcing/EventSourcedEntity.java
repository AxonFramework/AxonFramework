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

import org.axonframework.domain.DomainEventMessage;

/**
 * Interface towards an Event Sourced Entity that is part of an aggregate, but not its root. Events applied to the
 * aggregate can be propagated to this entity, provided it is properly exposed as a descendant (direct or indirect
 * child) of the aggregate root.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventsourcing.annotation.EventSourcedMember
 * @see org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot#getChildEntities()
 * @since 2.0
 */
public interface EventSourcedEntity<T extends AbstractEventSourcedAggregateRoot>{

    /**
     * Register the aggregate root with this entity. The entity must use this aggregate root to apply Domain Events.
     * The aggregate root is responsible for tracking all applied events.
     * <p/>
     * A parent entity is responsible for invoking this method on its child entities prior to propagating events to it.
     * Typically, this means all entities have their aggregate root set before any actions are taken on it.
     *
     * @param aggregateRootToRegister the root of the aggregate this entity is part of.
     */
    void registerAggregateRoot(T aggregateRootToRegister);

    /**
     * Report the given <code>event</code> for handling in the current instance (<code>this</code>), as well as all the
     * entities referenced by this instance.
     *
     * @param event The event to handle
     */
    void handleRecursively(DomainEventMessage event);
}
