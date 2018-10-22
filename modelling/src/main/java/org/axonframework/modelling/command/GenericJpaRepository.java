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
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.NullLockFactory;
import org.axonframework.eventhandling.DomainEventSequenceAware;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Generic repository implementation that stores JPA annotated aggregates. These aggregates must have the proper JPA
 * Annotations.
 * <p/>
 * Optionally, the repository may be configured with a locking scheme. The repository will always force optimistic
 * locking in the backing data store. The optional lock in the repository is in addition to this optimistic lock. Note
 * that locks on this repository will not be shared with other repository instances.
 * <p/>
 * When this repository is requested to persist changes to an aggregate, it will also flush the EntityManager, to
 * enforce checking of database constraints and optimistic locks.
 *
 * @param <T> The type of aggregate the repository provides access to
 * @author Allard Buijze
 * @since 0.7
 */
public class GenericJpaRepository<T> extends LockingRepository<T, AnnotatedAggregate<T>> {

    private final EntityManagerProvider entityManagerProvider;
    private final EventBus eventBus;
    private final RepositoryProvider repositoryProvider;
    private final Function<String, ?> identifierConverter;

    private boolean forceFlushOnSave = true;

    /**
     * Instantiate a Builder to be able to create a {@link GenericJpaRepository} for aggregate type {@code T}.
     * <p>
     * The {@link LockFactory} is defaulted to an {@link NullLockFactory}, thus providing no additional locking, and
     * the {@code identifierConverter} to {@link Function#identity()}.
     * A goal of this Builder goal is to create an {@link AggregateModel} specifying generic {@code T} as the aggregate
     * type to be stored. All aggregates in this repository must be {@code instanceOf} this aggregate type. To
     * instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an AggregateModel.
     * Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided.
     * <p>
     * Additionally, the {@link EntityManagerProvider} and {@link EventBus}  are <b>hard requirements</b> and as such
     * should be provided.
     *
     * @param <T>           The type of aggregate to build the repository for
     * @param aggregateType The type of aggregate to build the repository for
     * @return a Builder to be able to create a {@link GenericJpaRepository}
     */
    public static <T> Builder<T> builder(Class<T> aggregateType) {
        return new Builder<>(aggregateType);
    }

    /**
     * Instantiate a {@link GenericJpaRepository} based on the fields contained in the {@link Builder}.
     * <p>
     * A goal of the provided Builder is to create an {@link AggregateModel} specifying generic {@code T} as the
     * aggregate type to be stored. All aggregates in this repository must be {@code instanceOf} this aggregate type.
     * To instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an
     * AggregateModel. Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided. An
     * {@link org.axonframework.common.AxonConfigurationException} is thrown if this criteria is not met.
     * <p>
     * Additionally will assert that the {@link LockFactory}, {@link EntityManagerProvider}, {@link EventBus} and
     * {@code identifierConverter} are not {@code null}, resulting in an AxonConfigurationException if for any of these
     * this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link GenericJpaRepository} instance
     */
    protected GenericJpaRepository(Builder<T> builder) {
        super(builder);
        this.entityManagerProvider = builder.entityManagerProvider;
        this.eventBus = builder.eventBus;
        this.identifierConverter = builder.identifierConverter;
        this.repositoryProvider = builder.repositoryProvider;
    }

    @Override
    protected AnnotatedAggregate<T> doLoadWithLock(String aggregateIdentifier, Long expectedVersion) {
        T aggregateRoot = entityManagerProvider.getEntityManager().find(getAggregateType(),
                                                                        identifierConverter.apply(aggregateIdentifier),
                                                                        LockModeType.PESSIMISTIC_WRITE);
        if (aggregateRoot == null) {
            throw new AggregateNotFoundException(aggregateIdentifier,
                                                 format("Aggregate [%s] with identifier [%s] not found",
                                                        getAggregateType().getSimpleName(), aggregateIdentifier));
        }
        AnnotatedAggregate<T> aggregate = AnnotatedAggregate.initialize(aggregateRoot,
                                                                        aggregateModel(),
                                                                        eventBus,
                                                                        repositoryProvider);
        if (eventBus instanceof DomainEventSequenceAware) {
            Optional<Long> sequenceNumber =
                    ((DomainEventSequenceAware) eventBus).lastSequenceNumberFor(aggregateIdentifier);
            sequenceNumber.ifPresent(aggregate::initSequence);
        }
        return aggregate;
    }

    @Override
    protected AnnotatedAggregate<T> doCreateNewForLock(Callable<T> factoryMethod) throws Exception {
        // generate sequence numbers in events when using an Event Store
        return AnnotatedAggregate.initialize(factoryMethod,
                                             aggregateModel(),
                                             eventBus,
                                             repositoryProvider,
                                             eventBus instanceof DomainEventSequenceAware);
    }

