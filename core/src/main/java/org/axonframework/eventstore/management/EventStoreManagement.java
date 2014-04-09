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

package org.axonframework.eventstore.management;

import org.axonframework.eventstore.EventVisitor;

/**
 * Interface describing operations useful for management purposes. These operations are typically used in migration
 * scripts when deploying new versions of applications.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface EventStoreManagement {

    /**
     * Loads all events available in the event store and calls
     * {@link org.axonframework.eventstore.EventVisitor#doWithEvent(org.axonframework.domain.DomainEventMessage)}
     * for each event found. Events of a single aggregate are guaranteed to be ordered by their sequence number.
     * <p/>
     * Implementations are encouraged, though not required, to supply events in the absolute chronological order.
     * <p/>
     * Processing stops when the visitor throws an exception.
     *
     * @param visitor The visitor the receives each loaded event
     */
    void visitEvents(EventVisitor visitor);

    /**
     * Loads all events available in the event store that match the given <code>criteria</code> and calls {@link
     * EventVisitor#doWithEvent(org.axonframework.domain.DomainEventMessage)} for each event found. Events of a single
     * aggregate are guaranteed to be ordered by their sequence number.
     * <p/>
     * Implementations are encouraged, though not required, to supply events in the absolute chronological order.
     * <p/>
     * Processing stops when the visitor throws an exception.
     *
     * @param criteria The criteria describing the events to select
     * @param visitor  The visitor the receives each loaded event
     * @see #newCriteriaBuilder()
     */
    void visitEvents(Criteria criteria, EventVisitor visitor);

    /**
     * Returns a CriteriaBuilder that allows the construction of criteria for this EventStore implementation
     *
     * @return a builder to create Criteria for this Event Store.
     *
     * @see #visitEvents(Criteria, org.axonframework.eventstore.EventVisitor)
     */
    CriteriaBuilder newCriteriaBuilder();
}
