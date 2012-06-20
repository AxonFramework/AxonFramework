/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.SpringTransactionalTriggerCallback;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Spring FactoryBean that creates a SimpleEventScheduler instance using resources found in the Spring Application
 * Context. The SimpleEventScheduler uses Java's ScheduledExecutorService as scheduling and triggering mechanism.
 * <p/>
 * Note that this mechanism is non-persistent. Scheduled tasks will be lost when the JVM is shut down, unless special
 * measures have been taken to prevent that. For more flexible and powerful scheduling options, see {@link
 * org.axonframework.eventhandling.scheduling.quartz.Quartz2EventScheduler}.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class SimpleEventSchedulerFactoryBean implements FactoryBean<SimpleEventScheduler>, InitializingBean,
        ApplicationContextAware {

    private EventBus eventBus;
    private ScheduledExecutorService executorService;
    private SimpleEventScheduler eventScheduler;
    private ApplicationContext applicationContext;
    private PlatformTransactionManager transactionManager;
    private TransactionDefinition transactionDefinition;

    @Override
    public SimpleEventScheduler getObject() throws Exception {
        return eventScheduler;
    }

    @Override
    public Class<?> getObjectType() {
        return SimpleEventScheduler.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (executorService == null) {
            executorService = Executors.newSingleThreadScheduledExecutor();
        }
        if (eventBus == null) {
            eventBus = applicationContext.getBean(EventBus.class);
        }
        if (transactionManager == null) {
            this.eventScheduler = new SimpleEventScheduler(executorService, eventBus);
        } else {
            SpringTransactionalTriggerCallback callback = new SpringTransactionalTriggerCallback();
            callback.setTransactionManager(transactionManager);
            if (transactionDefinition != null) {
                callback.setTransactionDefinition(transactionDefinition);
            }
            this.eventScheduler = new SimpleEventScheduler(executorService, eventBus, callback);
        }
    }

    /**
     * Sets the eventBus that scheduled events should be published to. Defaults to the EventBus found in the
     * application context. If there is more than one EventBus in the application context, you must specify which one
     * to
     * use.
     *
     * @param eventBus The EventBus to publish scheduled events to
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Sets the ExecutorService implementation that monitors the triggers and provides the Threads to publish events.
     * Defaults to a single-threaded executor.
     *
     * @param executorService The executor service providing the scheduling and execution resources
     */
    public void setExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Sets the transaction manager that manages the transaction around the publication of an event. If a transaction
     * manager is not specified, no transactions are managed around the event publication.
     *
     * @param transactionManager the transaction manager that takes care of transactions around event publication
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * The TransactionDefinition to use by the transaction manager. Default to a {@link
     * org.springframework.transaction.support.DefaultTransactionDefinition}.
     * Is ignored if no transaction manager is configured.
     *
     * @param transactionDefinition the TransactionDefinition to use by the transaction manager
     */
    public void setTransactionDefinition(TransactionDefinition transactionDefinition) {
        this.transactionDefinition = transactionDefinition;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
