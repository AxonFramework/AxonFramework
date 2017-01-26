/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.commandhandling.model.ApplyMore;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.AnnotatedAggregate;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.messaging.MetaData;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Implementation of an {@link org.axonframework.commandhandling.model.Aggregate} that is sourced from events that have
 * been published by the aggregate.
 *
 * @param <T> The type of the aggregate root object
 */
public class EventSourcedAggregate<T> extends AnnotatedAggregate<T> {

    private final SnapshotTrigger snapshotTrigger;
    private boolean initializing = false;
    private long lastEventSequenceNumber;

    /**
     * Initializes an EventSourcedAggregate instance for the given {@code aggregateRoot}, based on the given {@code
     * inspector}, which publishes events to the given {@code eventBus}.
     *
     * @param aggregateRoot   The aggregate root instance
     * @param inspector       The inspector describing the aggregate structure
     * @param eventBus        The event bus to send generated events to
     * @param snapshotTrigger The trigger to notify of events and initialization
     * @param <T>             the aggregate root type
     * @return the initialized EventSourcedAggregate instance
     */
    public static <T> EventSourcedAggregate<T> initialize(T aggregateRoot, AggregateModel<T> inspector,
                                                          EventBus eventBus, SnapshotTrigger snapshotTrigger) {
        EventSourcedAggregate<T> aggregate =
                new EventSourcedAggregate<>(aggregateRoot, inspector, eventBus, snapshotTrigger);
        aggregate.registerWithUnitOfWork();
        return aggregate;
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
     * @throws Exception if the aggregate cannot be initialized
     */
    public static <T> EventSourcedAggregate<T> initialize(Callable<T> aggregateFactory, AggregateModel<T> inspector,
                                                          EventBus eventBus,
                                                          SnapshotTrigger snapshotTrigger) throws Exception {
        EventSourcedAggregate<T> aggregate = new EventSourcedAggregate<>(inspector, eventBus, snapshotTrigger);
        aggregate.registerWithUnitOfWork();
        aggregate.registerRoot(aggregateFactory);
        return aggregate;
    }

    /**
     * Reconstructs an EventSourcedAggregate instance with given {@code aggregateRoot}. The aggregate's sequence number
     * should be set to the given {@code seqNo} and its deleted flag to the given {@code isDeleted}.
     * <p>
     * Use this method to initialize an EventSourcedAggregate without having to replay the aggregate from events.
     *
     * @param aggregateRoot   The aggregate root instance
     * @param model           The model describing the aggregate structure
     * @param seqNo           The last event sequence number of the aggregate
     * @param isDeleted       Flag to indicate whether or not the aggregate is deleted
     * @param eventBus        The event bus to send generated events to
     * @param snapshotTrigger The trigger to notify of events and initialization
     * @param <T>             the aggregate root type
     * @return the reconstructed EventSourcedAggregate instance
     */
    public static <T> EventSourcedAggregate<T> reconstruct(T aggregateRoot, AggregateModel<T> model, long seqNo,
                                                           boolean isDeleted, EventBus eventBus,
                                                           SnapshotTrigger snapshotTrigger) {
        EventSourcedAggregate<T> aggregate = initialize(aggregateRoot, model, eventBus, snapshotTrigger);
        aggregate.lastEventSequenceNumber = seqNo;
        if (isDeleted) {
            aggregate.doMarkDeleted();
        }
        return aggregate;
    }

    /**
     * Initializes an Aggregate instance for the given {@code aggregateRoot}, based on the given {@code model}, which
     * publishes events to the given {@code eventBus}.
     *
     * @param aggregateRoot   The aggregate root instance
     * @param model           The model describing the aggregate structure
     * @param eventBus        The event store to store generated events in
     * @param snapshotTrigger The trigger to notify of events and initialization
     */
    protected EventSourcedAggregate(T aggregateRoot, AggregateModel<T> model, EventBus eventBus,
                                    SnapshotTrigger snapshotTrigger) {
        super(aggregateRoot, model, eventBus);
        this.lastEventSequenceNumber = -1;
        this.snapshotTrigger = snapshotTrigger;
    }

    /**
     * Creates a new EventSourcedAggregate instance based on the given {@code model}, which publishes events to the
     * given {@code eventBus}. This aggregate is not assigned a root instance yet.
     *
     * @param model           The model describing the aggregate structure
     * @param eventBus        The event store to store generated events in
     * @param snapshotTrigger The trigger to notify of events and initialization
     * @see #registerRoot(Callable)
     */
    protected EventSourcedAggregate(AggregateModel<T> model, EventBus eventBus, SnapshotTrigger snapshotTrigger) {
        super(model, eventBus);
        this.lastEventSequenceNumber = -1;
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
        if (msg instanceof DomainEventMessage) {
            lastEventSequenceNumber = ((DomainEventMessage) msg).getSequenceNumber();
        }
        snapshotTrigger.eventHandled(msg);
        super.publish(msg);
        if (identifierAsString() == null) {
            throw new IncompatibleAggregateException("Aggregate identifier must be non-null after applying an event. " +
                                                             "Make sure the aggregate identifier is initialized at " +
                                                             "the latest when handling the creation event.");
        }
    }

    @Override
    protected <P> DomainEventMessage<P> createMessage(P payload, MetaData metaData) {
        String id = identifierAsString();
        long seq = nextSequence();
        if (id == null) {
            Assert.state(seq == 0,
                         () -> "The aggregate identifier has not been set. It must be set at the latest by the " +
                                 "event sourcing handler of the creation event");
            return new LazyIdentifierDomainEventMessage<>(type(), seq, payload, metaData);
        }
        return new GenericDomainEventMessage<>(type(), identifierAsString(), nextSequence(), payload, metaData);
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
        return lastEventSequenceNumber < 0 ? null : lastEventSequenceNumber;
    }

    /**
     * Returns the sequence number to be used for the next event applied by this Aggregate instance. The first
     * event of an aggregate receives sequence number 0.
     *
     * @return the sequence number to be used for the next event
     */
    protected long nextSequence() {
        Long currentSequence = version();
        return currentSequence == null ? 0 : currentSequence + 1L;
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
                lastEventSequenceNumber = eventStream.getLastSequenceNumber();
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

    private class LazyIdentifierDomainEventMessage<P> extends GenericDomainEventMessage<P> {
        public LazyIdentifierDomainEventMessage(String type, long seq, P payload, MetaData metaData) {
            super(type, null, seq, payload, metaData);
        }

        @Override
        public String getAggregateIdentifier() {
            return identifierAsString();
        }

        @Override
        public GenericDomainEventMessage<P> withMetaData(Map<String, ?> newMetaData) {
            String identifier = identifierAsString();
            if (identifier != null) {
                return new GenericDomainEventMessage<>(getType(), getAggregateIdentifier(), getSequenceNumber(),
                                                       getPayload(), getMetaData(), getIdentifier(), getTimestamp());
            } else {
                return new LazyIdentifierDomainEventMessage<>(getType(), getSequenceNumber(), getPayload(),
                                                              MetaData.from(newMetaData));
            }
        }

        @Override
        public GenericDomainEventMessage<P> andMetaData(Map<String, ?> additionalMetaData) {
            String identifier = identifierAsString();
            if (identifier != null) {
                return new GenericDomainEventMessage<>(getType(), getAggregateIdentifier(), getSequenceNumber(),
                                                       getPayload(), getMetaData(), getIdentifier(), getTimestamp())
                        .andMetaData(additionalMetaData);
            } else {
                return new LazyIdentifierDomainEventMessage<>(getType(), getSequenceNumber(), getPayload(),
                                                              getMetaData().mergedWith(additionalMetaData));
            }
        }
    }
}
