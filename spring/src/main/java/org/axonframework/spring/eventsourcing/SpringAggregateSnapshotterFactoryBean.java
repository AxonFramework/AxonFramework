/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.common.DirectExecutor;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.spring.config.annotation.HandlerDefinitionFactoryBean;
import org.axonframework.spring.config.annotation.SpringBeanDependencyResolverFactory;
import org.axonframework.spring.config.annotation.SpringBeanParameterResolverFactory;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;

/**
 * Implementation of the {@link org.axonframework.eventsourcing.AggregateSnapshotter} that eases the configuration when
 * used within a Spring Application Context. It will automatically detect a Transaction Manager and Aggregate
 * Factories.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class SpringAggregateSnapshotterFactoryBean
        implements FactoryBean<SpringAggregateSnapshotter>, ApplicationContextAware {

    private Executor executor = DirectExecutor.INSTANCE;
    private PlatformTransactionManager transactionManager;
    private ApplicationContext applicationContext;
    private TransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
    private EventStore eventStore;
    private RepositoryProvider repositoryProvider;
    private ParameterResolverFactory parameterResolverFactory;
    private HandlerDefinition handlerDefinition;

    @Override
    public SpringAggregateSnapshotter getObject() {
        if (transactionManager == null) {
            Map<String, PlatformTransactionManager> candidates =
                    applicationContext.getBeansOfType(PlatformTransactionManager.class);
            if (candidates.size() == 1) {
                this.transactionManager = candidates.values().iterator().next();
            }
        }

        if (eventStore == null) {
            eventStore = applicationContext.getBean(EventStore.class);
        }

        if (repositoryProvider == null) {
            repositoryProvider = applicationContext.getBean(Configuration.class)::repository;
        }

        if (parameterResolverFactory == null) {
            parameterResolverFactory = MultiParameterResolverFactory
                    .ordered(ClasspathParameterResolverFactory.forClass(getObjectType()),
                             new SpringBeanDependencyResolverFactory(applicationContext),
                             new SpringBeanParameterResolverFactory(applicationContext));
        }

        if (handlerDefinition == null) {
            handlerDefinition = new HandlerDefinitionFactoryBean(new ArrayList<>(applicationContext.getBeansOfType(HandlerDefinition.class).values()),
                                                                 new ArrayList<>(applicationContext.getBeansOfType(HandlerEnhancerDefinition.class).values()))
                    .getObject();
        }

        TransactionManager txManager = transactionManager == null ? NoTransactionManager.INSTANCE :
                                       new SpringTransactionManager(transactionManager, transactionDefinition);

        SpringAggregateSnapshotter snapshotter =
                SpringAggregateSnapshotter.builder()
                                          .eventStore(eventStore)
                                          .executor(executor)
                                          .transactionManager(txManager)
                                          .repositoryProvider(repositoryProvider)
                                          .parameterResolverFactory(parameterResolverFactory)
                                          .handlerDefinition(handlerDefinition)
                                          .build();
        snapshotter.setApplicationContext(applicationContext);
        return snapshotter;
    }

    @Override
    public Class<?> getObjectType() {
        return AggregateSnapshotter.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * Sets the transaction manager to manager underlying transaction with. If none is provided, an attempt is made to
     * auto detect is from the application context. If a single transaction manager is found, it is used to manage
     * transactions. Of none or more than one is found, they are ignored.
     *
     * @param transactionManager the transaction manager managing underlying transactions
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Optionally sets the transaction definition to use. By default, uses the application context's default transaction
     * semantics (see {@link org.springframework.transaction.support.DefaultTransactionDefinition}).
     *
     * @param transactionDefinition the transaction definition to use
     */
    public void setTransactionDefinition(TransactionDefinition transactionDefinition) {
        this.transactionDefinition = transactionDefinition;
    }

    /**
     * Sets the Event Store instance to write the snapshots to
     *
     * @param eventStore The Event Store to store the snapshot events in
     */
    public void setEventStore(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    /**
     * Sets repository provider in order to have possibility to spawn new aggregates from THE aggregate.
     *
     * @param repositoryProvider Provides repositories for specific aggregate types
     */
    public void setRepositoryProvider(RepositoryProvider repositoryProvider) {
        this.repositoryProvider = repositoryProvider;
    }

    /**
     * Sets handler definition to be able to create concrete handlers.
     *
     * @param handlerDefinition The handler definition used to create concrete handlers
     */
    public void setHandlerDefinition(HandlerDefinition handlerDefinition) {
        this.handlerDefinition = handlerDefinition;
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Sets the executor to process the creation (and storage) of each snapshot. Defaults to an executer that runs the
     * task in the calling thread.
     *
     * @param executor The executor to process creation and storage of the snapshots with
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }
}
