/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.messaging.MetaData;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.ApplyMore;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Implementation of an {@link Aggregate} that is sourced from events that have
 * been published by the aggregate.
 *
 * @param <T> The type of the aggregate root object
 */
public class EventSourcedAggregate<T> extends AnnotatedAggregate<T> {

    private final SnapshotTrigger snapshotTrigger;
    private boolean initializing = false;

    /**
     * Initializes an EventSourcedAggregate instance for the given {@code aggregateRoot}, based on the given {@code
     * inspector}, which publishes events to the given {@code eventBus}.
     *
     * @param aggregateRoot      The aggregate root instance
     * @param inspector          The inspector describing the aggregate structure
     * @param eventBus           The event bus to send generated events to
     * @param snapshotTrigger    The trigger to notify of events and initialization
     * @param <T>                the aggregate root type
     * @return the initialized EventSourcedAggregate instance
     */
    public static <T> EventSourcedAggregate<T> initialize(T aggregateRoot, AggregateModel<T> inspector,
                                                          EventBus eventBus, SnapshotTrigger snapshotTrigger) {
        return initialize(aggregateRoot, inspector, eventBus, null, snapshotTrigger);
    }

    /**
     * Initializes an EventSourcedAggregate instance for the given {@code aggregateRoot}, based on the given {@code
     * inspector}, which publishes events to the given {@code eventBus}.
     *
     * @param aggregateRoot      The aggregate root instance
     * @param inspector          The inspector describing the aggregate structure
     * @param eventBus           The event bus to send generated events to
     * @param repositoryProvider Provides repositories for specific aggregate types
     * @param snapshotTrigger    The trigger to notify of events and initialization
     * @param <T>                the aggregate root type
     * @return the initialized EventSourcedAggregate instance
     */
    public static <T> EventSourcedAggregate<T> initialize(T aggregateRoot, AggregateModel<T> inspector,
                                                          EventBus eventBus, RepositoryProvider repositoryProvider,
                                                          SnapshotTrigger snapshotTrigger) {
        return new EventSourcedAggregate<>(aggregateRoot, inspector, eventBus, repositoryProvider, snapshotTrigger);
    }

    /**
     * Initializes an EventSourcedAggregate instance using the given {@code aggregateFactory}, based on the given {@code
     * inspector}, which publishes events to the given {@code eventBus} and stores events in the given {@code
     * eventStore}.
     *
     * @param aggregateFactory The aggregate root factory
     * @param inspector        The inspector describing the aggregate structure
     * @param eventBus         The event bus to send generated events to
     * @param snapshotTrigger  The trigger to notify of events and initialization
     * @param <T>              the aggregate root type
     * @return the initialized EventSourcedAggregate instance
     *
     * @throws Exception if the aggregate cannot be initialized
     */
    public static <T> EventSourcedAggregate<T> initialize(Callable<T> aggregateFactory, AggregateModel<T> inspector,
                                                          EventBus eventBus, SnapshotTrigger snapshotTrigger)
            throws Exception {
        return initialize(aggregateFactory, inspector, eventBus, null, snapshotTrigger);
    }

    /**
     * Initializes an EventSourcedAggregate instance using the given {@code aggregateFactory}, based on the given {@code
     * inspector}, which publishes events to the given {@code eventBus} and stores events in the given {@code
     * eventStore}.
     *
     * @param aggregateFactory   The aggregate root factory
     * @param inspector          The inspector describing the aggregate structure
     * @param eventBus           The event bus to send generated events to
     * @param repositoryProvider Provides repositories for specific aggregate types
     * @param snapshotTrigger    The trigger to notify of events and initialization
     * @param <T>                the aggregate root type
     * @return the initialized EventSourcedAggregate instance
     *
     * @throws Exception if the aggregate cannot be initialized
     */
    public static <T> EventSourcedAggregate<T> initialize(Callable<T> aggregateFactory, AggregateModel<T> inspector,
                                                          EventBus eventBus, RepositoryProvider repositoryProvider,
                                                          SnapshotTrigger snapshotTrigger) throws Exception {
        EventSourcedAggregate<T> aggregate = new EventSourcedAggregate<>(inspector,
                                                                         eventBus,
                                                                         repositoryProvider,
                                                                         snapshotTrigger);
        aggregate.registerRoot(aggregateFactory);
        return aggregate;
    }

