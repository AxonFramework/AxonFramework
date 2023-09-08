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

import org.axonframework.common.caching.Cache;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.conflictresolution.ConflictResolution;
import org.axonframework.eventsourcing.conflictresolution.DefaultConflictResolver;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.LockAwareAggregate;
import org.axonframework.modelling.command.LockingRepository;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.RepositorySpanFactory;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.tracing.SpanFactory;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract repository implementation that allows easy implementation of an Event Sourcing mechanism. It will
 * automatically publish new events to the given {@link org.axonframework.eventhandling.EventBus} and delegate event
 * storage to the provided {@link org.axonframework.eventsourcing.eventstore.EventStore}.
 *
 * @param <T> The type of aggregate this repository stores
 * @author Allard Buijze
 * @see org.axonframework.eventsourcing.eventstore.EventStore
 * @since 0.1
 */
public class EventSourcingRepository<T> extends LockingRepository<T, EventSourcedAggregate<T>> {

    private final EventStore eventStore;
    private final SnapshotTriggerDefinition snapshotTriggerDefinition;
    private final AggregateFactory<T> aggregateFactory;
    private final RepositoryProvider repositoryProvider;
    private final Predicate<? super DomainEventMessage<?>> eventStreamFilter;

    /**
     * Instantiate a {@link EventSourcingRepository} based on the fields contained in the {@link Builder}.
     * <p>
     * A goal of the provided Builder is to create an {@link AggregateModel} specifying generic {@code T} as the
     * aggregate type to be stored. All aggregates in this repository must be {@code instanceOf} this aggregate type. To
     * instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an AggregateModel.
     * Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided. An
     * {@link org.axonframework.common.AxonConfigurationException} is thrown if these criteria are not met. The same
     * criteria holds for the {@link AggregateFactory}. Either the AggregateFactory can be set directly or it will be
     * instantiated internally based on the {@code aggregateType}. Hence, one of both is a hard requirement, and will
     * also result in an AxonConfigurationException if both are missing.
     * <p>
     * Additionally, the builder will assert that the {@link LockFactory}, {@link EventStore} and
     * {@link SnapshotTriggerDefinition} are not {@code null}, resulting in an AxonConfigurationException if for any of
     * these this is the case. The {@link SpanFactory} is defaulted to a
     * {@link org.axonframework.tracing.NoOpSpanFactory}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link EventSourcingRepository} instance
     */
    protected EventSourcingRepository(Builder<T> builder) {
        super(builder);
        this.eventStore = builder.eventStore;
        this.aggregateFactory = builder.buildAggregateFactory();
        this.snapshotTriggerDefinition = builder.snapshotTriggerDefinition;
        this.repositoryProvider = builder.repositoryProvider;
        this.eventStreamFilter = builder.eventStreamFilter;
    }

    /**
     * Instantiate a Builder to be able to create a {@link EventSourcingRepository} for aggregate type {@code T}. Can
     * also be used to instantiate a {@link CachingEventSourcingRepository} for aggregate type {@code T}. This Builder
     * will check whether a {@link Cache} is provided. If this holds, the {@link Builder#build()} function returns a
     * CachingEventSourcingRepository instead of an EventSourcingRepository.
     * <p>
     * The {@link LockFactory} is defaulted to an {@link org.axonframework.common.lock.PessimisticLockFactory} and the
     * {@link SnapshotTriggerDefinition} to a {@link NoSnapshotTriggerDefinition} implementation. A goal of this Builder
     * goal is to create an {@link AggregateModel} specifying generic {@code T} as the aggregate type to be stored. All
     * aggregates in this repository must be {@code instanceOf} this aggregate type. To instantiate this AggregateModel,
     * either an {@link AggregateModel} can be provided directly or an {@code aggregateType} of type {@link Class} can
     * be used. The latter will internally resolve to an AggregateModel. Thus, either the AggregateModel <b>or</b> the
     * {@code aggregateType} should be provided. The same criteria holds for the {@link AggregateFactory}. Either the
     * AggregateFactory can be set directly or it will be instantiated internally based on the {@code aggregateType}.
     * Hence, one of both is a hard requirement.
     * <p>
     * Additionally, the {@link EventStore} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link EventSourcingRepository}
     */
    public static <T> Builder<T> builder(Class<T> aggregateType) {
        return new Builder<>(aggregateType);
    }

