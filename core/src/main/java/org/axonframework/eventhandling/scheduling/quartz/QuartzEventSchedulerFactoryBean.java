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

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.SpringTransactionalTriggerCallback;
import org.axonframework.util.AxonConfigurationException;
import org.quartz.Scheduler;
import org.quartz.core.QuartzScheduler;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Spring FactoryBean that creates a Quartz EventScheduler instance using resources found in the Spring Application
 * Context. The Quartz EventScheduler delegates the actual scheduling and triggering to a Quartz Scheduler, making it
 * more suitable for long-term triggers and triggers that must survive a system restart.
 * <p/>
 * This implementation will automatically detect the Quartz version used. For Quartz 1 support, make sure the
 * <em>axon-quartz1</em> module is on the classpath.
 * <p/>
 * Note that Quartz 1 support is deprecated and will be removed in Axon 2.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class QuartzEventSchedulerFactoryBean implements FactoryBean<AbstractQuartzEventScheduler>, InitializingBean,
        ApplicationContextAware {

    private ApplicationContext applicationContext;
    private AbstractQuartzEventScheduler eventScheduler;
    private Scheduler scheduler;
    private EventBus eventBus;
    private String groupIdentifier;
    private PlatformTransactionManager transactionManager;
    private TransactionDefinition transactionDefinition;

    @Override
    public AbstractQuartzEventScheduler getObject() throws Exception {
        return eventScheduler;
    }

    @Override
    public Class<?> getObjectType() {
        try {
            return Quartz2EventScheduler.class.getClassLoader().loadClass(
                    "org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler");
        } catch (ClassNotFoundException e) {
            return AbstractQuartzEventScheduler.class;
        }
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
        if ("1".equals(QuartzScheduler.getVersionMajor())) {
            try {
                Class<?> schedulerClass = Quartz2EventScheduler.class.getClassLoader().loadClass(
                        "org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler");
                eventScheduler = (AbstractQuartzEventScheduler) schedulerClass.newInstance();
            } catch (ClassNotFoundException e) {
                throw new AxonConfigurationException(
                        "No Axon support for Quartz 1. "
                                + "Make sure the axon-quartz1 module is available on the classpath", e);
            }
        } else {
            eventScheduler = new Quartz2EventScheduler();
        }
        eventScheduler.setScheduler(scheduler);
        eventScheduler.setEventBus(eventBus);
        if (groupIdentifier != null) {
            eventScheduler.setGroupIdentifier(groupIdentifier);
        }
        if (transactionManager != null) {
            SpringTransactionalTriggerCallback callback = new SpringTransactionalTriggerCallback();
            callback.setTransactionManager(transactionManager);
            if (transactionDefinition != null) {
                callback.setTransactionDefinition(transactionDefinition);
            }
            eventScheduler.setEventTriggerCallback(callback);
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
     * The TransactionDefinition to use by the transaction manager. Default to a {@link
     * org.springframework.transaction.support.DefaultTransactionDefinition}.
     * Is ignored if no transaction manager is configured.
     *
     * @param transactionDefinition the TransactionDefinition to use by the transaction manager
     */
    public void setTransactionDefinition(TransactionDefinition transactionDefinition) {
        this.transactionDefinition = transactionDefinition;
    }
}
