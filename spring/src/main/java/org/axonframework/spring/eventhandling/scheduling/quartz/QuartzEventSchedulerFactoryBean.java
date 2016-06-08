/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.spring.eventhandling.scheduling.quartz;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.quartz.Scheduler;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Spring FactoryBean that creates a QuartzEventScheduler instance using resources found in the Spring Application
 * Context. The
 * QuartzEventScheduler delegates the actual scheduling and triggering to a Quartz Scheduler, making it more suitable
 * for long-term triggers and triggers that must survive a system restart.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class QuartzEventSchedulerFactoryBean implements FactoryBean<QuartzEventScheduler>, InitializingBean,
        ApplicationContextAware {

    private ApplicationContext applicationContext;
    private QuartzEventScheduler eventScheduler;
    private Scheduler scheduler;
    private EventBus eventBus;
    private String groupIdentifier;
    private PlatformTransactionManager transactionManager;
    private TransactionDefinition transactionDefinition;


    @Override
    public QuartzEventScheduler getObject() throws Exception {
        return eventScheduler;
    }

    @Override
    public Class<?> getObjectType() {
        return QuartzEventScheduler.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (eventBus == null) {
            eventBus = applicationContext.getBean(EventBus.class);
        }
        if (scheduler == null) {
            scheduler = applicationContext.getBean(Scheduler.class);
        }

        eventScheduler = new QuartzEventScheduler();
        eventScheduler.setScheduler(scheduler);
        eventScheduler.setEventBus(eventBus);
        if (groupIdentifier != null) {
            eventScheduler.setGroupIdentifier(groupIdentifier);
        }
        if (transactionManager != null) {
            eventScheduler.setTransactionManager(new SpringTransactionManager(transactionManager,
                                                                              transactionDefinition));
        }
        eventScheduler.initialize();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Sets the backing Quartz Scheduler for this timer.
     *
     * @param scheduler the backing Quartz Scheduler for this timer
     */
    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Sets the event bus to which scheduled events need to be published.
     *
     * @param eventBus the event bus to which scheduled events need to be published.
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Sets the group identifier to use when scheduling jobs with Quartz. Defaults to "AxonFramework-Events".
     *
     * @param groupIdentifier the group identifier to use when scheduling jobs with Quartz
     */
    public void setGroupIdentifier(String groupIdentifier) {
        this.groupIdentifier = groupIdentifier;
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
     * The TransactionDefinition to use by the transaction manager. Defaults to a {@link
     * org.springframework.transaction.support.DefaultTransactionDefinition}.
     * Is ignored if no transaction manager is configured.
     *
     * @param transactionDefinition the TransactionDefinition to use by the transaction manager
     */
    public void setTransactionDefinition(TransactionDefinition transactionDefinition) {
        this.transactionDefinition = transactionDefinition;
    }
}
