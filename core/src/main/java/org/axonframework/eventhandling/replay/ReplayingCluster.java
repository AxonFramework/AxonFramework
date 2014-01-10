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

package org.axonframework.eventhandling.replay;

import org.axonframework.common.DirectExecutor;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.ClusterMetaData;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventProcessingMonitor;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.unitofwork.TransactionManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

/**
 * Cluster implementation that wraps another Cluster, adding the capability to replay events from an Event Store. All
 * events are forwarded for handling to a delegate cluster. When in replay mode, incoming events are forwarded to an
 * {@link IncomingMessageHandler}, which defines the behavior for these events.
 * <p/>
 * Replays can either be executed on the invoking thread (see {@link #startReplay()}) or asynchronously by providing an
 * Executor (see {@link #startReplay(java.util.concurrent.Executor)}).
 * <p/>
 * Note that this cluster will replay each event on all subscribed listeners, even those that do not implement the
 * {@link ReplayAware} interface. If a listener does not support replaying at all, it should not be
 * subscribed to either this cluster or the delegate.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ReplayingCluster implements Cluster {

    private final Cluster delegate;
    private final EventStoreManagement replayingEventStore;
    private final TransactionManager transactionManager;
    private final int commitThreshold;
    private final IncomingMessageHandler incomingMessageHandler;
    private final Set<ReplayAware> replayAwareListeners = new CopyOnWriteArraySet<ReplayAware>();

    private volatile Status status = Status.LIVE;
    private final EventProcessingListeners eventHandlingListeners = new EventProcessingListeners();

    /**
     * Initializes a ReplayingCluster that wraps the given <code>delegate</code>, to allow it to replay event from the
     * given <code>eventStore</code>. The given <code>transactionManager</code> is used to create a transaction for the
     * replay process. While in replay mode, the given <code>incomingMessageHandler</code> receives all Event Messages
     * being published to this instance. The given <code>commitThreshold</code> indicates how many events may be
     * processed within the same transaction. Values of 0 (zero) and negative values will prevent intermediate commits
     * altogether.
     *
     * @param delegate               The cluster to add replaying capability to
     * @param eventStore             The event store providing access to events to replay
     * @param transactionManager     The transaction manager providing the transaction for the replay process
     * @param commitThreshold        The number of messages to process before doing an intermediate commit (0 and
     *                               negative values prevent intermediate commits)
     * @param incomingMessageHandler The handler to receive Messages while in replay mode
     */
    public ReplayingCluster(Cluster delegate, EventStoreManagement eventStore, TransactionManager transactionManager,
                            int commitThreshold, IncomingMessageHandler incomingMessageHandler) {
        this.delegate = delegate;
        this.replayingEventStore = eventStore;
        this.transactionManager = transactionManager;
        this.commitThreshold = commitThreshold;
        this.incomingMessageHandler = incomingMessageHandler;
        this.delegate.subscribeEventProcessingMonitor(eventHandlingListeners);
    }


    /**
     * Returns a CriteriaBuilder that allows the construction of criteria for this EventStore implementation
     *
     * @return a builder to create Criteria for this Event Store.
     *
     * @see EventStoreManagement#newCriteriaBuilder()
     */
    public CriteriaBuilder newCriteriaBuilder() {
        return replayingEventStore.newCriteriaBuilder();
    }

    /**
     * Starts a replay process on the current thread. This method will return once the replay process is finished.
     *
     * @throws ReplayFailedException when an exception occurred during the replay process
     */
    public void startReplay() {
        startReplay((Criteria) null);
    }

    /**
     * Start a replay process on the current thread, only reading events matching the given <code>criteria</code>. This
     * method will return once the replay process is finished.
     *
     * @param criteria The criteria defining the events to reply
     * @throws ReplayFailedException when an exception occurred during the replay process
     */
    public void startReplay(Criteria criteria) {
        try {
            startReplay(DirectExecutor.INSTANCE, criteria).get();
        } catch (InterruptedException e) {
            // Can't really occur, because we're running the task in the scheduling thread.
            Thread.currentThread().interrupt();
            throw new ReplayFailedException("Replay failed because it was interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ReplayFailedException) {
                throw (ReplayFailedException) e.getCause(); // NOSONAR
            } else {
                throw new ReplayFailedException("Replay failed due to an exception.", e.getCause()); // NOSONAR
            }
        }
    }

    /**
     * Starts a replay process using the given <code>executor</code>. The replay process itself uses a single thread.
     *
     * @param executor The executor to execute the replay process
     * @return a Future that allows the calling thread to track progress.
     */
    public Future<Void> startReplay(Executor executor) {
        return startReplay(executor, null);
    }

    /**
     * Starts a replay process using the given <code>executor</code>, only reading events matching the given
     * <code>criteria</code>. The replay process itself uses a single thread.
     *
     * @param executor The executor to execute the replay process
     * @param criteria The criteria defining the events to reply
     * @return a Future that allows the calling thread to track progress.
     *
     * @throws ReplayFailedException when an exception occurred during the replay process
     */
    public Future<Void> startReplay(Executor executor, Criteria criteria) {
        RunnableFuture<Void> task = new FutureTask<Void>(new ReplayEventsTask(criteria), null);
        executor.execute(task);
        return task;
    }

    /**
     * Indicates whether this cluster is in replay mode. While in replay mode, EventMessages published to this cluster
     * are forwarded to the IncomingMessageHandler.
     *
     * @return <code>true</code> if this cluster is in replay mode, <code>false</code> otherwise.
     */
    public boolean isInReplayMode() {
        return status != Status.LIVE;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void publish(EventMessage... events) {
        if (status == Status.LIVE) {
            delegate.publish(events);
        } else {
            List<EventMessage> acknowledgedMessages = incomingMessageHandler.onIncomingMessages(delegate, events);
            if (acknowledgedMessages != null && !acknowledgedMessages.isEmpty()) {
                eventHandlingListeners.onEventProcessingCompleted(acknowledgedMessages);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * If the given <code>eventListener</code> implements {@link ReplayAware}, its {@link
     * ReplayAware#beforeReplay()} and {@link
     * ReplayAware#afterReplay()} methods will be invoked before and after the replay process,
     * respectively.
     * <p/>
     * EventListeners that are subscribed while the cluster is in replay mode <em>might</em> receive some of the
     * replayed events and might not have their {@link
     * ReplayAware#beforeReplay()} method invoked.
     *
     * @see #isInReplayMode()
     */
    @Override
    public void subscribe(EventListener eventListener) {
        delegate.subscribe(eventListener);
        if (eventListener instanceof ReplayAware) {
            replayAwareListeners.add((ReplayAware) eventListener);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * If the given <code>eventListener</code> implements {@link ReplayAware} and is unsubscribed during
     * replay, it <em>might not</em> have its {@link ReplayAware#afterReplay()} method invoked when the
     * replay process is finished.
     *
     * @see #isInReplayMode()
     */
    @Override
    public void unsubscribe(EventListener eventListener) {
        if (eventListener instanceof ReplayAware) {
            replayAwareListeners.remove(eventListener);
        }
        delegate.unsubscribe(eventListener);
    }

    @Override
    public Set<EventListener> getMembers() {
        return delegate.getMembers();
    }

    @Override
    public ClusterMetaData getMetaData() {
        return delegate.getMetaData();
    }

    @Override
    public void subscribeEventProcessingMonitor(EventProcessingMonitor monitor) {
        eventHandlingListeners.delegates.add(monitor);
    }

    @Override
    public void unsubscribeEventProcessingMonitor(EventProcessingMonitor monitor) {
        eventHandlingListeners.delegates.remove(monitor);
    }

    private class ReplayEventsTask implements Runnable {

        private Criteria criteria;

        public ReplayEventsTask(Criteria criteria) {
            this.criteria = criteria;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            incomingMessageHandler.prepareForReplay(delegate);
            status = Status.REPLAYING;
            Object tx = transactionManager.startTransaction();
            final ReplayingEventVisitor visitor = new ReplayingEventVisitor(tx);
            try {
                for (ReplayAware replayAwareEventListener : replayAwareListeners) {
                    replayAwareEventListener.beforeReplay();
                }
                if (criteria != null) {
                    replayingEventStore.visitEvents(criteria, visitor);
                } else {
                    replayingEventStore.visitEvents(visitor);
                }
                for (ReplayAware replayAwareEventListener : replayAwareListeners) {
                    replayAwareEventListener.afterReplay();
                }
                status = Status.PROCESSING_BACKLOG;
                incomingMessageHandler.processBacklog(delegate);
                transactionManager.commitTransaction(visitor.getTransaction());
            } catch (Throwable t) {
                try {
                    incomingMessageHandler.onReplayFailed(delegate, t);
                    for (ReplayAware replayAwareEventListener : replayAwareListeners) {
                        replayAwareEventListener.onReplayFailed(t);
                    }
                } finally {
                    transactionManager.rollbackTransaction(visitor.getTransaction());
                }
                throw new ReplayFailedException("Replay failed due to an exception.", t);
            } finally {
                status = Status.LIVE;
            }
        }

        private class ReplayingEventVisitor implements EventVisitor {

            private int eventCounter = 0;
            private Object currentTransaction;

            public ReplayingEventVisitor(Object tx) {
                this.currentTransaction = tx;
            }

            @SuppressWarnings("unchecked")
            @Override
            public void doWithEvent(DomainEventMessage domainEvent) {
                if (commitThreshold > 0 && ++eventCounter > commitThreshold) {
                    eventCounter = 0;
                    transactionManager.commitTransaction(currentTransaction);
                    currentTransaction = transactionManager.startTransaction();
                }
                delegate.publish(domainEvent);
                List<EventMessage> releasedMessages = incomingMessageHandler.releaseMessage(delegate, domainEvent);
                if (releasedMessages != null && !releasedMessages.isEmpty()) {
                    eventHandlingListeners.onEventProcessingCompleted(releasedMessages);
                }
            }

            public Object getTransaction() {
                return currentTransaction;
            }
        }
    }

    private final class EventProcessingListeners implements EventProcessingMonitor {

        private Set<EventProcessingMonitor> delegates = new CopyOnWriteArraySet<EventProcessingMonitor>();

        @Override
        public void onEventProcessingCompleted(List<? extends EventMessage> eventMessages) {
            if (status != Status.REPLAYING) {
                for (EventProcessingMonitor delegate : delegates) {
                    delegate.onEventProcessingCompleted(eventMessages);
                }
            }
        }

        @Override
        public void onEventProcessingFailed(List<? extends EventMessage> eventMessages, Throwable cause) {
            if (status != Status.REPLAYING) {
                for (EventProcessingMonitor delegate : delegates) {
                    delegate.onEventProcessingFailed(eventMessages, cause);
                }
            }
        }
    }

    private static enum Status {
        LIVE, REPLAYING, PROCESSING_BACKLOG
    }
}