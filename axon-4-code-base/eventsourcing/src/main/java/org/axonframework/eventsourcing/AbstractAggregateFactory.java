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

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;

import java.util.Optional;
import java.util.Set;

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
    private final AggregateModel<T> aggregateModel;

    /**
     * Initialize an {@link AggregateFactory} for the given {@code aggregateBaseType}.
     * <p>
     * If a first event is an instance of this {@code aggregateBaseType}, it is recognised as a snapshot event.
     * Otherwise, the subclass is asked to instantiate a new aggregate root instance based on the first event.
     *
     * @param aggregateBaseType the base type of the aggregate roots created by this instance
     */
    protected AbstractAggregateFactory(Class<T> aggregateBaseType) {
        this(AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateBaseType));
    }

    /**
     * Initialize an {@link AggregateFactory} for the given polymorphic {@code aggregateBaseType} and it's {@code
     * aggregateSubTypes}.
     * <p>
     * If a first event is an instance of this {@code aggregateBaseType}, it is recognised as a snapshot event.
     * Otherwise, the subclass is asked to instantiate a new aggregate root instance based on the first event.
     *
     * @param aggregateBaseType the base type of the aggregate roots created by this instance
     * @param aggregateSubTypes a {@link Set} of sub types of the given {@code aggregateBaseType}
     */
    protected AbstractAggregateFactory(Class<T> aggregateBaseType, Set<Class<? extends T>> aggregateSubTypes) {
        this(AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateBaseType, aggregateSubTypes));
    }

    /**
     * Initializes an {@link AggregateFactory} for the given {@code aggregateModel}.
     * <p>
     * If a first event is an instance of any aggregate root within this {@code aggregateModel}, it is recognised as a
     * snapshot event. Otherwise, the subclass is asked to instantiate a new aggregate root instance based on the first
     * event.
     *
     * @param aggregateModel the model of aggregate to be created by this factory
     */
    protected AbstractAggregateFactory(AggregateModel<T> aggregateModel) {
        //noinspection unchecked
        this.aggregateBaseType = (Class<T>) aggregateModel.entityClass();
        this.aggregateModel = aggregateModel;
    }

    /**
     * Gets the aggregate model.
     *
     * @return the aggregate model
     */
    protected AggregateModel<T> aggregateModel() {
        return aggregateModel;
    }

    @Override
    public final T createAggregateRoot(String aggregateIdentifier, DomainEventMessage<?> firstEvent) {
        return postProcessInstance(fromSnapshot(firstEvent).orElseGet(() -> doCreateAggregate(aggregateIdentifier, firstEvent)));
    }

    @SuppressWarnings("unchecked")
    private Optional<T> fromSnapshot(DomainEventMessage<?> firstEvent) {
        if (aggregateModel.types().anyMatch(firstEvent.getPayloadType()::equals)) {
            return (Optional<T>) Optional.of(firstEvent.getPayload());
        }
        return Optional.empty();
    }

    /**
     * Perform any processing that must be done on an aggregate instance that was reconstructed from a Snapshot Event.
     * Implementations may choose to modify the existing instance, or return a new instance.
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
     * Create an uninitialized Aggregate instance with the given {@code aggregateIdentifier}. The given {@code
     * firstEvent} can be used to define the requirements of the aggregate to create.
     * <p/>
     * The given {@code firstEvent} is never a snapshot event.
     *
     * @param aggregateIdentifier The identifier of the aggregate to create
     * @param firstEvent          The first event in the Event Stream of the Aggregate
     * @return The aggregate instance to initialize with the Event Stream
     */
    protected abstract T doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent);

    @Override
    public Class<T> getAggregateType() {
        return aggregateBaseType;
    }
}
