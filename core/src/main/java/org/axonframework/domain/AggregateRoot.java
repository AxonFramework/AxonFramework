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

import java.util.UUID;

/**
 * Interface defining a contract for entities that represent the aggregate root.
 *
 * @author Allard Buijze
 * @see org.axonframework.domain.AbstractAggregateRoot
 * @since 0.1
 */
public interface AggregateRoot {

    /**
     * Returns a DomainEventStream to the events in the aggregate that have been raised since creation or the last
     * commit.
     *
     * @return the DomainEventStream to the uncommitted events.
     */
    DomainEventStream getUncommittedEvents();

    /**
     * Returns the identifier of this aggregate
     *
     * @return the identifier of this aggregate
     */
    UUID getIdentifier();

    /**
     * Clears the events currently marked as "uncommitted".
     */
    void commitEvents();

    /**
     * Returns the number of uncommitted events currently available in the aggregate.
     *
     * @return the number of uncommitted events currently available in the aggregate.
     */
    int getUncommittedEventCount();
}