    @Override
    protected void doSaveWithLock(AnnotatedAggregate<T> aggregate) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        entityManager.persist(aggregate.getAggregateRoot());
        if (forceFlushOnSave) {
            entityManager.flush();
        }
    }

    @Override
    protected void doDeleteWithLock(AnnotatedAggregate<T> aggregate) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        entityManager.remove(aggregate.getAggregateRoot());
        if (forceFlushOnSave) {
            entityManager.flush();
        }
    }

    /**
     * Indicates whether the EntityManager's state should be flushed each time an aggregate is saved. Defaults to
     * {@code true}.
     * <p/>
     * Flushing the EntityManager will force JPA to send state changes to the database. Any key violations and failing
     * optimistic locks will be identified in an early stage.
     *
     * @param forceFlushOnSave whether or not to flush the EntityManager after each save. Defaults to {@code true}.
     * @see javax.persistence.EntityManager#flush()
     */
    public void setForceFlushOnSave(boolean forceFlushOnSave) {
        this.forceFlushOnSave = forceFlushOnSave;
    }

    /**
     * Builder class to instantiate a {@link GenericJpaRepository} for aggregate type {@code T}.
     * <p>
     * The {@link LockFactory} is defaulted to an {@link NullLockFactory}, thus providing no additional locking, and
     * the {@code identifierConverter} to {@link Function#identity()}.
     * A goal of this Builder goal is to create an {@link AggregateModel} specifying generic {@code T} as the aggregate
     * type to be stored. All aggregates in this repository must be {@code instanceOf} this aggregate type. To
     * instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an AggregateModel.
     * Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided.
     * <p>
     * Additionally, the {@link EntityManagerProvider} and {@link EventBus}  are <b>hard requirements</b> and as such
     * should be provided.
     *
     * @param <T> a generic specifying the Aggregate type contained in this {@link Repository} implementation
     */
    public static class Builder<T> extends LockingRepository.Builder<T> {

        private EntityManagerProvider entityManagerProvider;
        private EventBus eventBus;
        private RepositoryProvider repositoryProvider;
        private Function<String, ?> identifierConverter = Function.identity();

        /**
         * Creates a builder for a Repository for given {@code aggregateType}.
         *
         * @param aggregateType the {@code aggregateType} specifying the type of aggregate this {@link Repository} will
         *                      store
         */
        protected Builder(Class<T> aggregateType) {
            super(aggregateType);
            super.lockFactory(NullLockFactory.INSTANCE);
        }

        @Override
        public Builder<T> parameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
            super.parameterResolverFactory(parameterResolverFactory);
            return this;
        }

        @Override
        public Builder<T> handlerDefinition(HandlerDefinition handlerDefinition) {
            super.handlerDefinition(handlerDefinition);
            return this;
        }

        @Override
        public Builder<T> aggregateModel(AggregateModel<T> aggregateModel) {
            super.aggregateModel(aggregateModel);
            return this;
        }

        @Override
        public Builder<T> lockFactory(LockFactory lockFactory) {
            super.lockFactory(lockFactory);
            return this;
        }

        /**
         * Sets the {@link EntityManagerProvider} which provides the {@link EntityManager} instance for this repository.
         *
         * @param entityManagerProvider a {@link EntityManagerProvider} which provides the {@link EntityManager}
         *                              instance for this repository
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> entityManagerProvider(EntityManagerProvider entityManagerProvider) {
            assertNonNull(entityManagerProvider, "EntityManagerProvider may not be null");
            this.entityManagerProvider = entityManagerProvider;
            return this;
        }

        /**
         * Sets the {@link EventBus} to which events are published.
         *
         * @param eventBus an {@link EventBus} to which events are published
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> eventBus(EventBus eventBus) {
            assertNonNull(eventBus, "EventBus may not be null");
            this.eventBus = eventBus;
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
         * Sets the {@link Function} which converts a {@link String} based identifier to the Identifier object used in
         * the Entity.
         *
         * @param identifierConverter a {@link Function} of input type {@link String} and return type {@code ?} which
         *                            converts the String based identifier to the Identifier object used in the Entity
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> identifierConverter(Function<String, ?> identifierConverter) {
            assertNonNull(identifierConverter, "The identifierConverter may not be null");
            this.identifierConverter = identifierConverter;
            return this;
        }

        /**
         * Initializes a {@link GenericJpaRepository} as specified through this Builder.
         *
         * @return a {@link GenericJpaRepository} as specified through this Builder
         */
        public GenericJpaRepository<T> build() {
            return new GenericJpaRepository<>(this);
        }

        @Override
        protected void validate() {
            super.validate();
            assertNonNull(entityManagerProvider,
                          "The EntityManagerProvider is a hard requirement and should be provided");
            assertNonNull(eventBus, "The EventBus is a hard requirement and should be provided");
        }
    }
}
