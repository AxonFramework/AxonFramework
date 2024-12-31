/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.*;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.ApplyMore;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.tracing.SpanFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of a snapshotter that uses the actual aggregate and its state to create a snapshot event. The
 * motivation is that an aggregate always contains all relevant state. Therefore, storing the aggregate itself inside an
 * event should capture all necessary information.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class AggregateSnapshotter extends AbstractSnapshotter {

    private final Map<Class<?>, AggregateFactory<?>> aggregateFactories;
    private final RepositoryProvider repositoryProvider;
    private final ParameterResolverFactory parameterResolverFactory;
    private final HandlerDefinition handlerDefinition;
    private final MessageNameResolver messageNameResolver;

    private final Map<Class<?>, AggregateModel<?>> aggregateModels = new ConcurrentHashMap<>();

    /**
     * Instantiate a {@link AggregateSnapshotter} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link EventStore}, {@link ParameterResolverFactory} and {@link HandlerDefinition} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}. The
     * {@link SpanFactory} is defaulted to a {@link org.axonframework.tracing.NoOpSpanFactory}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AggregateSnapshotter} instance
     */
    protected AggregateSnapshotter(Builder builder) {
        super(builder);
        this.aggregateFactories = new ConcurrentHashMap<>(builder.aggregateFactories);
        this.repositoryProvider = builder.repositoryProvider;
        this.parameterResolverFactory = builder.buildParameterResolverFactory();
        this.handlerDefinition = builder.buildHandlerDefinition();
        this.messageNameResolver = builder.messageNameResolver;
    }

    /**
     * Instantiate a Builder to be able to create a {@link AggregateSnapshotter}.
     * <p>
     * The {@link Executor} is defaulted to an {@link org.axonframework.common.DirectExecutor#INSTANCE} and the
     * {@link TransactionManager} defaults to a {@link org.axonframework.common.transaction.NoTransactionManager}.
     * Additionally, this Builder has convenience functions to default the {@link ParameterResolverFactory} and
     * {@link HandlerDefinition} based on instances of these available on the classpath in case these are not provided
     * (respectively {@link Builder#buildParameterResolverFactory()} and {@link Builder#buildHandlerDefinition()}). Upon
     * instantiation of a {@link AggregateSnapshotter}, it is recommended to use these function to set those fields.
     * <p>
     * The {@link EventStore} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link AggregateSnapshotter}
     * @see ClasspathParameterResolverFactory
     * @see ClasspathHandlerDefinition
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected DomainEventMessage<?> createSnapshot(Class<?> aggregateType,
                                                   String aggregateIdentifier,
                                                   DomainEventStream eventStream) {
        DomainEventMessage<?> firstEvent = eventStream.peek();
        AggregateFactory<?> aggregateFactory = getAggregateFactory(aggregateType);
        if (aggregateFactory == null) {
            throw new IllegalArgumentException(
                    "Aggregate Type is unknown in this snapshotter: " + aggregateType.getName()
            );
        }
        aggregateModels.computeIfAbsent(aggregateType,
                                        k -> AnnotatedAggregateMetaModelFactory
                                                .inspectAggregate(k, parameterResolverFactory, handlerDefinition));
        Object aggregateRoot = aggregateFactory.createAggregateRoot(aggregateIdentifier, firstEvent);
        //noinspection rawtypes,unchecked
        SnapshotAggregate<Object> aggregate = new SnapshotAggregate(aggregateRoot,
                                                                    aggregateModels.get(aggregateType),
                                                                    repositoryProvider);
        aggregate.initializeState(eventStream);
        if (aggregate.isDeleted()) {
            return null;
        }
        return new GenericDomainEventMessage<>(aggregate.type(),
                                               aggregate.identifierAsString(),
                                               aggregate.version(),
                                               messageNameResolver.resolve(aggregateType),
                                               aggregate.getAggregateRoot());
    }

    /**
     * Returns the AggregateFactory registered for the given {@code aggregateType}, or {@code null} if no such
     * AggregateFactory is known.
     * <p>
     * Subclasses may override this method to enhance how AggregateFactories are retrieved. They may choose to
     * {@link #registerAggregateFactory(AggregateFactory)} if it hasn't been found using this implementation.
     *
     * @param aggregateType The type to get the AggregateFactory for
     * @return the appropriate AggregateFactory, or {@code null} if not found
     */
    protected AggregateFactory<?> getAggregateFactory(Class<?> aggregateType) {
        return aggregateFactories.get(aggregateType);
    }

    /**
     * Registers the given {@code aggregateFactory} with this snapshotter. If a factory for this type was already
     * registered, it is overwritten with this one.
     *
     * @param aggregateFactory the AggregateFactory to register
     */
    protected void registerAggregateFactory(AggregateFactory<?> aggregateFactory) {
        aggregateFactories.put(aggregateFactory.getAggregateType(), aggregateFactory);
    }

    /**
     * Builder class to instantiate a {@link AggregateSnapshotter}.
     * <p>
     * The {@link Executor} is defaulted to an {@link org.axonframework.common.DirectExecutor#INSTANCE}, the
     * {@link TransactionManager} defaults to a {@link org.axonframework.common.transaction.NoTransactionManager} and
     * the {@link SpanFactory} defaults to a {@link org.axonframework.tracing.NoOpSpanFactory}. Additionally, this
     * Builder has convenience functions to default the {@link ParameterResolverFactory} and {@link HandlerDefinition}
     * based on instances of these available on the classpath in case these are not provided (respectively
     * {@link Builder#buildParameterResolverFactory()} and {@link Builder#buildHandlerDefinition()}). Upon instantiation
     * of a {@link AggregateSnapshotter}, it is recommended to use these function to set those fields.
     * <p>
     * The {@link EventStore} is a <b>hard requirement</b> and as such should be provided.
     *
     * @see ClasspathParameterResolverFactory
     * @see ClasspathHandlerDefinition
     */
    public static class Builder extends AbstractSnapshotter.Builder {

        private final Map<Class<?>, AggregateFactory<?>> aggregateFactories = new ConcurrentHashMap<>();
        private RepositoryProvider repositoryProvider;
        private ParameterResolverFactory parameterResolverFactory;
        private HandlerDefinition handlerDefinition;
        private MessageNameResolver messageNameResolver = new ClassBasedMessageNameResolver();

        @Override
        public Builder spanFactory(@Nonnull SnapshotterSpanFactory spanFactory) {
            super.spanFactory(spanFactory);
            return this;
        }

        @Override
        public Builder eventStore(EventStore eventStore) {
            super.eventStore(eventStore);
            return this;
        }

        @Override
        public Builder executor(Executor executor) {
            super.executor(executor);
            return this;
        }

        @Override
        public Builder transactionManager(TransactionManager transactionManager) {
            super.transactionManager(transactionManager);
            return this;
        }

        /**
         * Sets the {@code aggregateFactories} supported by this snapshotter. The {@link AggregateFactory} instances are
         * used to create the relevant Aggregate Root instance, which represent the snapshots.
         *
         * @param aggregateFactories an array of {@link AggregateFactory} instances which this snapshotter will support
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder aggregateFactories(AggregateFactory<?>... aggregateFactories) {
            return aggregateFactories(Arrays.asList(aggregateFactories));
        }

        /**
         * Sets the {@code aggregateFactories} supported by this snapshotter. The {@link AggregateFactory} instances are
         * used to create the relevant Aggregate Root instance, which represent the snapshots.
         *
         * @param aggregateFactories a {@link List} of {@link AggregateFactory} instances which this snapshotter will
         *                           support
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder aggregateFactories(List<AggregateFactory<?>> aggregateFactories) {
            assertNonNull(aggregateFactories, "AggregateFactories may not be null");
            aggregateFactories.forEach(f -> this.aggregateFactories.put(f.getAggregateType(), f));
            return this;
        }

        /**
         * Sets the {@link RepositoryProvider} provided to the snapshot aggregates this snapshotter creates for correct
         * instantiation.
         *
         * @param repositoryProvider the {@link RepositoryProvider} provided to the snapshot aggregates this snapshotter
         *                           creates for correct instantiation
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder repositoryProvider(RepositoryProvider repositoryProvider) {
            this.repositoryProvider = repositoryProvider;
            return this;
        }

        /**
         * Sets the {@link ParameterResolverFactory} used to resolve parameter values for annotated handlers in the
         * snapshot aggregate this snapshotter creates.
         *
         * @param parameterResolverFactory the {@link ParameterResolverFactory} used to resolve parameter values for
         *                                 annotated handlers in the snapshot aggregate this snapshotter creates
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder parameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
            assertNonNull(parameterResolverFactory, "ParameterResolverFactory may not be null");
            this.parameterResolverFactory = parameterResolverFactory;
            return this;
        }

        /**
         * Sets the {@link HandlerDefinition} used to create concrete handlers in the snapshot aggregate this
         * snapshotter creates.
         *
         * @param handlerDefinition the {@link HandlerDefinition} used to create concrete handlers in the snapshot
         *                          aggregate this snapshotter creates
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder handlerDefinition(HandlerDefinition handlerDefinition) {
            assertNonNull(handlerDefinition, "HandlerDefinition may not be null");
            this.handlerDefinition = handlerDefinition;
            return this;
        }

        /**
         * Sets the {@link MessageNameResolver} used to resolve the {@link QualifiedName} for snapshots.
         * If not set, a {@link ClassBasedMessageNameResolver} is used by default.
         *
         * @param messageNameResolver The {@link MessageNameResolver} providing the {@link QualifiedName} for snapshots.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder messageNameResolver(MessageNameResolver messageNameResolver) {
            assertNonNull(messageNameResolver, "MessageNameResolver may not be null");
            this.messageNameResolver = messageNameResolver;
            return this;
        }

        /**
         * Initializes a {@link AggregateSnapshotter} as specified through this Builder.
         *
         * @return a {@link AggregateSnapshotter} as specified through this Builder
         */
        public AggregateSnapshotter build() {
            return new AggregateSnapshotter(this);
        }

        /**
         * Return the set {@link ParameterResolverFactory}, or create and return it if it is {@code null}. In case it
         * has not been set yet, a ParameterResolverFactory is created by calling the
         * {@link ClasspathParameterResolverFactory#forClass(Class)} function, either providing the
         * {@link AggregateSnapshotter} type or the first {@link AggregateFactory} it's class as input.
         * <p>
         * <b>Note:</b> it is recommended to use this function when retrieving the  ParameterResolverFactory as it
         * ensure it is non-null.
         *
         * @return the set or instantiated {@link ParameterResolverFactory}
         */
        protected ParameterResolverFactory buildParameterResolverFactory() {
            if (parameterResolverFactory == null) {
                parameterResolverFactory = ClasspathParameterResolverFactory.forClass(
                        aggregateFactories.isEmpty()
                                ? AggregateSnapshotter.class
                                : aggregateFactories.values().iterator().next().getClass()
                );
            }
            return parameterResolverFactory;
        }

        /**
         * Return the set {@link HandlerDefinition}, or create and return it if it is {@code null}. In case it has not
         * been set yet, a HandlerDefinition is created by calling the
         * {@link ClasspathHandlerDefinition#forClass(Class)} function, either providing the
         * {@link AggregateSnapshotter} type or the first {@link AggregateFactory} it's class as input.
         * <p>
         * <b>Note:</b> it is recommended to use this function when retrieving the  HandlerDefinition as it
         * ensure it is non-null.
         *
         * @return the set or instantiated {@link ParameterResolverFactory}
         */
        protected HandlerDefinition buildHandlerDefinition() {
            if (handlerDefinition == null) {
                handlerDefinition = ClasspathHandlerDefinition.forClass(
                        aggregateFactories.isEmpty()
                                ? AggregateSnapshotter.class
                                : aggregateFactories.values().iterator().next().getClass()
                );
            }
            return handlerDefinition;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
        }
    }

    private static class SnapshotAggregate<T> extends EventSourcedAggregate<T> {

        private SnapshotAggregate(T aggregateRoot,
                                  AggregateModel<T> aggregateModel,
                                  RepositoryProvider repositoryProvider) {
            super(aggregateRoot, aggregateModel, null, repositoryProvider, NoSnapshotTriggerDefinition.TRIGGER);
        }

        @Override
        public Object handle(Message<?> message) {
            throw new UnsupportedOperationException("Aggregate instance is read-only");
        }

        @Override
        public <P> ApplyMore doApply(P payload, MetaData metaData) {
            return this;
        }

        @Override
        public ApplyMore andThen(Runnable runnable) {
            return this;
        }

        @Override
        public ApplyMore andThenApply(Supplier<?> payloadOrMessageSupplier) {
            return this;
        }
    }
}
