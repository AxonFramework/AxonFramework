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

/**
 * Abstract AggregateFactory implementation that is aware of snapshot events. If an incoming event is not a snapshot
 * event, creation is delegated to the subclass.
 *
 * @param <T> The type of Aggregate created by this factory
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractAggregateFactory<T> implements AggregateFactory<T> {

    private final Class<T> aggregateBaseType;

    /**
     * Initialize an aggregateFactory for the given {@code aggregateBaseType}. If a first event is and instance of this
     * {@code aggregateBaseType}, it is recognised as a snapshot event. Otherwise, the subclass is asked to instantiate
     * a new aggregate root instance based on the first event.
     *
     * @param aggregateBaseType The base type of the aggregate roots created by this instance.
     */
    protected AbstractAggregateFactory(Class<T> aggregateBaseType) {
        this.aggregateBaseType = aggregateBaseType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final T createAggregateRoot(String aggregateIdentifier, DomainEventMessage<?> firstEvent) {
        T aggregate;
        if (aggregateBaseType.isAssignableFrom(firstEvent.getPayloadType())) {
            aggregate = (T) firstEvent.getPayload();
        } else {
            aggregate = doCreateAggregate(aggregateIdentifier, firstEvent);
        }
        return postProcessInstance(aggregate);
    }

    /**
     * Perform any processing that must be done on an aggregate instance that was reconstructed from a Snapshot
     * Event. Implementations may choose to modify the existing instance, or return a new instance.
     * <p/>
     * This method can be safely overridden. This implementation does nothing.
     *
     * @param aggregate The aggregate to post-process.
     * @return The aggregate to initialize with the Event Stream
     */
    protected T postProcessInstance(T aggregate) {
        return aggregate;
    }

    /**
     * Create an uninitialized Aggregate instance with the given {@code aggregateIdentifier}. The given
     * {@code firstEvent} can be used to define the requirements of the aggregate to create.
     * <p/>
     * The given {@code firstEvent} is never a snapshot event.
     *
     * @param aggregateIdentifier The identifier of the aggregate to create
     * @param firstEvent          The first event in the Event Stream of the Aggregate
     * @return The aggregate instance to initialize with the Event Stream
     */
    protected abstract T doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent);

    public Class<T> getAggregateType() {
        return aggregateBaseType;
    }
}