    /**
     * Reconstructs an EventSourcedAggregate instance with given {@code aggregateRoot}. The aggregate's sequence number
     * should be set to the given {@code seqNo} and its deleted flag to the given {@code isDeleted}.
     * <p>
     * Use this method to initialize an EventSourcedAggregate without having to replay the aggregate from events.
     *
     * @param aggregateRoot      The aggregate root instance
     * @param model              The model describing the aggregate structure
     * @param seqNo              The last event sequence number of the aggregate
     * @param isDeleted          Flag to indicate whether or not the aggregate is deleted
     * @param eventBus           The event bus to send generated events to
     * @param snapshotTrigger    The trigger to notify of events and initialization
     * @param <T>                the aggregate root type
     * @return the reconstructed EventSourcedAggregate instance
     */
    public static <T> EventSourcedAggregate<T> reconstruct(T aggregateRoot, AggregateModel<T> model, long seqNo,
                                                           boolean isDeleted, EventBus eventBus,
                                                           SnapshotTrigger snapshotTrigger) {
        return reconstruct(aggregateRoot, model, seqNo, isDeleted, eventBus, null, snapshotTrigger);
    }

    /**
     * Reconstructs an EventSourcedAggregate instance with given {@code aggregateRoot}. The aggregate's sequence number
     * should be set to the given {@code seqNo} and its deleted flag to the given {@code isDeleted}.
     * <p>
     * Use this method to initialize an EventSourcedAggregate without having to replay the aggregate from events.
     *
     * @param aggregateRoot      The aggregate root instance
     * @param model              The model describing the aggregate structure
     * @param seqNo              The last event sequence number of the aggregate
     * @param isDeleted          Flag to indicate whether or not the aggregate is deleted
     * @param eventBus           The event bus to send generated events to
     * @param repositoryProvider Provides repositories for specific aggregate types
     * @param snapshotTrigger    The trigger to notify of events and initialization
     * @param <T>                the aggregate root type
     * @return the reconstructed EventSourcedAggregate instance
     */
    public static <T> EventSourcedAggregate<T> reconstruct(T aggregateRoot, AggregateModel<T> model, long seqNo,
                                                           boolean isDeleted, EventBus eventBus,
                                                           RepositoryProvider repositoryProvider,
                                                           SnapshotTrigger snapshotTrigger) {
        EventSourcedAggregate<T> aggregate = initialize(aggregateRoot,
                                                        model,
                                                        eventBus,
                                                        repositoryProvider,
                                                        snapshotTrigger);
        aggregate.initSequence(seqNo);
        if (isDeleted) {
            aggregate.doMarkDeleted();
        }
        return aggregate;
    }

    /**
     * Initializes an Aggregate instance for the given {@code aggregateRoot}, based on the given {@code model}, which
     * publishes events to the given {@code eventBus}.
     *
     * @param aggregateRoot      The aggregate root instance
     * @param model              The model describing the aggregate structure
     * @param eventBus           The event store to store generated events in
     * @param snapshotTrigger    The trigger to notify of events and initialization
     */
    protected EventSourcedAggregate(T aggregateRoot, AggregateModel<T> model, EventBus eventBus,
                                    SnapshotTrigger snapshotTrigger) {
        super(aggregateRoot, model, eventBus);
        this.initSequence();
        this.snapshotTrigger = snapshotTrigger;
    }

    /**
     * Initializes an Aggregate instance for the given {@code aggregateRoot}, based on the given {@code model}, which
     * publishes events to the given {@code eventBus}.
     *
     * @param aggregateRoot      The aggregate root instance
     * @param model              The model describing the aggregate structure
     * @param eventBus           The event store to store generated events in
     * @param repositoryProvider Provides repositories for specific aggregate types
     * @param snapshotTrigger    The trigger to notify of events and initialization
     */
    protected EventSourcedAggregate(T aggregateRoot, AggregateModel<T> model, EventBus eventBus,
                                    RepositoryProvider repositoryProvider, SnapshotTrigger snapshotTrigger) {
        super(aggregateRoot, model, eventBus, repositoryProvider);
        this.initSequence();
        this.snapshotTrigger = snapshotTrigger;
    }

