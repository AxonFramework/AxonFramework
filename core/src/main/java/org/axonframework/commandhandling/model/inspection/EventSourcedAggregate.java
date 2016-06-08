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

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.commandhandling.model.ApplyMore;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.metadata.MetaData;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class EventSourcedAggregate<T> extends AnnotatedAggregate<T> {

    private boolean initializing = false;
    private long lastEventSequenceNumber;

    public static <T> EventSourcedAggregate<T> initialize(T aggregateRoot, AggregateModel<T> inspector,
                                                          EventStore eventStore) {
        EventSourcedAggregate<T> aggregate = new EventSourcedAggregate<T>(aggregateRoot, inspector, eventStore);
        aggregate.registerWithUnitOfWork();
        return aggregate;
    }

    public static <T> EventSourcedAggregate<T> initialize(Callable<T> aggregateFactory, AggregateModel<T> inspector,
                                                          EventStore eventStore) throws Exception {
        EventSourcedAggregate<T> aggregate = new EventSourcedAggregate<T>(inspector, eventStore);
        aggregate.registerWithUnitOfWork();
        aggregate.registerRoot(aggregateFactory);
        return aggregate;
    }

    public static <T> EventSourcedAggregate<T> reconstruct(T aggregateRoot, AggregateModel<T> model, long seqNo,
                                                           boolean isDeleted, EventStore eventStore) {
        EventSourcedAggregate<T> aggregate = initialize(aggregateRoot, model, eventStore);
        aggregate.lastEventSequenceNumber = seqNo;
        if (isDeleted) {
            aggregate.doMarkDeleted();
        }
        return aggregate;
    }

    /**
     * Initializes an Aggregate instance for the given {@code aggregateRoot}, based on the given {@code model}, which
     * publishes events to the given {@code eventBus} and stores events in the given {@code eventStore}.
     *
     * @param aggregateRoot The aggregate root instance
     * @param model         The model describing the aggregate structure
     * @param eventStore    The event store to store generated events in
     */
    protected EventSourcedAggregate(T aggregateRoot, AggregateModel<T> model, EventStore eventStore) {
        super(aggregateRoot, model, eventStore);
        this.lastEventSequenceNumber = -1;
    }

    /**
     * Creates a new EventSourcedAggregate instance based on the given {@code model}, which publishes events to the given
     * {@code eventBus} and stores events in the given {@code eventStore}.
     * This aggregate is not assigned a root instance yet.
     *
     * @param model      The model describing the aggregate structure
     * @param eventStore The event store to store generated events in
     * @see #registerRoot(Callable)
     */
    protected EventSourcedAggregate(AggregateModel<T> model, EventBus eventStore) {
        super(model, eventStore);
        this.lastEventSequenceNumber = -1;
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
        super.publish(msg);
    }

    @Override
    protected <P> GenericDomainEventMessage<P> createMessage(P payload, MetaData metaData) {
        String id = identifier();
        long seq = nextSequence();
        if (id == null) {
            Assert.state(seq == 0, "The aggregate identifier has not been set. It must be set at the latest by the " +
                    "event sourcing handler of the creation event");
            return new LazyIdentifierDomainEventMessage<>(type(), seq, payload, metaData);
        }
        return new GenericDomainEventMessage<>(type(), identifier(), nextSequence(), payload, metaData);
    }

    @Override
    protected void publishOnEventBus(EventMessage<?> msg) {
        if (!initializing) {
            super.publishOnEventBus(msg);
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
    public void initializeState(Iterator<? extends DomainEventMessage<?>> eventStream) {
        this.initializing = true;
        try {
            eventStream.forEachRemaining(this::publish);
        } finally {
            this.initializing = false;
        }
    }

    private static class IgnoreApplyMore implements ApplyMore {

        public static final ApplyMore INSTANCE = new IgnoreApplyMore();

        @Override
        public ApplyMore andThenApply(Supplier<?> payloadOrMessageSupplier) {
            return this;
        }
    }

    private class LazyIdentifierDomainEventMessage<P> extends GenericDomainEventMessage<P> {
        public LazyIdentifierDomainEventMessage(String type, long seq, P payload, MetaData metaData) {
            super(type, null, seq, payload, metaData);
        }

        @Override
        public String getAggregateIdentifier() {
            return identifier();
        }

        @Override
        public GenericDomainEventMessage<P> withMetaData(Map<String, ?> newMetaData) {
            String identifier = identifier();
            if (identifier != null) {
                return new GenericDomainEventMessage<>(getType(), getAggregateIdentifier(), getSequenceNumber(),
                                                       new GenericEventMessage<>(identifier,
                                                                                 getPayload(), getMetaData(), getTimestamp()));
            } else {
                return new LazyIdentifierDomainEventMessage<>(getType(), getSequenceNumber(), getPayload(),
                                                              MetaData.from(newMetaData));
            }
        }

        @Override
        public GenericDomainEventMessage<P> andMetaData(Map<String, ?> additionalMetaData) {
            String identifier = identifier();
            if (identifier != null) {
                return new GenericDomainEventMessage<>(getType(), getAggregateIdentifier(), getSequenceNumber(),
                                                       new GenericEventMessage<>(identifier,
                                                                                 getPayload(), getMetaData(), getTimestamp()));
            } else {
                return new LazyIdentifierDomainEventMessage<>(getType(), getSequenceNumber(), getPayload(),
                                                              getMetaData().mergedWith(additionalMetaData));
            }
        }
    }
}
