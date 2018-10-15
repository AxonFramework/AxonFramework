/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of the {@link Repository} that takes care of the dispatching of events when an aggregate is
 * persisted. All uncommitted events on an aggregate are dispatched when the aggregate is saved.
 * <p>
 * Note that this repository implementation does not take care of any locking. The underlying persistence is expected
 * to deal with concurrency. Alternatively, consider using the {@link LockingRepository}.
 *
 * @param <T> The type of aggregate this repository stores
 * @author Allard Buijze
 * @see LockingRepository
 * @since 0.1
 */
public abstract class AbstractRepository<T, A extends Aggregate<T>> implements Repository<T> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractRepository.class);

    private final String aggregatesKey = this + "_AGGREGATES";
    private final AggregateModel<T> aggregateModel;

    /**
     * Instantiate a {@link AbstractRepository} based on the fields contained in the {@link Builder}.
     * <p>
     * The provided Builder's main goal is to build an {@link AggregateModel} specifying generic {@code T} as the
     * aggregate type to be stored. All aggregates in this repository must be {@code instanceOf} this aggregate type.
     * To instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an
     * AggregateModel. Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided. An
     * {@link org.axonframework.common.AxonConfigurationException} is thrown if this criteria is not met.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractRepository} instance
     */
    protected AbstractRepository(Builder<T> builder) {
        builder.validate();
        this.aggregateModel = builder.buildAggregateModel();
    }

    @Override
    public A newInstance(Callable<T> factoryMethod) throws Exception {
        UnitOfWork<?> uow = CurrentUnitOfWork.get();
        AtomicReference<A> aggregateReference = new AtomicReference<>();
        // a constructor may apply events, and the persistence of an aggregate must take precedence over publishing its events.
        uow.onPrepareCommit(x -> prepareForCommit(aggregateReference.get()));

        A aggregate = doCreateNew(factoryMethod);
        aggregateReference.set(aggregate);
        Assert.isTrue(aggregateModel.entityClass().isAssignableFrom(aggregate.rootType()),
                      () -> "Unsuitable aggregate for this repository: wrong type");
        Map<String, A> aggregates = managedAggregates(uow);
        Assert.isTrue(aggregates.putIfAbsent(aggregate.identifierAsString(), aggregate) == null,
                      () -> "The Unit of Work already has an Aggregate with the same identifier");
        uow.onRollback(u -> aggregates.remove(aggregate.identifierAsString()));

        return aggregate;
    }

    /**
     * Creates a new aggregate instance using the given {@code factoryMethod}. Implementations should assume that this
     * method is only called if a UnitOfWork is currently active.
     *
     * @param factoryMethod The method to create the aggregate's root instance
     * @return an Aggregate instance describing the aggregate's state
     *
     * @throws Exception when the factoryMethod throws an exception
     */
    protected abstract A doCreateNew(Callable<T> factoryMethod) throws Exception;

    /**
     * @throws AggregateNotFoundException if aggregate with given id cannot be found
     * @throws RuntimeException           any exception thrown by implementing classes
     */
    @Override
    public A load(String aggregateIdentifier, Long expectedVersion) {
        UnitOfWork<?> uow = CurrentUnitOfWork.get();
        Map<String, A> aggregates = managedAggregates(uow);
        A aggregate = aggregates.computeIfAbsent(aggregateIdentifier,
                                                 s -> doLoad(aggregateIdentifier, expectedVersion));
        uow.onRollback(u -> aggregates.remove(aggregateIdentifier));
        validateOnLoad(aggregate, expectedVersion);
        prepareForCommit(aggregate);

        return aggregate;
    }

    /**
     * Returns the map of aggregates currently managed by this repository under the given unit of work. Note that the
     * repository keeps the managed aggregates in the root unit of work, to guarantee each Unit of Work works with the
     * state left by the parent unit of work.
     * <p>
     * The returns map is mutable and reflects any changes made during processing.
     *
     * @param uow The unit of work to find the managed aggregates for
     * @return a map with the aggregates managed by this repository in the given unit of work
     */
    protected Map<String, A> managedAggregates(UnitOfWork<?> uow) {
        return uow.root().getOrComputeResource(aggregatesKey, s -> new HashMap<>());
    }

    @Override
    public A load(String aggregateIdentifier) {
        return load(aggregateIdentifier, null);
    }

    /**
     * Checks the aggregate for concurrent changes. Throws a {@link ConflictingModificationException} when conflicting
     * changes have been detected.
     * <p>
     * This implementation throws a {@link ConflictingAggregateVersionException} if the expected version is not null
     * and the version number of the aggregate does not match the expected version
     *
     * @param aggregate       The loaded aggregate
     * @param expectedVersion The expected version of the aggregate
     * @throws ConflictingModificationException     when conflicting changes have been detected
     * @throws ConflictingAggregateVersionException the expected version is not {@code null}
     *                                              and the version number of the aggregate does not match the expected
     *                                              version
     */
    protected void validateOnLoad(Aggregate<T> aggregate, Long expectedVersion) {
        if (expectedVersion != null && aggregate.version() != null &&
                !expectedVersion.equals(aggregate.version())) {
            throw new ConflictingAggregateVersionException(aggregate.identifierAsString(),
                                                           expectedVersion,
                                                           aggregate.version());
        }
    }

    /**
     * Register handlers with the current Unit of Work that save or delete the given {@code aggregate} when
     * the Unit of Work is committed.
     *
     * @param aggregate The Aggregate to save or delete when the Unit of Work is committed
     */
    protected void prepareForCommit(A aggregate) {
        if (UnitOfWork.Phase.STARTED.isBefore(CurrentUnitOfWork.get().phase())) {
            doCommit(aggregate);
        } else {
            CurrentUnitOfWork.get().onPrepareCommit(u -> {
                // If the aggregate isn't "managed" anymore, it means its state was invalidated by a rollback
                doCommit(aggregate);
            });
        }
    }

    private void doCommit(A aggregate) {
        if (managedAggregates(CurrentUnitOfWork.get()).containsValue(aggregate)) {
            if (aggregate.isDeleted()) {
                doDelete(aggregate);
            } else {
                doSave(aggregate);
            }
            if (aggregate.isDeleted()) {
                postDelete(aggregate);
            } else {
                postSave(aggregate);
            }
        } else {
            reportIllegalState(aggregate);
        }
    }

    /**
     * Invoked when an the given {@code aggregate} instance has been detected that has been part of a rolled back Unit
     * of Work. This typically means that the state of the Aggregate instance has been compromised and cannot be
     * guaranteed to be correct.
     * <p>
     * This implementation throws an exception, effectively causing the unit of work to be rolled back. Subclasses that
     * can guarantee correct storage, even when specific instances are compromised, may override this method to suppress
     * this exception.
     * <p>
     * When this method is invoked, the {@link #doSave(Aggregate)}, {@link #doDelete(Aggregate)},
     * {@link #postSave(Aggregate)} and {@link #postDelete(Aggregate)} are not invoked. Implementations may choose to
     * invoke these methods.
     *
     * @param aggregate The aggregate instance with illegal state
     */
    protected void reportIllegalState(A aggregate) {
        throw new AggregateRolledBackException(aggregate.identifierAsString());
    }

    /**
     * Returns the aggregate model stored by this repository.
     *
     * @return the aggregate model stored by this repository
     */
    protected AggregateModel<T> aggregateModel() {
        return aggregateModel;
    }

    /**
     * Returns the aggregate type stored by this repository.
     *
     * @return the aggregate type stored by this repository
     */
    protected Class<? extends T> getAggregateType() {
        return aggregateModel.entityClass();
    }

    /**
     * Performs the actual saving of the aggregate.
     *
     * @param aggregate the aggregate to store
     */
    protected abstract void doSave(A aggregate);

    /**
     * Loads and initialized the aggregate with the given aggregateIdentifier.
     *
     * @param aggregateIdentifier the identifier of the aggregate to load
     * @param expectedVersion     The expected version of the aggregate to load
     * @return a fully initialized aggregate
     *
     * @throws AggregateNotFoundException if the aggregate with given identifier does not exist
     */
    protected abstract A doLoad(String aggregateIdentifier, Long expectedVersion);

    /**
     * Removes the aggregate from the repository. Typically, the repository should ensure that any calls to {@link
     * #doLoad(String, Long)} throw a {@link AggregateNotFoundException} when
     * loading a deleted aggregate.
     *
     * @param aggregate the aggregate to delete
     */
    protected abstract void doDelete(A aggregate);

    /**
     * Perform action that needs to be done directly after updating an aggregate and committing the aggregate's
     * uncommitted events. No op by default.
     *
     * @param aggregate The aggregate instance being saved
     */
    @SuppressWarnings("UnusedParameters")
    protected void postSave(A aggregate) {
        //no op by default
    }

    /**
     * Perform action that needs to be done directly after deleting an aggregate and committing the aggregate's
     * uncommitted events. No op by default.
     *
     * @param aggregate The aggregate instance being saved
     */
    @SuppressWarnings("UnusedParameters")
    protected void postDelete(A aggregate) {
        //no op by default
    }

    @Override
    public void send(Message<?> message, ScopeDescriptor scopeDescription) throws Exception {
        if (canResolve(scopeDescription)) {
            String aggregateIdentifier = ((AggregateScopeDescriptor) scopeDescription).getIdentifier().toString();
            try {
                load(aggregateIdentifier).handle(message);
            } catch (AggregateNotFoundException e) {
                logger.debug("Aggregate (with id: [{}]) cannot be loaded. Hence, message '[{}]' cannot be handled.",
                             aggregateIdentifier, message);
            }
        }
    }

    @Override
    public boolean canResolve(ScopeDescriptor scopeDescription) {
        return scopeDescription instanceof AggregateScopeDescriptor
                && Objects.equals(aggregateModel.type(), ((AggregateScopeDescriptor) scopeDescription).getType());
    }

    /**
     * Abstract Builder class to instantiate {@link AbstractRepository} implementations.
     * <p>
     * This Builder's main goal is to build an {@link AggregateModel} specifying generic {@code T} as the aggregate
     * type to be stored. All aggregates in this repository must be {@code instanceOf} this aggregate type. To
     * instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an
     * AggregateModel. Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided.
     *
     * @param <T> a generic specifying the Aggregate type contained in this {@link Repository} implementation
     */
    public static abstract class Builder<T> {

        protected final Class<T> aggregateType;
        private ParameterResolverFactory parameterResolverFactory;
        private HandlerDefinition handlerDefinition;
        private AggregateModel<T> aggregateModel;

        /**
         * Creates a builder for a Repository for given {@code aggregateType}.
         *
         * @param aggregateType the {@code aggregateType} specifying the type of aggregate this {@link Repository} will
         *                      store
         */
        protected Builder(Class<T> aggregateType) {
            this.aggregateType = aggregateType;
        }

        /**
         * Sets the {@link ParameterResolverFactory} used to resolve parameters for annotated handlers contained in the
         * Aggregate. Only used if the {@code aggregateType} approach is selected to create an {@link AggregateModel}.
         *
         * @param parameterResolverFactory a {@link ParameterResolverFactory} used to resolve parameters for annotated
         *                                 handlers contained in the Aggregate
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> parameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
            assertNonNull(parameterResolverFactory, "ParameterResolverFactory may not be null");
            this.parameterResolverFactory = parameterResolverFactory;
            return this;
        }

        /**
         * Sets the {@link HandlerDefinition} used to create concrete handlers for the given {@code aggregateType}.
         * Only used if the {@code aggregateType} approach is selected to create an {@link AggregateModel}.
         *
         * @param handlerDefinition a {@link HandlerDefinition} used to create concrete handlers for the given
         *                          {@code aggregateType}.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> handlerDefinition(HandlerDefinition handlerDefinition) {
            assertNonNull(handlerDefinition, "HandlerDefinition may not be null");
            this.handlerDefinition = handlerDefinition;
            return this;
        }

        /**
         * Sets the {@link AggregateModel} of generic type {@code T}, describing the structure of the aggregate this
         * {@link Repository} will store.
         *
         * @param aggregateModel the {@link AggregateModel} of generic type {@code T} of the aggregate this
         *                       {@link Repository} will store
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> aggregateModel(AggregateModel<T> aggregateModel) {
            assertNonNull(aggregateModel, "AggregateModel may not be null");
            this.aggregateModel = aggregateModel;
            return this;
        }

        /**
         * Instantiate the {@link AggregateModel} of generic type {@code T} describing the structure of the Aggregate
         * this {@link Repository} will store.
         *
         * @return a {@link AggregateModel} of generic type {@code T} describing the Aggregate this {@link Repository}
         * will store
         */
        protected AggregateModel<T> buildAggregateModel() {
            if (aggregateModel == null) {
                return inspectAggregateModel();
            } else {
                return aggregateModel;
            }
        }

        private AggregateModel<T> inspectAggregateModel() {
            if (parameterResolverFactory == null && handlerDefinition == null) {
                return AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateType);
            } else if (parameterResolverFactory != null && handlerDefinition == null) {
                handlerDefinition = ClasspathHandlerDefinition.forClass(aggregateType);
            }
            return AnnotatedAggregateMetaModelFactory.inspectAggregate(
                    aggregateType, parameterResolverFactory, handlerDefinition
            );
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            if (aggregateModel == null) {
                assertNonNull(
                        aggregateType,
                        "No AggregateModel is set, whilst either it or the aggregateType is a hard requirement"
                );
                return;
            }
            assertNonNull(
                    aggregateModel,
                    "No aggregateType is set, whilst either it or the AggregateModel is a hard requirement"
            );
        }
    }
}
