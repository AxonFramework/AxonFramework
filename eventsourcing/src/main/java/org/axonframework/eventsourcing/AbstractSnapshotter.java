/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.DirectExecutor;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of the {@link org.axonframework.eventsourcing.Snapshotter} that uses a task executor to
 * creates snapshots. Actual snapshot creation logic should be provided by a subclass.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class AbstractSnapshotter implements Snapshotter {

    private static final String SCHEDULED_SNAPSHOT_SET = "SCHEDULED_SNAPSHOT_SET";

    private static final Logger logger = LoggerFactory.getLogger(AbstractSnapshotter.class);

    private final EventStore eventStore;
    private final Executor executor;
    private final TransactionManager transactionManager;
    private final Set<AggregateTypeId> snapshotsInProgress = ConcurrentHashMap.newKeySet();
    private final SpanFactory spanFactory;

    /**
     * Instantiate a {@link AbstractSnapshotter} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link EventStore} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if it is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractSnapshotter} instance
     */
    protected AbstractSnapshotter(Builder builder) {
        builder.validate();
        this.eventStore = builder.eventStore;
        this.executor = builder.executor;
        this.transactionManager = builder.transactionManager;
        this.spanFactory = builder.builderSpanFactory;
    }

    @Override
    public void scheduleSnapshot(@Nonnull Class<?> aggregateType, @Nonnull String aggregateIdentifier) {
        if (CurrentUnitOfWork.isStarted() && CurrentUnitOfWork.get().phase().isBefore(UnitOfWork.Phase.COMMIT)) {
            CurrentUnitOfWork.get().afterCommit(u -> doScheduleSnapshot(aggregateType, aggregateIdentifier));
        } else {
            doScheduleSnapshot(aggregateType, aggregateIdentifier);
        }
    }

    private void doScheduleSnapshot(Class<?> aggregateType, String aggregateIdentifier) {
        AggregateTypeId typeAndId = new AggregateTypeId(aggregateType, aggregateIdentifier);
        if (CurrentUnitOfWork.isStarted()) {
            Set<AggregateTypeId> scheduledSnapshotMap =
                    CurrentUnitOfWork.get()
                                     .root()
                                     .getOrComputeResource(SCHEDULED_SNAPSHOT_SET, key -> new HashSet<>());
            if (!scheduledSnapshotMap.add(typeAndId)) {
                return;
            }
        }
        if (snapshotsInProgress.add(typeAndId)) {
            String rootTraceName = String.format("createSnapshot %s",
                                                 aggregateType.getSimpleName());
            Span span = spanFactory.createRootTrace(rootTraceName);
            try {
                // The traces here are separated for two reasons: A) measuring the delay between scheduling and making
                // B) To have a more generic name for trace grouping and a more specific name for the span with aggId
                String innerTraceName = String.format("createSnapshot %s %s",
                                                      aggregateType.getSimpleName(),
                                                      aggregateIdentifier);
                executor.execute(span.wrapRunnable(silently(() -> spanFactory.createInternalSpan(innerTraceName)
                                                                             .run(() ->
                                                                                          transactionManager.executeInTransaction(

                                                                                                  createSnapshotterTask(
                                                                                                          aggregateType,
                                                                                                          aggregateIdentifier)))
                ).andFinally(() -> snapshotsInProgress.remove(typeAndId))));
            } catch (Exception e) {
                snapshotsInProgress.remove(typeAndId);
                throw e;
            }
        }
    }

    private SilentTask silently(Runnable r) {
        return new SilentTask(r);
    }

    /**
     * Creates an instance of a task that contains the actual snapshot creation logic.
     *
     * @param aggregateType       The type of the aggregate to create a snapshot for
     * @param aggregateIdentifier The identifier of the aggregate to create a snapshot for
     *
     * @return the task containing snapshot creation logic
     */
    protected Runnable createSnapshotterTask(Class<?> aggregateType, String aggregateIdentifier) {
        return new CreateSnapshotTask(aggregateType, aggregateIdentifier);
    }

    /**
     * Creates a snapshot event for an aggregate of which passed events are available in the given {@code eventStream}.
     * May return {@code null} to indicate a snapshot event is not necessary or appropriate for the given event stream.
     *
     * @param aggregateType       The aggregate's type identifier
     * @param aggregateIdentifier The identifier of the aggregate to create a snapshot for
     * @param eventStream         The event stream containing the aggregate's past events
     * @return the snapshot event for the given events, or {@code null} if none should be stored.
     */
    protected abstract DomainEventMessage createSnapshot(Class<?> aggregateType, String aggregateIdentifier,
                                                         DomainEventStream eventStream);

    /**
     * Returns the event store this snapshotter uses to load domain events and store snapshot events.
     *
     * @return the event store this snapshotter uses to load domain events and store snapshot events.
     */
    protected EventStore getEventStore() {
        return eventStore;
    }

    /**
     * Returns the executor that executes snapshot taking tasks.
     *
     * @return the executor that executes snapshot taking tasks.
     */
    protected Executor getExecutor() {
        return executor;
    }

    private static class AggregateTypeId {
        private final Class<?> aggregateType;
        private final String aggregateIdentifier;

        private AggregateTypeId(Class<?> aggregateType, String aggregateIdentifier) {
            this.aggregateType = aggregateType;
            this.aggregateIdentifier = aggregateIdentifier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AggregateTypeId that = (AggregateTypeId) o;
            return Objects.equals(aggregateType, that.aggregateType) &&
                    Objects.equals(aggregateIdentifier, that.aggregateIdentifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aggregateType, aggregateIdentifier);
        }
    }

    /**
     * Abstract Builder class to instantiate {@link AbstractSnapshotter} implementations.
     * <p>
     * The {@link Executor} is defaulted to an {@link DirectExecutor#INSTANCE} and the {@link TransactionManager}
     * defaults to a {@link NoTransactionManager}. The {@link EventStore} is a <b>hard requirement</b> and as such
     * should be provided.
     */
    public abstract static class Builder {

        private EventStore eventStore;
        private Executor executor = DirectExecutor.INSTANCE;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private SpanFactory builderSpanFactory = NoOpSpanFactory.INSTANCE;

        /**
         * Sets the {@link EventStore} instance which this {@link AbstractSnapshotter} implementation will store
         * snapshots in.
         *
         * @param eventStore the {@link EventStore} instance which this {@link AbstractSnapshotter} implementation will
         *                   store snapshots in
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventStore(EventStore eventStore) {
            assertNonNull(eventStore, "EventStore may not be null");
            this.eventStore = eventStore;
            return this;
        }

        /**
         * Sets the {@link Executor} which handles the actual snapshot creation process. Defaults to a
         * {@link DirectExecutor}.
         *
         * @param executor an {@link Executor} which handles the actual snapshot creation process
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder executor(Executor executor) {
            assertNonNull(executor, "Executor may not be null");
            this.executor = executor;
            return this;
        }

        /**
         * Sets the {@link SpanFactory} implementation to use for providing tracing capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(SpanFactory spanFactory) {
            this.builderSpanFactory = spanFactory;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage the transaction around storing the snapshot. Defaults to a
         * {@link NoTransactionManager}.
         *
         * @param transactionManager the {@link TransactionManager} used to manage the transaction around storing the
         *                           snapshot
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(eventStore, "The EventStore is a hard requirement and should be provided");
        }
    }

    private static class SilentTask implements Runnable {

        private final Runnable snapshotterTask;

        private SilentTask(Runnable snapshotterTask) {
            this.snapshotterTask = snapshotterTask;
        }

        @Override
        public void run() {
            try {
                snapshotterTask.run();
            } catch (ConcurrencyException e) {
                logger.info("An up-to-date snapshot entry already exists, ignoring this attempt.");
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.warn("An attempt to create and store a snapshot resulted in an exception:", e);
                } else {
                    logger.warn("An attempt to create and store a snapshot resulted in an exception. " +
                                        "Exception summary: {}", e.getMessage());
                }
            }
        }

        public Runnable andFinally(Runnable r) {
            return new RunnableAndFinally(this, r);
        }

    }

    private static class RunnableAndFinally implements Runnable {
        private final Runnable first;

        private final Runnable then;

        public RunnableAndFinally(Runnable first, Runnable then) {
            this.first = first;
            this.then = then;
        }

        @Override
        public void run() {
            try {
                first.run();
            } finally {
                then.run();
            }
        }

    }

    private final class CreateSnapshotTask implements Runnable {

        private final Class<?> aggregateType;
        private final String identifier;

        private CreateSnapshotTask(Class<?> aggregateType, String identifier) {
            this.aggregateType = aggregateType;
            this.identifier = identifier;
        }

        @Override
        public void run() {
            DomainEventStream eventStream = eventStore.readEvents(identifier);
            // a snapshot should only be stored if the snapshot replaces at least more than one event
            long firstEventSequenceNumber = eventStream.peek().getSequenceNumber();
            DomainEventMessage snapshotEvent = createSnapshot(aggregateType, identifier, eventStream);
            if (snapshotEvent != null && snapshotEvent.getSequenceNumber() > firstEventSequenceNumber) {
                eventStore.storeSnapshot(snapshotEvent);
            }
        }
    }
}
