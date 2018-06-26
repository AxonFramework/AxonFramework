/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.spring.eventsourcing;

import org.axonframework.commandhandling.model.RepositoryProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Snapshotter implementation that uses the AggregateRoot as state for the snapshot. Unlike the
 * {@link AggregateSnapshotter}, this implementation lazily retrieves AggregateFactories from the Application
 * Context when snapshot requests are made.
 * <p>
 * Instead of configuring directly, consider using the {@link SpringAggregateSnapshotterFactoryBean}, which
 * configures this class using values available in the application context.
 *
 * @see SpringAggregateSnapshotterFactoryBean
 */
public class SpringAggregateSnapshotter extends AggregateSnapshotter implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * Initializes a snapshotter. The given Aggregate Factories are lazily retrieved from the application context.
     *
     * @param eventStore               The Event Store to store snapshots in
     * @param parameterResolverFactory The parameterResolverFactory used to reconstruct aggregates
     * @param executor                 The executor that processes the snapshotting requests
     * @param txManager                The transaction manager to manage the persistence transactions with
     */
    public SpringAggregateSnapshotter(EventStore eventStore, ParameterResolverFactory parameterResolverFactory,
                                      Executor executor, TransactionManager txManager) {
        super(eventStore, Collections.emptyList(), parameterResolverFactory, executor, txManager);
    }

    /**
     * Initializes a snapshotter using the ParameterResolverFactory instances available on the classpath.
     * The given Aggregate Factories are lazily retrieved from the application context.
     *
     * @param eventStore               The Event Store to store snapshots in
     * @param parameterResolverFactory The parameterResolverFactory used to reconstruct aggregates
     * @param handlerDefinition        The handler definition used to create concrete handlers
     * @param executor                 The executor that processes the snapshotting requests
     * @param txManager                The transaction manager to manage the persistence transactions with
     * @param repositoryProvider       Provides repositories for specific aggregate types
     */
    public SpringAggregateSnapshotter(EventStore eventStore, ParameterResolverFactory parameterResolverFactory,
                                      HandlerDefinition handlerDefinition, Executor executor,
                                      TransactionManager txManager, RepositoryProvider repositoryProvider) {
        super(eventStore,
              Collections.emptyList(),
              parameterResolverFactory,
              handlerDefinition,
              executor,
              txManager,
              repositoryProvider);
    }

    @Override
    protected AggregateFactory<?> getAggregateFactory(Class<?> aggregateType) {
        AggregateFactory<?> aggregateFactory = super.getAggregateFactory(aggregateType);
        if (aggregateFactory == null) {
            Optional<AggregateFactory> factory = applicationContext.getBeansOfType(AggregateFactory.class)
                    .values().stream()
                    .filter(af -> Objects.equals(af.getAggregateType(), aggregateType))
                    .findFirst();
            if (!factory.isPresent()) {
                factory = applicationContext.getBeansOfType(EventSourcingRepository.class)
                                            .values().stream()
                                            .map((Function<EventSourcingRepository, AggregateFactory>) EventSourcingRepository::getAggregateFactory)
                                            .filter(af -> Objects.equals(af.getAggregateType(), aggregateType))
                                            .findFirst();
                if (factory.isPresent()) {
                    aggregateFactory = factory.get();
                    registerAggregateFactory(aggregateFactory);
                }
            }

            if (factory.isPresent()) {
                aggregateFactory = factory.get();
                registerAggregateFactory(aggregateFactory);
            }
        }
        return aggregateFactory;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
