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
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.GenericJpaRepository;
import org.axonframework.repository.LockManager;
import org.axonframework.repository.NullLockManager;

import javax.persistence.EntityManager;

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
    private final String aggregateTypeIdentifier;

    /**
     * Initializes a Hybrid Repository that stored entities of the given <code>aggregateType</code> and the locking
     * mechanism provided by the backend storage.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this repository
     * @param aggregateType         The type of aggregate stored in this repository.
     */
    public HybridJpaRepository(EntityManagerProvider entityManagerProvider,
                               Class<T> aggregateType) {
        this(entityManagerProvider, aggregateType, aggregateType.getSimpleName());
    }

    /**
     * Initializes a Hybrid Repository that stored entities of the given <code>aggregateType</code> without locking.
     * The events are appended to the event store under the given <code>aggregateTypeIdentifier</code>.
     *
     * @param entityManagerProvider   The EntityManagerProvider providing the EntityManager instance for this
     *                                repository
     * @param aggregateType           The type of aggregate stored in this repository.
     * @param aggregateTypeIdentifier The type identifier to store events with
     */
    public HybridJpaRepository(EntityManagerProvider entityManagerProvider,
                               Class<T> aggregateType, String aggregateTypeIdentifier) {
        this(entityManagerProvider, aggregateType, aggregateTypeIdentifier, new NullLockManager());
    }

    /**
     * Initializes a Hybrid Repository that stored entities of the given <code>aggregateType</code> and a locking
     * mechanism based on the given <code>lockManager</code>.
     *
     * @param entityManagerProvider The EntityManagerProvider providing the EntityManager instance for this repository
     * @param aggregateType         The type of aggregate stored in this repository.
     * @param lockManager       The locking strategy to use when loading and storing aggregates
     */
    public HybridJpaRepository(EntityManagerProvider entityManagerProvider,
                               Class<T> aggregateType, LockManager lockManager) {
        this(entityManagerProvider, aggregateType, aggregateType.getSimpleName(), lockManager);
    }

    /**
     * Initializes a Hybrid Repository that stored entities of the given <code>aggregateType</code> and a locking
     * mechanism based on the given <code>lockManager</code>.
     *
     * @param entityManager The EntityManager instance for this repository
     * @param aggregateType         The type of aggregate stored in this repository.
     * @param lockManager       The locking strategy to use when loading and storing aggregates
     */
    public HybridJpaRepository(EntityManager entityManager,
                               Class<T> aggregateType, LockManager lockManager) {
        this(new SimpleEntityManagerProvider(entityManager), aggregateType, aggregateType.getSimpleName(), lockManager);
    }

    /**
     * Initializes a Hybrid Repository that stored entities of the given <code>aggregateType</code> and a locking
     * mechanism based on the given <code>lockManager</code>.
     *
     * @param entityManagerProvider   The EntityManagerProvider providing the EntityManager instance for this
     *                                repository
     * @param aggregateType           The type of aggregate stored in this repository.
     * @param aggregateTypeIdentifier The type identifier to store events with
     * @param lockManager         The locking strategy to use when loading and storing aggregates
     */
    public HybridJpaRepository(EntityManagerProvider entityManagerProvider,
                               Class<T> aggregateType, String aggregateTypeIdentifier,
                               LockManager lockManager) {
        super(entityManagerProvider, aggregateType, lockManager);
        this.aggregateTypeIdentifier = aggregateTypeIdentifier;
    }


    /**
     * Initializes a Hybrid Repository that stored entities of the given <code>aggregateType</code> and a locking
     * mechanism based on the given <code>lockManager</code>.
     *
     * @param entityManager   The EntityManager instance for this repository
     * @param aggregateType           The type of aggregate stored in this repository.
     * @param aggregateTypeIdentifier The type identifier to store events with
     * @param lockManager         The locking strategy to use when loading and storing aggregates
     */
    public HybridJpaRepository(EntityManager entityManager,
                               Class<T> aggregateType, String aggregateTypeIdentifier,
                               LockManager lockManager) {
        super(new SimpleEntityManagerProvider(entityManager), aggregateType, lockManager);
        this.aggregateTypeIdentifier = aggregateTypeIdentifier;
    }

    @Override
    protected void doDeleteWithLock(T aggregate) {
        if (eventStore != null) {
            eventStore.appendEvents(getTypeIdentifier(), aggregate.getUncommittedEvents());
        }
        super.doDeleteWithLock(aggregate);
    }

    @Override
    protected void doSaveWithLock(T aggregate) {
        if (eventStore != null) {
            eventStore.appendEvents(getTypeIdentifier(), aggregate.getUncommittedEvents());
        }
        super.doSaveWithLock(aggregate);
    }

    /**
     * Returns the type identifier to use when appending events in the event store. Default to the simple class name of
     * the aggregateType provided in the constructor.
     *
     * @return the type identifier to use when appending events in the event store.
     */
    protected String getTypeIdentifier() {
        return aggregateTypeIdentifier;
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