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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.model.ApplyMore;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.EventSourcedAggregate;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.common.PeekingIterator;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.messaging.metadata.MetaData;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Implementation of a snapshotter that uses the actual aggregate and its state to create a snapshot event. The
 * motivation is that an aggregate always contains all relevant state. Therefore, storing the aggregate itself inside
 * an event should capture all necessary information.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class AggregateSnapshotter extends AbstractSnapshotter {

    private final Map<Class<?>, AggregateFactory<?>> aggregateFactories = new ConcurrentHashMap<>();
    private final Map<Class, AggregateModel> aggregateModels = new ConcurrentHashMap<>();
    private final ParameterResolverFactory parameterResolverFactory;

    public AggregateSnapshotter() {
        this(ClasspathParameterResolverFactory.forClass(AggregateSnapshotter.class));
    }

    public AggregateSnapshotter(ParameterResolverFactory parameterResolverFactory) {
        this.parameterResolverFactory = parameterResolverFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected DomainEventMessage createSnapshot(Class<?> aggregateType,
                                                String aggregateIdentifier,
                                                Iterator<? extends DomainEventMessage<?>> eventStream) {
        PeekingIterator<? extends DomainEventMessage<?>> iterator = PeekingIterator.of(eventStream);
        DomainEventMessage firstEvent = iterator.peek();
        AggregateFactory<?> aggregateFactory = aggregateFactories.get(aggregateType);
        aggregateModels.computeIfAbsent(aggregateType, k -> ModelInspector.inspectAggregate(k, parameterResolverFactory));
        Object aggregateRoot = aggregateFactory.createAggregate(aggregateIdentifier, firstEvent);
        SnapshotAggregate<Object> aggregate = new SnapshotAggregate(aggregateRoot, aggregateModels.get(aggregateType));
        aggregate.initializeState(eventStream);
        return new GenericDomainEventMessage<>(aggregate.type(), aggregate.identifier(), aggregate.version(),
                                               aggregate.getAggregateRoot());

    }

    /**
     * Sets the aggregate factory to use. The aggregate factory is responsible for creating the aggregates stores
     * inside the snapshot events.
     *
     * @param aggregateFactories The list of aggregate factories creating the aggregates to store. May not be
     *                           <code>null</code> or contain any <code>null</code> values.
     * @throws NullPointerException if <code>aggregateFactories</code> is <code>null</code> or if contains any
     *                              <code>null</code> values.
     */
    public void setAggregateFactories(List<AggregateFactory<?>> aggregateFactories) {
        for (AggregateFactory<?> factory : aggregateFactories) {
            this.aggregateFactories.put(factory.getAggregateType(), factory);
        }
    }

    private static class SnapshotAggregate<T> extends EventSourcedAggregate<T> {
        private SnapshotAggregate(T aggregateRoot, AggregateModel<T> aggregateModel) {
            super(aggregateRoot, aggregateModel, null);
        }

        @Override
        public Object handle(CommandMessage<?> msg) {
            throw new UnsupportedOperationException("Aggregate instance is read-only");
        }

        @Override
        public <P> ApplyMore doApply(P payload, MetaData metaData) {
            return this;
        }

        @Override
        public ApplyMore andThenApply(Supplier<?> payloadOrMessageSupplier) {
            return this;
        }
    }
}
