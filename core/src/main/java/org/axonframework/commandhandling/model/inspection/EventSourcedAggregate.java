/*
 * Copyright (c) 2010-2015. Axon Framework
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
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.DomainEventStream;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventstore.EventStore;
import org.axonframework.messaging.metadata.MetaData;

import java.util.function.Supplier;

public class EventSourcedAggregate<T> extends AnnotatedAggregate<T> {

    private final EventStore eventStore;
    private boolean initializing = false;
    private long lastEventSequenceNumber;

    public static <T> EventSourcedAggregate<T> initialize(T aggregateRoot, AggregateModel<T> inspector,
                                                          EventBus eventBus, EventStore eventStore) {
        EventSourcedAggregate<T> aggregate = new EventSourcedAggregate<T>(aggregateRoot, inspector,
                                                                          eventBus, eventStore);
        aggregate.registerWithUnitOfWork();
        return aggregate;
    }

    public static <T> EventSourcedAggregate<T> initialize(Supplier<T> aggregateFactory, AggregateModel<T> inspector,
                                                          EventBus eventBus, EventStore eventStore) {
        EventSourcedAggregate<T> aggregate = new EventSourcedAggregate<T>(inspector, eventBus, eventStore);
        aggregate.registerWithUnitOfWork();
        aggregate.registerRoot(aggregateFactory);
        return aggregate;
    }

    public static <T> EventSourcedAggregate<T> reconstruct(T aggregateRoot, AggregateModel<T> model,
                                                           long seqNo, boolean isDeleted,
                                                           EventBus eventBus, EventStore eventStore) {
        EventSourcedAggregate<T> aggregate = initialize(aggregateRoot, model, eventBus, eventStore);
        aggregate.lastEventSequenceNumber = seqNo;
        if (isDeleted) {
            aggregate.doMarkDeleted();
        }
        return aggregate;
    }

    protected EventSourcedAggregate(T aggregateRoot, AggregateModel<T> inspector, EventBus eventBus, EventStore eventStore) {
        super(aggregateRoot, inspector, eventBus);
        this.eventStore = eventStore;
        this.lastEventSequenceNumber = -1;
    }

    protected EventSourcedAggregate(AggregateModel<T> inspector, EventBus eventBus, EventStore eventStore) {
        super(inspector, eventBus);
        this.eventStore = eventStore;
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
        // TODO: When identifier is not set (yet), and nextSequence() returns 0, then allow for lazy initialized aggregate identifier
        String id = identifier();
        long seq = nextSequence();
        if (id == null) {
            Assert.state(seq == 0, "The aggregate identifier has not been set. It must be set at the latest by the " +
                    "event sourcing handler of the creation event");
            return new GenericDomainEventMessage<P>(null, seq, payload, metaData) {
                @Override
                public String getAggregateIdentifier() {
                    return identifier();
                }
            };
        }
        return new GenericDomainEventMessage<>(identifier(), nextSequence(), payload, metaData);
    }

    @Override
    protected void publishOnEventBus(EventMessage<?> msg) {
        if (!initializing) {
            if (msg instanceof DomainEventMessage<?>) {
                eventStore.appendEvents((DomainEventMessage<?>) msg);
            }
            super.publishOnEventBus(msg);
        }
    }

    @Override
    public Long version() {
        return lastEventSequenceNumber < 0 ? null : lastEventSequenceNumber;
    }

    protected long nextSequence() {
        Long currentSequence = version();
        return currentSequence == null ? 0 : currentSequence + 1L;
    }

    public void initializeState(DomainEventStream eventStream) {
        this.initializing = true;
        try {
            while (eventStream.hasNext()) {
                publish(eventStream.next());
            }
        } finally {
            this.initializing = false;
        }
    }

    public boolean initializing() {
        return initializing;
    }

    private static class IgnoreApplyMore implements ApplyMore {

        public static final ApplyMore INSTANCE = new IgnoreApplyMore();

        @Override
        public ApplyMore andThenApply(Supplier<?> payloadOrMessageSupplier) {
            return this;
        }
    }
}