    /**
     * Perform the actual loading of an aggregate. The necessary locks have been obtained.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param expectedVersion     The expected version of the loaded aggregate
     * @return the fully initialized aggregate
     *
     * @throws AggregateDeletedException  in case an aggregate existed in the past, but has been deleted
     * @throws AggregateNotFoundException when an aggregate with the given identifier does not exist
     */
    @Override
    protected EventSourcedAggregate<T> doLoadWithLock(String aggregateIdentifier, Long expectedVersion) {
        SnapshotTrigger trigger = snapshotTriggerDefinition.prepareTrigger(aggregateFactory.getAggregateType());
        DomainEventStream eventStream = readEvents(aggregateIdentifier);
        if (!eventStream.hasNext()) {
            throw new AggregateNotFoundException(aggregateIdentifier, "The aggregate was not found in the event store");
        }
        AggregateModel<T> model = aggregateModel();
        EventSourcedAggregate<T> aggregate = spanFactory
                .createInitializeStateSpan(model.type(), aggregateIdentifier)
                .runSupplier(() -> doLoadAggregate(aggregateIdentifier, trigger, eventStream, model));

        if (aggregate.isDeleted()) {
            throw new AggregateDeletedException(aggregateIdentifier);
        }
        return aggregate;
    }

    private EventSourcedAggregate<T> doLoadAggregate(String aggregateIdentifier,
                                                     SnapshotTrigger trigger,
                                                     DomainEventStream eventStream,
                                                     AggregateModel<T> model) {
        EventSourcedAggregate<T> loadingAggregate = EventSourcedAggregate
                .initialize(aggregateFactory.createAggregateRoot(
                                    aggregateIdentifier,
                                    eventStream.peek()),
                            model,
                            eventStore,
                            repositoryProvider,
                            trigger);
        loadingAggregate.initializeState(eventStream);
        return loadingAggregate;
    }

    /**
     * Reads the events for the given aggregateIdentifier from the eventStore. this method may be overridden to
     * add pre or postprocessing to the loading of an event stream
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @return the domain event stream for the given aggregateIdentifier, with {@link #eventStreamFilter} applied if
     *         one was configured
     */
    protected DomainEventStream readEvents(String aggregateIdentifier) {
        DomainEventStream fullStream = eventStore.readEvents(aggregateIdentifier);
        return eventStreamFilter != null ? fullStream.filter(eventStreamFilter) : fullStream;
    }

