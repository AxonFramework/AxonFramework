/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.repository;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateRoot;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * Generic repository implementation that stores JPA annotated aggregates. These aggregates must implement {@link
 * org.axonframework.domain.AggregateRoot} and have the proper JPA Annotations.
 * <p/>
 * Optionally, the repository may be configured with a locking scheme. The repository will always force optimistic
 * locking in the backing data store. The optional lock in the repository is in addition to this optimistic lock. Note
 * that locks on this repository will not be shared with other repository instances.
 * <p/>
 * When this repository is requested to persist changes to an aggregate, it will also flush the EntityManager, to
 * enforce checking of database constraints and optimistic locks.
 *
 * @param <T> The type of aggregate the repository provides access to
 * @author Allard Buijze
 * @since 0.7
 */
public class GenericJpaRepository<T extends AggregateRoot> extends LockingRepository<T> {

    private EntityManager entityManager;
    private final Class<T> aggregateType;
    private boolean forceFlushOnSave = true;

    /**
     * Initialize a repository for storing aggregates of the given <code>aggregateType</code>. No additional locking
     * will be used.
     *
     * @param aggregateType the aggregate type this repository manages
     */
    public GenericJpaRepository(Class<T> aggregateType) {
        this(aggregateType, LockingStrategy.NO_LOCKING);
    }

    /**
     * Initialize a repository  for storing aggregates of the given <code>aggregateType</code> with an additional <code>
     * lockingStrategy</code>.
     *
     * @param aggregateType   the aggregate type this repository manages
     * @param lockingStrategy the additional locking strategy for this repository
     */
    public GenericJpaRepository(Class<T> aggregateType, LockingStrategy lockingStrategy) {
        super(lockingStrategy);
        this.aggregateType = aggregateType;
    }

    @Override
    protected void doSaveWithLock(T aggregate) {
        entityManager.persist(aggregate);
        if (forceFlushOnSave) {
            entityManager.flush();
        }
    }

    /**
     * Returns the aggregate type stored by this repository.
     *
     * @return the aggregate type stored by this repository
     */
    protected Class<T> getAggregateType() {
        return aggregateType;
    }

    @Override
    protected T doLoad(AggregateIdentifier aggregateIdentifier, Long expectedVersion) {
        return entityManager.find(aggregateType, aggregateIdentifier.asString());
    }

    /**
     * Sets the EntityManager this repository should use to access the underlying storage.
     *
     * @param entityManager the EntityManager providing access to the underlying storage.
     */
    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Indicates whether the EntityManager's state should be flushed each time an aggregate is saved. Defaults to
     * <code>true</code>.
     * <p/>
     * Flushing the EntityManager will force JPA to send state changes to the database. Any key violations and failing
     * optimistic locks will be identified in an early stage.
     *
     * @param forceFlushOnSave whether or not to flush the EntityManager after each save. Defaults to
     *                         <code>true</code>.
     * @see javax.persistence.EntityManager#flush()
     */
    public void setForceFlushOnSave(boolean forceFlushOnSave) {
        this.forceFlushOnSave = forceFlushOnSave;
    }
}
