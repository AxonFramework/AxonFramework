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

package org.axonframework.unitofwork;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;

/**
 * This class represents a UnitOfWork in which modifications are made to aggregates. A typical UnitOfWork scope is the
 * execution of a command. A UnitOfWork may be used to prevent individual events from being published before a number of
 * aggregates has been processed. It also allows repositories to manage resources, such as locks, over an entire
 * transaction. Locks, for example, will only be released when the UnitOfWork is either committed or rolled back.
 * <p/>
 * The current UnitOfWork can be obtained using {@link CurrentUnitOfWork#get()}.
 *
 * @author Allard Buijze
 * @see CurrentUnitOfWork
 * @since 0.6
 */
public abstract class UnitOfWork {

    private boolean isStarted;
    private UnitOfWork outerUnitOfWork;

    /**
     * Commits the UnitOfWork. All registered aggregates that have not been registered as stored are saved in their
     * respective repositories, buffered events are sent to their respective event bus, and all registered
     * UnitOfWorkListeners are notified.
     * <p/>
     * After the commit (successful or not), the UnitOfWork is unregistered from the CurrentUnitOfWork.
     *
     * @throws IllegalStateException if the UnitOfWork wasn't started
     */
    public void commit() {
        assertStarted();
        try {
            if (outerUnitOfWork != null) {
                outerUnitOfWork.registerListener(new CommitOnOuterCommitTask());
            } else {
                performCommit();
            }
        } finally {
            clear();
        }
    }

    /**
     * Clear the UnitOfWork of any buffered changes. All buffered events and registered aggregates are discarded and
     * registered {@link UnitOfWorkListener}s are notified.
     *
     * @throws IllegalStateException if the UnitOfWork wasn't started
     */
    public void rollback() {
        assertStarted();
        try {
            if (outerUnitOfWork != null) {
                outerUnitOfWork.registerListener(new CommitOnOuterCommitTask());
            } else {
                doRollback();
            }
        } finally {
            clear();
        }
    }

    /**
     * Starts the current unit of work, preparing it for aggregate registration. The UnitOfWork instance is registered
     * with the CurrentUnitOfWork.
     */
    public void start() {
        if (isStarted) {
            throw new IllegalStateException("UnitOfWork is already started");
        } else if (CurrentUnitOfWork.isStarted()) {
            // we're nesting.
            this.outerUnitOfWork = CurrentUnitOfWork.get();
        }
        CurrentUnitOfWork.set(this);
        isStarted = true;
        doStart();
    }

    /**
     * Indicates whether this UnitOfWork is started. It is started when the {@link #start()} method has been called, and
     * if the UnitOfWork has not been committed or rolled back.
     *
     * @return <code>true</code> if this UnitOfWork is started, <code>false</code> otherwise.
     */
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * Register a listener that listens to state changes in this UnitOfWork. This typically allows components to clean
     * up resources, such as locks, when a UnitOfWork is committed or rolled back. If a UnitOfWork is partially
     * committed, only the listeners bound to one of the committed aggregates is notified.
     *
     * @param listener The listener to notify when the UnitOfWork's state changes.
     */
    public abstract void registerListener(UnitOfWorkListener listener);

    /**
     * Register an aggregate with this UnitOfWork. These aggregates will be saved (at the latest) when the UnitOfWork is
     * committed.
     *
     * @param aggregateRoot         The aggregate root to register in the UnitOfWork
     * @param saveAggregateCallback The callback that is invoked when the UnitOfWork wants to store the registered
     *                              aggregate
     * @param <T>                   the type of aggregate to register
     */
    public abstract <T extends AggregateRoot> void registerAggregate(T aggregateRoot,
                                                                     SaveAggregateCallback<T> saveAggregateCallback);

    /**
     * Request to publish the given <code>event</code> on the given <code>eventBus</code>. The UnitOfWork may either
     * publish immediately, or buffer the events until the UnitOfWork is committed.
     *
     * @param event    The event to be published on the event bus
     * @param eventBus The event bus on which to publish the event
     */
    public abstract void publishEvent(Event event, EventBus eventBus);

    /**
     * Performs logic required when starting this UnitOfWork instance.
     * <p/>
     * This implementation does nothing and may be freely overridden.
     */
    protected void doStart() {
    }

    /**
     * Executes the logic required to commit this unit of work.
     */
    protected abstract void doCommit();

    /**
     * Executes the logic required to commit this unit of work.
     */
    protected abstract void doRollback();

    private void performCommit() {
        try {
            doCommit();
        } catch (RuntimeException t) {
            doRollback();
            throw t;
        }
    }

    private void assertStarted() {
        if (!isStarted) {
            throw new IllegalStateException("UnitOfWork is not started");
        }
    }

    private void clear() {
        CurrentUnitOfWork.clear(this);
        isStarted = false;
    }

    private class CommitOnOuterCommitTask implements UnitOfWorkListener {

        @Override
        public void afterCommit() {
            CurrentUnitOfWork.set(UnitOfWork.this);
            try {
                performCommit();
            } finally {
                CurrentUnitOfWork.clear(UnitOfWork.this);
            }
        }

        @Override
        public void onRollback() {
            CurrentUnitOfWork.set(UnitOfWork.this);
            try {
                doRollback();
            } finally {
                CurrentUnitOfWork.clear(UnitOfWork.this);
            }
        }

        @Override
        public void onPrepareCommit() {
        }

    }

}
