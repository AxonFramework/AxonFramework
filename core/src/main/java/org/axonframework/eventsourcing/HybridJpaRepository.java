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

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.GenericJpaRepository;
import org.axonframework.repository.LockManager;
import org.axonframework.repository.NullLockManager;

/**
 * Repository that stores both a (JPA based) relational model of the current state of an aggregate and the events
 * produced by that aggregate. When an aggregate is loaded, only the relational model is used to reconstruct the
 * aggregate state.
 * <p/>
 * As events are not used for reconstructing the aggregate state, there is no need for snapshots or upcasters. In some
 * scenario's that could be a sensible choice.
 *
 * @param <T> The type of aggregate stored in this repository. Must implement {@link EventSourcedAggregateRoot}.
 * @author Allard Buijze
 * @since 1.0
 */
public class HybridJpaRepository<T extends AggregateRoot> extends GenericJpaRepository<T> {

    private EventStore eventStore;

    /**
     * Initializes a Hybrid Repository that stored entities of the given <code>aggregateType</code> and the locking
     * mechanism provided by the backend storage.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this repository
     * @param aggregateType         The type of aggregate stored in this repository.
     */
    public HybridJpaRepository(EntityManagerProvider entityManagerProvider,
                               Class<T> aggregateType) {
        this(entityManagerProvider, aggregateType, new NullLockManager());
    }

    /**
     * Initializes a Hybrid Repository that stored entities of the given <code>aggregateType</code> and a locking
     * mechanism based on the given <code>lockManager</code>.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this
     *                              repository
     * @param aggregateType         The type of aggregate stored in this repository.
     * @param lockManager           The locking strategy to use when loading and storing aggregates
     */
    public HybridJpaRepository(EntityManagerProvider entityManagerProvider, Class<T> aggregateType,
                               LockManager lockManager) {
        super(entityManagerProvider, aggregateType, lockManager);
    }

    @Override
    protected void doDeleteWithLock(T aggregate) {
        if (eventStore != null) {
            eventStore.appendEvents(aggregate.getUncommittedEvents());
        }
        super.doDeleteWithLock(aggregate);
    }

    @Override
    protected void doSaveWithLock(T aggregate) {
        if (eventStore != null) {
            eventStore.appendEvents(aggregate.getUncommittedEvents());
        }
        super.doSaveWithLock(aggregate);
    }

    /**
     * The event store to which events are appended. This event store is not used to load events, as the aggregate's
     * state is loaded from a relational model.
     * <p/>
     * If no event store is configured, events are not appended.
     *
     * @param eventStore The event store where events should be appended
     */
    public void setEventStore(EventStore eventStore) {
        this.eventStore = eventStore;
    }
}