    @Override
    protected void validateOnLoad(Aggregate<T> aggregate, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion < aggregate.version()) {
            DefaultConflictResolver conflictResolver =
                    new DefaultConflictResolver(eventStore, aggregate.identifierAsString(), expectedVersion,
                                                aggregate.version());
            ConflictResolution.initialize(conflictResolver);
            CurrentUnitOfWork.get().onPrepareCommit(uow -> conflictResolver.ensureConflictsResolved());
        } else {
            super.validateOnLoad(aggregate, expectedVersion);
        }
    }

    @Override
    protected void reportIllegalState(LockAwareAggregate<T, EventSourcedAggregate<T>> aggregate) {
        // event sourcing repositories are able to reconstruct the current state
    }

    @Override
    protected EventSourcedAggregate<T> doCreateNewForLock(Callable<T> factoryMethod) throws Exception {
        return EventSourcedAggregate.initialize(factoryMethod, aggregateModel(), eventStore, repositoryProvider,
                                                snapshotTriggerDefinition.prepareTrigger(getAggregateType()));
    }

    @Override
    protected void doSaveWithLock(EventSourcedAggregate<T> aggregate) {
    }

    @Override
    protected void doDeleteWithLock(EventSourcedAggregate<T> aggregate) {
    }

    /**
     * Returns the factory used by this repository.
     *
     * @return the factory used by this repository
     */
    public AggregateFactory<T> getAggregateFactory() {
        return aggregateFactory;
    }

    /**
     * Builder class to instantiate a {@link EventSourcingRepository}. Can also be used to instantiate a
     * {@link CachingEventSourcingRepository}. This Builder will check whether a {@link Cache} is provided. If this
     * holds, the {@link Builder#build()} function returns a CachingEventSourcingRepository instead of an
     * EventSourcingRepository.
     * <p>
     * The {@link LockFactory} is defaulted to an {@link org.axonframework.common.lock.PessimisticLockFactory}, the
     * {@link SpanFactory} is defaulted to a {@link org.axonframework.tracing.NoOpSpanFactory} and the
     * {@link SnapshotTriggerDefinition} to a {@link NoSnapshotTriggerDefinition} implementation. A goal of this Builder
     * goal is to create an {@link AggregateModel} specifying generic {@code T} as the aggregate type to be stored. All
     * aggregates in this repository must be {@code instanceOf} this aggregate type. To instantiate this AggregateModel,
     * either an {@link AggregateModel} can be provided directly or an {@code aggregateType} of type {@link Class} can
     * be used. The latter will internally resolve to an AggregateModel. Thus, either the AggregateModel <b>or</b> the
     * {@code aggregateType} should be provided. The same criteria holds for the {@link AggregateFactory}. Either the
     * AggregateFactory can be set directly or it will be instantiated internally based on the {@code aggregateType}.
     * Hence, one of both is a hard requirement.
     * <p>
     * Additionally, the {@link EventStore} is a <b>hard requirement</b> and as such should be provided.
     *
     * @param <T> a generic specifying the Aggregate type contained in this {@link Repository} implementation
     */
    public static class Builder<T> extends LockingRepository.Builder<T> {

        protected EventStore eventStore;
        protected SnapshotTriggerDefinition snapshotTriggerDefinition = NoSnapshotTriggerDefinition.INSTANCE;
        private AggregateFactory<T> aggregateFactory;
        protected RepositoryProvider repositoryProvider;
        protected Cache cache;
        protected Predicate<? super DomainEventMessage<?>> eventStreamFilter;

        /**
         * Creates a builder for a Repository for given {@code aggregateType}.
         *
         * @param aggregateType the {@code aggregateType} specifying the type of aggregate this {@link Repository} will
         *                      store
         */
        protected Builder(Class<T> aggregateType) {
            super(aggregateType);
        }

        @Override
        public Builder<T> parameterResolverFactory(@Nonnull ParameterResolverFactory parameterResolverFactory) {
            super.parameterResolverFactory(parameterResolverFactory);
            return this;
        }

        @Override
        public Builder<T> handlerDefinition(@Nonnull HandlerDefinition handlerDefinition) {
            super.handlerDefinition(handlerDefinition);
            return this;
        }

        @Override
        public Builder<T> aggregateModel(@Nonnull AggregateModel<T> aggregateModel) {
            super.aggregateModel(aggregateModel);
            return this;
        }

        /**
         * {@inheritDoc}
         * If this Builder is used to instantiate a {@link CachingEventSourcingRepository}, do note that an optimistic
         * locking strategy is not compatible with a caching approach.
         */
        @Override
        public Builder<T> lockFactory(LockFactory lockFactory) {
            super.lockFactory(lockFactory);
            return this;
        }

        @Override
        public Builder<T> subtypes(@Nonnull Set<Class<? extends T>> subtypes) {
            super.subtypes(subtypes);
            return this;
        }

        @Override
        public Builder<T> subtype(@Nonnull Class<? extends T> subtype) {
            super.subtype(subtype);
            return this;
        }

        @Override
        public Builder<T> spanFactory(SpanFactory spanFactory) {
            super.spanFactory(spanFactory);
            return this;
        }

        @Override
        public Builder<T> spanFactory(RepositorySpanFactory spanFactory) {
            super.spanFactory(spanFactory);
            return this;
        }


        /**
         * Sets the {@link EventStore} that holds the event stream this repository needs to event source an Aggregate.
         *
         * @param eventStore an {@link EventStore} that holds the event stream this repository needs to event source
         *                   an Aggregate
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> eventStore(EventStore eventStore) {
            assertNonNull(eventStore, "EventStore may not be null");
            this.eventStore = eventStore;
            return this;
        }

        /**
         * Sets the {@link SnapshotTriggerDefinition} specifying when to trigger a snapshot for an Aggregate contained
         * in this repository.
         *
         * @param snapshotTriggerDefinition a {@link SnapshotTriggerDefinition} specifying when to trigger a snapshot
         *                                  for an Aggregate contained in this repository
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> snapshotTriggerDefinition(SnapshotTriggerDefinition snapshotTriggerDefinition) {
            assertNonNull(snapshotTriggerDefinition, "SnapshotTriggerDefinition may not be null");
            this.snapshotTriggerDefinition = snapshotTriggerDefinition;
            return this;
        }

        /**
         * Sets the {@link AggregateFactory} used to create new Aggregate instances.
         *
         * @param aggregateFactory the {@link AggregateFactory} used to create new Aggregate instances
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> aggregateFactory(AggregateFactory<T> aggregateFactory) {
            assertNonNull(aggregateFactory, "AggregateFactory may not be null");
            this.aggregateFactory = aggregateFactory;
            return this;
        }

        /**
         * Sets the {@link RepositoryProvider} which services repositories for specific aggregate types.
         *
         * @param repositoryProvider a {@link RepositoryProvider} servicing repositories for specific aggregate types
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> repositoryProvider(RepositoryProvider repositoryProvider) {
            this.repositoryProvider = repositoryProvider;
            return this;
        }

        /**
         * Sets the {@link Cache} which services repositories for specific aggregate types.
         *
         * @param cache a {@link Cache} servicing repositories for specific aggregate types
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> cache(Cache cache) {
            this.cache = cache;
            return this;
        }

        /**
         * Sets the {@link Predicate} used to filter events when reading from the EventStore. By default, all events
         * with the Aggregate identifier passed to {@link EventSourcingRepository#readEvents(String)} are returned.
         * Calls to {@link #filterByAggregateType()} will overwrite this configuration and vice versa.
         *
         * @param filter a {@link Predicate} that may return false to discard events.
         */
        public Builder<T> eventStreamFilter(Predicate<? super DomainEventMessage<?>> filter) {
            this.eventStreamFilter = filter;
            return this;
        }

        /**
         * Configures a filter that rejects events with a different Aggregate type than the one specified by this
         * Repository's {@link AggregateModel}. This may be used to enable multiple Aggregate types to share overlapping
         * Aggregate identifiers. Calls to {@link #eventStreamFilter(Predicate)} will overwrite this configuration and
         * vice versa.
         *
         * <p>If the caller supplies an explicit {@link AggregateModel} to this Builder, that must be done before
         * calling this method.
         */
        public Builder<T> filterByAggregateType() {
            final String aggregateType = buildAggregateModel().type();
            return eventStreamFilter(event -> aggregateType.equals(event.getType()));
        }

        /**
         * Initializes a {@link EventSourcingRepository} or {@link CachingEventSourcingRepository} as specified through
         * this Builder. Will return a CachingEventSourcingRepository if {@link #cache(Cache)} has been set. Otherwise
         * builds a regular EventSourcingRepository
         *
         * @param <R> a generic extending {@link EventSourcingRepository}, so allowing both an EventSourcingRepository
         *            and {@link CachingEventSourcingRepository} return type
         * @return a {@link EventSourcingRepository} or {@link CachingEventSourcingRepository} (if {@link #cache(Cache)}
         * has been set) as specified through this Builder
         */
        @SuppressWarnings("unchecked")
        public <R extends EventSourcingRepository<T>> R build() {
            return cache != null
                    ? (R) new CachingEventSourcingRepository<>(this)
                    : (R) new EventSourcingRepository<>(this);
        }

        /**
         * Instantiate the {@link AggregateFactory} of generic type {@code T} for the Aggregate this
         * {@link EventSourcingRepository} will instantiate based on an event stream.
         *
         * @return a {@link AggregateFactory} of generic type {@code T} for the Aggregate this
         * {@link EventSourcingRepository} will instantiate based on an event stream
         */
        private AggregateFactory<T> buildAggregateFactory() {
            if (aggregateFactory == null) {
                return new GenericAggregateFactory<>(buildAggregateModel());
            } else {
                return aggregateFactory;
            }
        }

        @Override
        protected void validate() {
            super.validate();
            assertNonNull(eventStore, "The EventStore is a hard requirement and should be provided");
            if (aggregateFactory == null) {
                assertNonNull(
                        aggregateType,
                        "No AggregateFactory is set, whilst either it or the aggregateType is a hard requirement"
                );
                return;
            }
            assertNonNull(
                    aggregateFactory,
                    "No aggregateType is set, whilst either it or the AggregateFactory is a hard requirement"
            );
        }
    }
}
