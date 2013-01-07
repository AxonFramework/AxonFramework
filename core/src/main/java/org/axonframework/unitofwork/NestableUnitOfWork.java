/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract implementation of the UnitOfWork interface. Provides the necessary implementations to support most actions
 * required by any Unit of Work, such as managing registration with the {@link CurrentUnitOfWork} and support for
 * nesting a Unit of Work.
 *
 * @author Allard Buijze
 * @see CurrentUnitOfWork
 * @since 0.7
 */
public abstract class NestableUnitOfWork implements UnitOfWork {

    private static final Logger logger = LoggerFactory.getLogger(NestableUnitOfWork.class);

    private boolean isStarted;
    private UnitOfWork outerUnitOfWork;
    private List<NestableUnitOfWork> innerUnitsOfWork = new ArrayList<NestableUnitOfWork>();

    @Override
    public void commit() {
        logger.debug("Committing Unit Of Work");
        assertStarted();
        try {
            notifyListenersPrepareCommit();
            saveAggregates();
            if (outerUnitOfWork == null) {
                logger.debug("This Unit Of Work is not nested. Finalizing commit...");
                doCommit();
                stop();
                performCleanup();
            } else if (logger.isDebugEnabled()) {
                logger.debug("This Unit Of Work is nested. Commit will be finalized by outer Unit Of Work.");
            }
        } catch (RuntimeException e) {
            logger.debug("An error occurred while committing this UnitOfWork. Performing rollback...");
            doRollback(e);
            stop();
            if (outerUnitOfWork == null) {
                performCleanup();
            }
            throw e;
        } finally {
            logger.debug("Clearing resources of this Unit Of Work.");
            clear();
        }
    }

    private void performCleanup() {
        for (NestableUnitOfWork uow : innerUnitsOfWork) {
            uow.performCleanup();
        }
        notifyListenersCleanup();
    }

    /**
     * Send a {@link org.axonframework.unitofwork.UnitOfWorkListener#onCleanup(UnitOfWork)} notification to all
     * registered listeners. The implementation must ensure that all listeners are notified, even if one throws an
     * exception.
     */
    protected abstract void notifyListenersCleanup();

    /**
     * Send a {@link UnitOfWorkListener#onRollback(UnitOfWork, Throwable)} notification to all registered listeners.
     *
     * @param cause The cause of the rollback
     */
    protected abstract void notifyListenersRollback(Throwable cause);

    @Override
    public void rollback() {
        rollback(null);
    }

    @Override
    public void rollback(Throwable cause) {
        if (cause != null && logger.isInfoEnabled()) {
            logger.debug("Rollback requested for Unit Of Work due to exception. ", cause);
        } else if (logger.isInfoEnabled()) {
            logger.debug("Rollback requested for Unit Of Work for unknown reason.");
        }

        try {
            if (isStarted()) {
                for (NestableUnitOfWork inner : innerUnitsOfWork) {
                    CurrentUnitOfWork.set(inner);
                    inner.rollback(cause);
                }
                doRollback(cause);
            }
        } finally {
            if (outerUnitOfWork == null) {
                performCleanup();
            }
            clear();
            stop();
        }
    }

    @Override
    public void start() {
        logger.debug("Starting Unit Of Work.");
        if (isStarted) {
            throw new IllegalStateException("UnitOfWork is already started");
        }

        doStart();
        if (CurrentUnitOfWork.isStarted()) {
            // we're nesting.
            this.outerUnitOfWork = CurrentUnitOfWork.get();
            if (outerUnitOfWork instanceof NestableUnitOfWork) {
                ((NestableUnitOfWork) outerUnitOfWork).registerInnerUnitOfWork(this);
            } else {
                outerUnitOfWork.registerListener(new CommitOnOuterCommitTask());
            }
        }
        logger.debug("Registering Unit Of Work as CurrentUnitOfWork");
        CurrentUnitOfWork.set(this);
        isStarted = true;
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    private void stop() {
        logger.debug("Stopping Unit Of Work");
        isStarted = false;
    }

    /**
     * Performs logic required when starting this UnitOfWork instance.
     */
    protected abstract void doStart();

    /**
     * Executes the logic required to commit this unit of work.
     */
    protected abstract void doCommit();

    /**
     * Executes the logic required to commit this unit of work.
     *
     * @param cause the cause of the rollback
     */
    protected abstract void doRollback(Throwable cause);

    private void performInnerCommit() {
        logger.debug("Finalizing commit of inner Unit Of Work...");
        CurrentUnitOfWork.set(this);
        try {
            doCommit();
        } catch (RuntimeException t) {
            doRollback(t);
            throw t;
        } finally {
            clear();
            stop();
        }
    }

    private void assertStarted() {
        if (!isStarted) {
            throw new IllegalStateException("UnitOfWork is not started");
        }
    }

    private void clear() {
        CurrentUnitOfWork.clear(this);
    }

    /**
     * Commit all registered inner units of work. This should be invoked after events have been dispatched and before
     * any listeners are notified of the commit.
     */
    protected void commitInnerUnitOfWork() {
        for (NestableUnitOfWork unitOfWork : innerUnitsOfWork) {
            if (unitOfWork.isStarted()) {
                unitOfWork.performInnerCommit();
            }
        }
    }

    private void registerInnerUnitOfWork(NestableUnitOfWork unitOfWork) {
        innerUnitsOfWork.add(unitOfWork);
    }

    /**
     * Saves all registered aggregates by calling their respective callbacks.
     */
    protected abstract void saveAggregates();

    /**
     * Send a {@link org.axonframework.unitofwork.UnitOfWorkListener#onPrepareCommit(UnitOfWork, java.util.Set,
     * java.util.List)} notification to all registered listeners.
     */
    protected abstract void notifyListenersPrepareCommit();

    private final class CommitOnOuterCommitTask extends UnitOfWorkListenerAdapter {

        @Override
        public void afterCommit(UnitOfWork unitOfWork) {
            performInnerCommit();
        }

        @Override
        public void onRollback(UnitOfWork unitOfWork, Throwable failureCause) {
            CurrentUnitOfWork.set(NestableUnitOfWork.this);
            try {
                doRollback(failureCause);
            } finally {
                CurrentUnitOfWork.clear(NestableUnitOfWork.this);
            }
        }

        @Override
        public void onCleanup(UnitOfWork unitOfWork) {
            performCleanup();
        }
    }
}
