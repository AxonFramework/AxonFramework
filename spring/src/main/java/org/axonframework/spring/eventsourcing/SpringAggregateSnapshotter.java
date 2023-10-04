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

package org.axonframework.spring.eventsourcing;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.*;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.tracing.SpanFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Snapshotter implementation that uses the AggregateRoot as state for the snapshot. Unlike the
 * {@link AggregateSnapshotter}, this implementation lazily retrieves AggregateFactories from the Application
 * Context when snapshot requests are made.
 * <p>
 * Instead of configuring directly, consider using the {@link SpringAggregateSnapshotterFactoryBean}, which
 * configures this class using values available in the application context.
 *
 * @author Allard Buijze
 * @see SpringAggregateSnapshotterFactoryBean
 * @since 3.0
 */
public class SpringAggregateSnapshotter extends AggregateSnapshotter implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * Instantiate a {@link SpringAggregateSnapshotter} based on the fields contained in the {@link Builder}. The
     * {@link AggregateFactory} instances are lazily retrieved by the {@link ApplicationContext}.
     * <p>
     * Will assert that the {@link EventStore}, {@link ParameterResolverFactory} and {@link HandlerDefinition} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SpringAggregateSnapshotter} instance
     */
    protected SpringAggregateSnapshotter(Builder builder) {
        super(builder);
    }

    /**
     * Instantiate a Builder to be able to create a {@link SpringAggregateSnapshotter}. The {@link AggregateFactory}
     * instances are lazily retrieved by the {@link ApplicationContext}.
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
     * @return a Builder to be able to create a {@link SpringAggregateSnapshotter}
     * @see org.axonframework.messaging.annotation.ClasspathParameterResolverFactory
     * @see org.axonframework.messaging.annotation.ClasspathHandlerDefinition
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected AggregateFactory<?> getAggregateFactory(Class<?> aggregateType) {
        AggregateFactory<?> aggregateFactory = super.getAggregateFactory(aggregateType);
        if (aggregateFactory == null) {
            Optional<AggregateFactory> factory =
                    beansOfTypeIncludingAncestors(applicationContext, AggregateFactory.class).values().stream()
                                      .filter(af -> Objects.equals(af.getAggregateType(), aggregateType))
                                      .findFirst();
            if (!factory.isPresent()) {
                factory = beansOfTypeIncludingAncestors(applicationContext, EventSourcingRepository.class).values().stream()
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
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Builder class to instantiate a {@link SpringAggregateSnapshotter}. The {@link AggregateFactory} instances are
     * lazily retrieved by the {@link ApplicationContext}.
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
     * @see org.axonframework.messaging.annotation.ClasspathParameterResolverFactory
     * @see org.axonframework.messaging.annotation.ClasspathHandlerDefinition
     */
    public static class Builder extends AggregateSnapshotter.Builder {

        public Builder() {
            aggregateFactories(Collections.emptyList());
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

        @Override
        public Builder repositoryProvider(RepositoryProvider repositoryProvider) {
            super.repositoryProvider(repositoryProvider);
            return this;
        }

        @Override
        public Builder parameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
            super.parameterResolverFactory(parameterResolverFactory);
            return this;
        }

        @Override
        public Builder handlerDefinition(HandlerDefinition handlerDefinition) {
            super.handlerDefinition(handlerDefinition);
            return this;
        }

        @Override
        public Builder spanFactory(@Nonnull SpanFactory spanFactory) {
            super.spanFactory(spanFactory);
            return this;
        }

        @Override
        public Builder spanFactory(@Nonnull SnapshotterSpanFactory spanFactory) {
            super.spanFactory(spanFactory);
            return this;
        }

        /**
         * Initializes a {@link SpringAggregateSnapshotter} as specified through this Builder.
         *
         * @return a {@link SpringAggregateSnapshotter} as specified through this Builder
         */
        public SpringAggregateSnapshotter build() {
            return new SpringAggregateSnapshotter(this);
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
}
