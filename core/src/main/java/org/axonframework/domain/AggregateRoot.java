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

package org.axonframework.domain;

/**
 * Interface defining a contract for entities that represent the aggregate root.
 *
 * @param <I> The type of the identifier of this aggregate
 * @author Allard Buijze
 * @see org.axonframework.domain.AbstractAggregateRoot
 * @since 0.1
 */
public interface AggregateRoot<I> {

    /**
     * Returns the identifier of this aggregate.
     *
     * @return the identifier of this aggregate
     */
    I getIdentifier();

    /**
     * Clears the events currently marked as "uncommitted" and clears any known EventRegistrationCallbacks (see {@link
     * #addEventRegistrationCallback(EventRegistrationCallback)}).
     *
     * @see org.axonframework.domain.EventContainer#commit()
     */
    void commitEvents();

    /**
     * Returns the number of uncommitted events currently available in the aggregate.
     *
     * @return the number of uncommitted events currently available in the aggregate.
     */
    int getUncommittedEventCount();

    /**
     * Returns a DomainEventStream to the events in the aggregate that have been raised since creation or the last
     * commit.
     *
     * @return the DomainEventStream to the uncommitted events.
     */
    DomainEventStream getUncommittedEvents();

    /**
     * Returns the current version number of the aggregate, or <code>null</code> if the aggregate is newly created.
     * This
     * version must reflect the version number of the aggregate on which changes are applied.
     * <p/>
     * Each time the aggregate is <em>modified and stored</em> in a repository, the version number must be increased by
     * at least 1. This version number can be used by optimistic locking strategies and detection of conflicting
     * concurrent modification.
     * <p/>
     * Typically the sequence number of the last committed event on this aggregate is used as version number.
     *
     * @return the current version number of this aggregate, or <code>null</code> if no events were ever committed
     */
    Long getVersion();

    /**
     * Indicates whether this aggregate has been marked as deleted. When <code>true</code>, it is an instruction to the
     * repository to remove this instance at an appropriate time.
     * <p/>
     * Repositories should not return any instances of Aggregates that return <code>true</code> on
     * <code>isDeleted()</code>.
     *
     * @return <code>true</code> if this aggregate was marked as deleted, otherwise <code>false</code>.
     */
    boolean isDeleted();

    /**
     * Adds an EventRegistrationCallback, which is notified when the aggregate registers an Event for publication.
     * These callbacks are cleared when the aggregate is committed.
     * <p/>
     * If the aggregate contains uncommitted events, they are all passed to the given
     * <code>eventRegistrationCallback</code> for processing.
     *
     * @param eventRegistrationCallback the callback to notify when an event is registered
     */
    void addEventRegistrationCallback(EventRegistrationCallback eventRegistrationCallback);
}
