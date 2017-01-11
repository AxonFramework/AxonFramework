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

package org.axonframework.spring.eventsourcing;

import org.axonframework.common.DirectExecutor;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.spring.config.annotation.SpringBeanParameterResolverFactory;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Implementation of the {@link org.axonframework.eventsourcing.AggregateSnapshotter} that eases the configuration when
 * used within a Spring Application Context. It will automatically detect a Transaction Manager and Aggregate
 * Factories.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class SpringAggregateSnapshotterFactoryBean implements FactoryBean<SpringAggregateSnapshotter>, ApplicationContextAware {

    private final Executor executor = DirectExecutor.INSTANCE;
    private PlatformTransactionManager transactionManager;
    private ApplicationContext applicationContext;
    private TransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
    private EventStore eventStore;
    private ParameterResolverFactory parameterResolverFactory;

    @Override
    public SpringAggregateSnapshotter getObject() throws Exception {
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

        if (parameterResolverFactory == null) {
            parameterResolverFactory = MultiParameterResolverFactory
                    .ordered(ClasspathParameterResolverFactory.forClass(getObjectType()),
                             new SpringBeanParameterResolverFactory(applicationContext));
        }

        TransactionManager txManager = transactionManager == null ? NoTransactionManager.INSTANCE :
                new SpringTransactionManager(transactionManager, transactionDefinition);

        SpringAggregateSnapshotter snapshotter = new SpringAggregateSnapshotter(eventStore, parameterResolverFactory,
                                                                                executor, txManager);
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

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
