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

package org.axonframework.springboot;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.dbscheduler.DbSchedulerDeadlineManager;
import org.axonframework.deadline.dbscheduler.DbSchedulerHumanReadableDeadlineDetails;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.dbscheduler.DbSchedulerEventScheduler;
import org.axonframework.eventhandling.scheduling.dbscheduler.DbSchedulerHumanReadableEventData;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

class DbSchedulerAutoConfigurationTest {

    @Test
    void eventSchedulerAndDeadlineManagerCreated() {
        new ApplicationContextRunner()
                .withPropertyValues("axon.axonserver.enabled=false")
                .withUserConfiguration(DefaultContext.class)
                .run(context -> {
                    EventScheduler eventScheduler = context.getBean(EventScheduler.class);
                    assertNotNull(eventScheduler);
                    assertTrue(eventScheduler instanceof DbSchedulerEventScheduler);
                    DeadlineManager deadlineManager = context.getBean(DeadlineManager.class);
                    assertNotNull(deadlineManager);
                    assertTrue(deadlineManager instanceof DbSchedulerDeadlineManager);
                    assertEquals(2, context.getBeanNamesForType(Task.class).length);
                });
    }

    @Test
    void onlyTwoTaskBeansWhenOverwritten() {
        new ApplicationContextRunner()
                .withPropertyValues("axon.axonserver.enabled=false")
                .withUserConfiguration(HumanReadableContext.class)
                .run(context -> {
                    EventScheduler eventScheduler = context.getBean(EventScheduler.class);
                    assertNotNull(eventScheduler);
                    assertTrue(eventScheduler instanceof DbSchedulerEventScheduler);
                    DeadlineManager deadlineManager = context.getBean(DeadlineManager.class);
                    assertNotNull(deadlineManager);
                    assertTrue(deadlineManager instanceof DbSchedulerDeadlineManager);
                    assertEquals(2, context.getBeanNamesForType(Task.class).length);
                    Class<?> dataclass = context.getBean("dbSchedulerEventDataTask", Task.class).getDataClass();
                    assertEquals(DbSchedulerHumanReadableEventData.class, dataclass);
                    dataclass = context.getBean("dbSchedulerDeadlineDetailsTask", Task.class).getDataClass();
                    assertEquals(DbSchedulerHumanReadableDeadlineDetails.class, dataclass);
                });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class DefaultContext {

        private final DataSource existingDataSource;
        private final List<Task<?>> configuredTasks;

        public DefaultContext(
                DataSource dataSource,
                List<Task<?>> configuredTasks) {
            this.existingDataSource = dataSource;
            this.configuredTasks = configuredTasks;
        }

        @Bean
        public Scheduler scheduler() {
            return Scheduler.create(existingDataSource, configuredTasks).build();
        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class HumanReadableContext {

        private final DataSource existingDataSource;
        private final List<Task<?>> configuredTasks;

        @Bean
        @Qualifier("eventDataTask")
        @ConditionalOnMissingQualifiedBean(beanClass = Task.class, qualifier = "eventDataTask")
        public Task<DbSchedulerHumanReadableEventData> dbSchedulerEventDataTask() {
            return DbSchedulerEventScheduler.humanReadableTask();
        }

        @Bean
        @Qualifier("deadlineDetailsTask")
        @ConditionalOnMissingQualifiedBean(beanClass = Task.class, qualifier = "deadlineDetailsTask")
        public Task<DbSchedulerHumanReadableDeadlineDetails> dbSchedulerDeadlineDetailsTask() {
            return DbSchedulerDeadlineManager.humanReadableTask();
        }

        public HumanReadableContext(DataSource dataSource) {
            this.existingDataSource = dataSource;
            List<Task<?>> tasks = new ArrayList<>();
            tasks.add(dbSchedulerEventDataTask());
            tasks.add(dbSchedulerDeadlineDetailsTask());
            this.configuredTasks = tasks;
        }

        @Bean
        public Scheduler scheduler() {
            return Scheduler.create(existingDataSource, configuredTasks).build();
        }

        @Bean
        public EventScheduler eventScheduler(
                Scheduler scheduler,
                @Qualifier("eventSerializer") Serializer serializer,
                EventBus eventBus) {
            return DbSchedulerEventScheduler.builder()
                                            .scheduler(scheduler)
                                            .serializer(serializer)
                                            .useBinaryPojo(false)
                                            .eventBus(eventBus)
                                            .startScheduler(false)
                                            .build();
        }

        @Bean
        public DeadlineManager deadlineManager(
                Scheduler scheduler,
                Configuration configuration,
                @Qualifier("eventSerializer") Serializer serializer) {
            ScopeAwareProvider scopeAwareProvider = new ConfigurationScopeAwareProvider(configuration);
            return DbSchedulerDeadlineManager.builder()
                                             .scheduler(scheduler)
                                             .scopeAwareProvider(scopeAwareProvider)
                                             .serializer(serializer)
                                             .useBinaryPojo(false)
                                             .startScheduler(false)
                                             .build();
        }
    }
}
