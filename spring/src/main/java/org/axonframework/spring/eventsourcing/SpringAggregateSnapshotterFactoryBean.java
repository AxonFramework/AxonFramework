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
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.Snapshotter;
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
public class SpringAggregateSnapshotterFactoryBean implements FactoryBean<Snapshotter>, ApplicationContextAware {

    private PlatformTransactionManager transactionManager;
    private boolean autoDetectAggregateFactories = true;
    private ApplicationContext applicationContext;
    private TransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
    private EventStore eventStore;
    private final Executor executor = DirectExecutor.INSTANCE;
    private final List<AggregateFactory<?>> aggregateFactories = new ArrayList<>();
    private ParameterResolverFactory parameterResolverFactory;

    @Override
    public Snapshotter getObject() throws Exception {
        return (Snapshotter) Proxy.newProxyInstance(Snapshotter.class.getClassLoader(),
                                                    new Class[]{Snapshotter.class},
                                                    new LazyInitializationInvocationHandler());
    }

    private Snapshotter createInstance() {
        List<AggregateFactory<?>> factoriesFound = new ArrayList<>(aggregateFactories);
        if (autoDetectAggregateFactories) {
            applicationContext.getBeansOfType(AggregateFactory.class).values().forEach(factoriesFound::add);
            Collection<EventSourcingRepository> eventSourcingRepositories =
                    applicationContext.getBeansOfType(EventSourcingRepository.class).values();
            eventSourcingRepositories.forEach(repo -> factoriesFound.add(repo.getAggregateFactory()));
        }

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

        return new AggregateSnapshotter(eventStore, factoriesFound, parameterResolverFactory, executor, txManager);
    }

    private class LazyInitializationInvocationHandler implements InvocationHandler {

        private Snapshotter delegate;

        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
            synchronized (this) {
                if (delegate == null) {
                    delegate = createInstance();
                }
            }
            try {
                return method.invoke(delegate, objects);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
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
     * Optionally sets the aggregate factories to use. By default, this implementation will auto detect available
     * factories from the application context. Configuring them using this method will prevent auto detection.
     *
     * @param aggregateFactories The list of aggregate factories creating the aggregates to store.
     */
    public void setAggregateFactories(List<AggregateFactory<?>> aggregateFactories) {
        this.autoDetectAggregateFactories = false;
        this.aggregateFactories.addAll(aggregateFactories);
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
