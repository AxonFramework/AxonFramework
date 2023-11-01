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

package org.axonframework.springboot.autoconfig;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineManagerSpanFactory;
import org.axonframework.deadline.dbscheduler.DbSchedulerBinaryDeadlineDetails;
import org.axonframework.deadline.dbscheduler.DbSchedulerDeadlineManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.dbscheduler.DbSchedulerBinaryEventData;
import org.axonframework.eventhandling.scheduling.dbscheduler.DbSchedulerEventScheduler;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.axonframework.tracing.SpanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Auto configuration class for the deadline manager and event scheduler using db-scheduler.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
@AutoConfiguration
@ConditionalOnBean(Scheduler.class)
@AutoConfigureAfter(value = {AxonServerAutoConfiguration.class, AxonTracingAutoConfiguration.class},
        name = {"com.github.kagkarlsson.scheduler.boot.autoconfigure.DbSchedulerAutoConfiguration"})
public class AxonDbSchedulerAutoConfiguration {

    @Bean
    @Qualifier("eventDataTask")
    @ConditionalOnMissingQualifiedBean(beanClass = Task.class, qualifier = "eventDataTask")
    public Task<DbSchedulerBinaryEventData> dbSchedulerEventDataTask(
            ApplicationContext context
    ) {
        return DbSchedulerEventScheduler.binaryTask(() -> context.getBean(DbSchedulerEventScheduler.class));
    }

    @Bean
    @Qualifier("deadlineDetailsTask")
    @ConditionalOnMissingQualifiedBean(beanClass = Task.class, qualifier = "deadlineDetailsTask")
    public Task<DbSchedulerBinaryDeadlineDetails> dbSchedulerDeadlineDetailsTask(
            ApplicationContext context
    ) {
        return DbSchedulerDeadlineManager.binaryTask(() -> context.getBean(DbSchedulerDeadlineManager.class));
    }

    @Bean
    @ConditionalOnMissingBean
    public EventScheduler eventScheduler(
            Scheduler scheduler,
            @Qualifier("eventSerializer") Serializer serializer,
            TransactionManager transactionManager,
            EventBus eventBus) {
        return DbSchedulerEventScheduler.builder()
                                        .scheduler(scheduler)
                                        .serializer(serializer)
                                        .transactionManager(transactionManager)
                                        .eventBus(eventBus)
                                        //Set to false, as a DbSchedulerStarter is expected to start the scheduler.
                                        .startScheduler(false)
                                        //Set to false, as autoconfiguration from the db scheduler will take care.
                                        .stopScheduler(false)
                                        .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeadlineManager deadlineManager(
            Scheduler scheduler,
            Configuration configuration,
            @Qualifier("eventSerializer") Serializer serializer,
            TransactionManager transactionManager,
            DeadlineManagerSpanFactory spanFactory) {
        ScopeAwareProvider scopeAwareProvider = new ConfigurationScopeAwareProvider(configuration);
        return DbSchedulerDeadlineManager.builder()
                                         .scheduler(scheduler)
                                         .scopeAwareProvider(scopeAwareProvider)
                                         .serializer(serializer)
                                         .transactionManager(transactionManager)
                                         .spanFactory(spanFactory)
                                         //Set to false, as a DbSchedulerStarter is expected to start the scheduler.
                                         .startScheduler(false)
                                         //Set to false as the auto config of DbScheduler manages this.
                                         .stopScheduler(false)
                                         .build();
    }
}
