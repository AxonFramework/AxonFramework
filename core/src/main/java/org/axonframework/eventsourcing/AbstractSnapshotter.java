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

import org.axonframework.common.DirectExecutor;
import org.axonframework.common.io.IOUtils;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.unitofwork.NoTransactionManager;
import org.axonframework.unitofwork.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * Abstract implementation of the {@link org.axonframework.eventsourcing.Snapshotter} that uses a task executor to
 * creates snapshots. Actual snapshot creation logic should be provided by a subclass.
 * <p/>
 * By default, this implementations uses a {@link org.axonframework.common.DirectExecutor} to process snapshot taking
 * tasks. In production environments, it is recommended to use asynchronous executors instead.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class AbstractSnapshotter implements Snapshotter {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSnapshotter.class);

    private SnapshotEventStore eventStore;
    private Executor executor = DirectExecutor.INSTANCE;
    private TransactionManager transactionManager = new NoTransactionManager();

    @Override
    public void scheduleSnapshot(String typeIdentifier, Object aggregateIdentifier) {
        executor.execute(new SilentTask(
                new TransactionalRunnableWrapper(transactionManager,
                                                 createSnapshotterTask(typeIdentifier, aggregateIdentifier)
                )));
    }

    /**
     * Creates an instance of a task that contains the actual snapshot creation logic.
     *
     * @param typeIdentifier      The type of the aggregate to create a snapshot for
     * @param aggregateIdentifier The identifier of the aggregate to create a snapshot for
     * @return the task containing snapshot creation logic
     */
    protected Runnable createSnapshotterTask(String typeIdentifier, Object aggregateIdentifier) {
        return new CreateSnapshotTask(typeIdentifier, aggregateIdentifier);
    }

    /**
     * Creates a snapshot event for an aggregate of the given <code>typeIdentifier</code> of which passed events are
     * available in the given <code>eventStream</code>. May return <code>null</code> to indicate a snapshot event is
     * not necessary or appropriate for the given event stream.
     *
     * @param typeIdentifier      The aggregate's type identifier
     * @param aggregateIdentifier The identifier of the aggregate to create a snapshot for
     * @param eventStream         The event stream containing the aggregate's past events
     * @return the snapshot event for the given events, or <code>null</code> if none should be stored.
     */
    protected abstract DomainEventMessage createSnapshot(String typeIdentifier, Object aggregateIdentifier,
                                                         DomainEventStream eventStream);

    /**
     * Sets the transactionManager that wraps the snapshot creation in a transaction. By default, no transactions are
     * created.
     *
     * @param transactionManager the transactionManager to create transactions with
     */
    public void setTxManager(TransactionManager<?> transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Returns the event store this snapshotter uses to load domain events and store snapshot events.
     *
     * @return the event store this snapshotter uses to load domain events and store snapshot events.
     */
    protected SnapshotEventStore getEventStore() {
        return eventStore;
    }

    /**
     * Sets the event store where the snapshotter can load domain events and store its snapshot events.
     *
     * @param eventStore the event store to use
     */
    public void setEventStore(SnapshotEventStore eventStore) {
        this.eventStore = eventStore;
    }

    /**
     * Returns the executor that executes snapshot taking tasks.
     *
     * @return the executor that executes snapshot taking tasks.
     */
    protected Executor getExecutor() {
        return executor;
    }

    /**
     * Sets the executor that should process actual snapshot taking. Defaults to an instance that runs all actions in
     * the calling thread (i.e. synchronous execution).
     *
     * @param executor the executor to execute snapshotting tasks
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    private static class TransactionalRunnableWrapper implements Runnable {

        private final Runnable command;
        private final TransactionManager transactionManager;

        public TransactionalRunnableWrapper(TransactionManager transactionManager, Runnable command) {
            this.command = command;
            this.transactionManager = transactionManager;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            Object transaction = transactionManager.startTransaction();
            try {
                command.run();
                transactionManager.commitTransaction(transaction);
            } catch (RuntimeException e) {
                transactionManager.rollbackTransaction(transaction);
                throw e;
            }
        }
    }

    private static class SilentTask implements Runnable {

        private final Runnable snapshotterTask;

        public SilentTask(Runnable snapshotterTask) {
            this.snapshotterTask = snapshotterTask;
        }

        @Override
        public void run() {
            try {
                snapshotterTask.run();
            } catch (ConcurrencyException e) {
                logger.info("An up-to-date snapshot entry already exists, ignoring this attempts.");
            } catch (RuntimeException e) {
                if (logger.isDebugEnabled()) {
                    logger.warn("An attempt to create and store a snapshot resulted in an exception:", e);
                } else {
                    logger.warn("An attempt to create and store a snapshot resulted in an exception. "
                                        + "Exception summary: {}", e.getMessage());
                }
            }
        }
    }

    private final class CreateSnapshotTask implements Runnable {

        private final String typeIdentifier;
        private final Object aggregateIdentifier;

        private CreateSnapshotTask(String typeIdentifier, Object aggregateIdentifier) {
            this.typeIdentifier = typeIdentifier;
            this.aggregateIdentifier = aggregateIdentifier;
        }

        @Override
        public void run() {
            DomainEventStream eventStream = eventStore.readEvents(typeIdentifier, aggregateIdentifier);
            try {
                // a snapshot should only be stored if the snapshot replaces at least more than one event
                long firstEventSequenceNumber = eventStream.peek().getSequenceNumber();
                DomainEventMessage snapshotEvent = createSnapshot(typeIdentifier, aggregateIdentifier, eventStream);
                if (snapshotEvent != null && snapshotEvent.getSequenceNumber() > firstEventSequenceNumber) {
                    eventStore.appendSnapshotEvent(typeIdentifier, snapshotEvent);
                }
            } finally {
                IOUtils.closeQuietlyIfCloseable(eventStream);
            }
        }
    }
}
