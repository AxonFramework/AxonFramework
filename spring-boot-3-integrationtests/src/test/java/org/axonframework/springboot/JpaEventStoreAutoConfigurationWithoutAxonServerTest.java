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

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests JPA EventStore auto-configuration
 *
 * @author Sara Pellegrini
 */

class JpaEventStoreAutoConfigurationWithoutAxonServerTest {

    @Test
    void eventStore() {
        new ApplicationContextRunner()
                .withPropertyValues("axon.axonserver.enabled=false")
                .withUserConfiguration(TestContext.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(JpaEventStorageEngine.class);
                    assertThat(context).getBean(JpaEventStorageEngine.class).isInstanceOf(JpaEventStorageEngine.class);
                    assertThat(context).getBean(EventStore.class).isInstanceOf(EmbeddedEventStore.class);
                });
    }

    @Test
    void eventBusOverridesEventStoreDefinition() {
        new ApplicationContextRunner()
                .withPropertyValues("axon.axonserver.enabled=false")
                .withUserConfiguration(EventBusContext.class, TestContext.class)
                .run(context -> {
                    assertThat(context).hasBean("simpleEventBus");
                    assertThat(context).getBean(EventBus.class).isInstanceOf(SimpleEventBus.class);
                    assertThat(context).doesNotHaveBean(EventStore.class);
                    assertThat(context).doesNotHaveBean(EventStorageEngine.class);
                });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContext {

    }

    private static class EventBusContext {

        @Bean
        public EventBus simpleEventBus() {
            return SimpleEventBus.builder().build();
        }
    }
}