    /**
     * Creates a new EventSourcedAggregate instance based on the given {@code model}, which publishes events to the
     * given {@code eventBus}. This aggregate is not assigned a root instance yet.
     *
     * @param model              The model describing the aggregate structure
     * @param eventBus           The event store to store generated events in
     * @param snapshotTrigger    The trigger to notify of events and initialization
     * @see #registerRoot(Callable)
     */
    protected EventSourcedAggregate(AggregateModel<T> model, EventBus eventBus, SnapshotTrigger snapshotTrigger) {
        super(model, eventBus);
        this.initSequence();
        this.snapshotTrigger = snapshotTrigger;
    }

    /**
     * Creates a new EventSourcedAggregate instance based on the given {@code model}, which publishes events to the
     * given {@code eventBus}. This aggregate is not assigned a root instance yet.
     *
     * @param model              The model describing the aggregate structure
     * @param eventBus           The event store to store generated events in
     * @param repositoryProvider Provides repositories for specific aggregate types
     * @param snapshotTrigger    The trigger to notify of events and initialization
     * @see #registerRoot(Callable)
     */
    protected EventSourcedAggregate(AggregateModel<T> model, EventBus eventBus, RepositoryProvider repositoryProvider,
                                    SnapshotTrigger snapshotTrigger) {
        super(model, eventBus, repositoryProvider);
        this.initSequence();
        this.snapshotTrigger = snapshotTrigger;
    }

    @Override
    public <P> ApplyMore doApply(P payload, MetaData metaData) {
        if (initializing) {
            return IgnoreApplyMore.INSTANCE;
        } else {
            return super.doApply(payload, metaData);
        }
    }

    @Override
    protected void publish(EventMessage<?> msg) {
        super.publish(msg);
        snapshotTrigger.eventHandled(msg);
        if (identifierAsString() == null && isSnapshotEvent(msg)) {
            throw new IncompatibleAggregateException(
                    "Aggregate identifier must be non-null after applying a snapshot. "
                            + "Make sure the aggregate identifier is included in the snapshot that is used to restore"
                            + " the aggregate from. "
                            + "Note the identifier may be missing due to your serializer being unable to find the"
                            + " private field(s) of your aggregate."
            );
        } else if (identifierAsString() == null) {
            throw new IncompatibleAggregateException("Aggregate identifier must be non-null after applying an event. " +
                                                             "Make sure the aggregate identifier is initialized at " +
                                                             "the latest when handling the creation event.");
        }
    }

    private boolean isSnapshotEvent(EventMessage<?> event) {
        return inspector.types().anyMatch(event.getPayloadType()::equals);
    }

    @Override
    protected void publishOnEventBus(EventMessage<?> msg) {
        if (!initializing) {
            // force conversion of LazyIdentifierDomainEventMessage to Generic to release reference to Aggregate.
            super.publishOnEventBus(msg.andMetaData(Collections.emptyMap()));
        }
    }

    @Override
    public Long version() {
        return lastSequence();
    }

    /**
     * Initialize the state of this Event Sourced Aggregate with the events from the given {@code eventStream}.
     *
     * @param eventStream The Event Stream containing the events to be used to reconstruct this Aggregate's state.
     */
    public void initializeState(DomainEventStream eventStream) {
        execute(r -> {
            this.initializing = true;
            try {
                eventStream.forEachRemaining(this::publish);
                initSequence(eventStream.getLastSequenceNumber());
            } finally {
                this.initializing = false;
                snapshotTrigger.initializationFinished();
            }
        });
    }

    @Override
    protected boolean getIsLive() {
        return !initializing;
    }

    /**
     * The trigger instance that monitors this aggregate to trigger a snapshot
     *
     * @return the trigger instance assigned to this aggregate instance
     */
    public SnapshotTrigger getSnapshotTrigger() {
        return snapshotTrigger;
    }

    private static class IgnoreApplyMore implements ApplyMore {

        public static final ApplyMore INSTANCE = new IgnoreApplyMore();

        @Override
        public ApplyMore andThenApply(Supplier<?> payloadOrMessageSupplier) {
            return this;
        }

        @Override
        public ApplyMore andThen(Runnable runnable) {
            return this;
        }
    }

}
