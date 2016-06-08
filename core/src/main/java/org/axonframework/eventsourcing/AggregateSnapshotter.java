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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.model.ApplyMore;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.EventSourcedAggregate;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.interceptors.TransactionManager;
import org.axonframework.messaging.metadata.MetaData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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

    /**
     * Initializes a snapshotter using the ParameterResolverFactory instances available on the classpath.
     * The given {@code aggregateFactories} are used to instantiate the relevant Aggregate Root instances,
     * which represent the snapshots.
     *
     * @param eventStorageEngine         The Event Store to store snapshots in
     * @param aggregateFactories The factories for the aggregates supported by this snapshotter.
     * @see ClasspathParameterResolverFactory
     */
    public AggregateSnapshotter(EventStorageEngine eventStorageEngine, List<AggregateFactory<?>> aggregateFactories) {
        this(eventStorageEngine, aggregateFactories, ClasspathParameterResolverFactory.forClass(AggregateSnapshotter.class));
    }

    /**
     * Initializes a snapshotter using the given {@code parameterResolverFactory}. The given {@code aggregateFactories}
     * are used to instantiate the relevant Aggregate Root instances, which represent the snapshots. Snapshots are
     * stores in the given {@code eventStore}.
     *
     * @param eventStorageEngine               The Event Store to store snapshots in
     * @param aggregateFactories       The factories for the aggregates supported by this snapshotter.
     * @param parameterResolverFactory The ParameterResolverFactory instance to resolve parameter values for annotated
     *                                 handlers with
     */
    public AggregateSnapshotter(EventStorageEngine eventStorageEngine, List<AggregateFactory<?>> aggregateFactories,
                                ParameterResolverFactory parameterResolverFactory) {
        super(eventStorageEngine);
        aggregateFactories.forEach(f -> this.aggregateFactories.put(f.getAggregateType(), f));
        this.parameterResolverFactory = parameterResolverFactory;
    }

    /**
     * Initializes a snapshotter that stores snapshots using the given {@code executor}.
     *
     * @param eventStore               The Event Store to store snapshots in
     * @param aggregateFactories       The factories for the aggregates supported by this snapshotter.
     * @param parameterResolverFactory The ParameterResolverFactory instance to resolve parameter values for annotated
     *                                 handlers with
     * @param executor                 The executor to process the actual snapshot creation with
     * @param transactionManager       The transaction manager to handle the transactions around the snapshot creation
     *                                 process with
     */
    public AggregateSnapshotter(EventStorageEngine eventStore, List<AggregateFactory<?>> aggregateFactories,
                                ParameterResolverFactory parameterResolverFactory, Executor executor,
                                TransactionManager transactionManager) {
        super(eventStore, executor, transactionManager);
        aggregateFactories.forEach(f -> this.aggregateFactories.put(f.getAggregateType(), f));
        this.parameterResolverFactory = parameterResolverFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected DomainEventMessage createSnapshot(Class<?> aggregateType,
                                                String aggregateIdentifier,
                                                DomainEventStream eventStream) {
        DomainEventMessage firstEvent = eventStream.peek();
        AggregateFactory<?> aggregateFactory = aggregateFactories.get(aggregateType);
        aggregateModels.computeIfAbsent(aggregateType, k -> ModelInspector.inspectAggregate(k, parameterResolverFactory));
        Object aggregateRoot = aggregateFactory.createAggregateRoot(aggregateIdentifier, firstEvent);
        SnapshotAggregate<Object> aggregate = new SnapshotAggregate(aggregateRoot, aggregateModels.get(aggregateType));
        aggregate.initializeState(eventStream);
        return new GenericDomainEventMessage<>(aggregate.type(), aggregate.identifier(), aggregate.version(),
                                               aggregate.getAggregateRoot());

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
