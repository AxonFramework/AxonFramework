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

package org.axonframework.repository;

import org.axonframework.common.Assert;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.SaveAggregateCallback;

/**
 * Abstract implementation of the {@link Repository} that takes care of the dispatching of events when an aggregate is
 * persisted. All uncommitted events on an aggregate are dispatched when the aggregate is saved.
 * <p/>
 * Note that this repository implementation does not take care of any locking. The underlying persistence is expected
 * to
 * deal with concurrency. Alternatively, consider using the {@link LockingRepository}.
 *
 * @param <T> The type of aggregate this repository stores
 * @author Allard Buijze
 * @see #setEventBus(org.axonframework.eventhandling.EventBus)
 * @see LockingRepository
 * @since 0.1
 */
public abstract class AbstractRepository<T extends AggregateRoot> implements Repository<T> {

    private final Class<T> aggregateType;
    private final SimpleSaveAggregateCallback saveAggregateCallback = new SimpleSaveAggregateCallback();
    private EventBus eventBus;

    /**
     * Initializes a repository that stores aggregate of the given <code>aggregateType</code>. All aggregates in this
     * repository must be <code>instanceOf</code> this aggregate type.
     *
     * @param aggregateType The type of aggregate stored in this repository
     */
    protected AbstractRepository(Class<T> aggregateType) {
        Assert.notNull(aggregateType, "aggregateType may not be null");
        this.aggregateType = aggregateType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(T aggregate) {
        Assert.isTrue(aggregateType.isInstance(aggregate), "Unsuitable aggregate for this repository: wrong type");
        if (aggregate.getVersion() != null) {
            throw new IllegalArgumentException("Only newly created (unpersisted) aggregates may be added.");
        }
        CurrentUnitOfWork.get().registerAggregate(aggregate, eventBus, saveAggregateCallback);
    }

    /**
     * {@inheritDoc}
     *
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     * @throws RuntimeException           any exception thrown by implementing classes
     */
    @Override
    public T load(String aggregateIdentifier, Long expectedVersion) {
        T aggregate = doLoad(aggregateIdentifier, expectedVersion);
        validateOnLoad(aggregate, expectedVersion);
        return CurrentUnitOfWork.get().registerAggregate(aggregate, eventBus, saveAggregateCallback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T load(String aggregateIdentifier) {
        return load(aggregateIdentifier, null);
    }

    /**
     * Checks the aggregate for concurrent changes. Throws a
     * {@link org.axonframework.repository.ConflictingModificationException} when conflicting changes have been
     * detected.
     * <p/>
     * This implementation throws a {@link ConflictingAggregateVersionException} if the expected version is not null
     * and the version number of the aggregate does not match the expected version
     *
     * @param aggregate       The loaded aggregate
     * @param expectedVersion The expected version of the aggregate
     * @throws ConflictingModificationException
     *
     * @throws ConflictingAggregateVersionException
     *
     */
    protected void validateOnLoad(T aggregate, Long expectedVersion) {
        if (expectedVersion != null && aggregate.getVersion() != null &&
                !expectedVersion.equals(aggregate.getVersion())) {
            throw new ConflictingAggregateVersionException(aggregate.getIdentifier(),
                                                           expectedVersion,
                                                           aggregate.getVersion());
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

    /**
     * Performs the actual saving of the aggregate.
     *
     * @param aggregate the aggregate to store
     */
    protected abstract void doSave(T aggregate);

    /**
     * Loads and initialized the aggregate with the given aggregateIdentifier.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param expectedVersion     The expected version of the aggregate to load
     * @return a fully initialized aggregate
     *
     * @throws AggregateNotFoundException if the aggregate with given identifier does not exist
     */
    protected abstract T doLoad(String aggregateIdentifier, Long expectedVersion);

    /**
     * Removes the aggregate from the repository. Typically, the repository should ensure that any calls to {@link
     * #doLoad(String, Long)} throw a {@link AggregateNotFoundException} when
     * loading a deleted aggregate.
     *
     * @param aggregate the aggregate to delete
     */
    protected abstract void doDelete(T aggregate);

    /**
     * Sets the event bus to which newly stored events should be published. Optional. By default, the repository tries
     * to autowire the event bus.
     *
     * @param eventBus the event bus to publish events to
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Perform action that needs to be done directly after updating an aggregate and committing the aggregate's
     * uncommitted events.
     *
     * @param aggregate The aggregate instance being saved
     */
    protected void postSave(T aggregate) {
    }

    /**
     * Perform action that needs to be done directly after deleting an aggregate and committing the aggregate's
     * uncommitted events.
     *
     * @param aggregate The aggregate instance being saved
     */
    protected void postDelete(T aggregate) {
    }

    private class SimpleSaveAggregateCallback implements SaveAggregateCallback<T> {

        @Override
        public void save(final T aggregate) {
            if (aggregate.isDeleted()) {
                doDelete(aggregate);
            } else {
                doSave(aggregate);
            }
            aggregate.commitEvents();
            if (aggregate.isDeleted()) {
                postDelete(aggregate);
            } else {
                postSave(aggregate);
            }
        }
    }
}
