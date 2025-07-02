/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.eventstore.LegacyEmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.LegacyInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageType;
import org.axonframework.spring.config.SpringAggregateLookup;
import org.axonframework.spring.eventsourcing.context.SpringWiredAggregate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SpringPrototypeAggregateFactory}.
 *
 * @author Allard Buijze
 */
@Disabled("TODO #3499") // TODO #3499 Fix as part of referred to issue
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringPrototypeAggregateFactoryTest.Context.class)
class SpringPrototypeAggregateFactoryTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private AggregateFactory<SpringWiredAggregate> testSubject;

    @Test
    void contextStarts() {
        assertNotNull(testSubject);
    }

    @Test
    void createNewAggregateInstance() {
        GenericDomainEventMessage<String> domainEvent = new GenericDomainEventMessage<>(
                "SpringWiredAggregate", "id2", 0, new MessageType("event"), "FirstEvent"
        );
        SpringWiredAggregate aggregate = testSubject.createAggregateRoot("id2", domainEvent);

        assertNotNull(aggregate.getContext(), "ContextAware method not invoked");
    }

    @Test
    void processSnapshotAggregateInstance() {
        DomainEventMessage<SpringWiredAggregate> snapshotEvent = new GenericDomainEventMessage<>(
                "SpringWiredAggregate", "id2", 5, new MessageType("event"),
                new SpringWiredAggregate()
        );
        SpringWiredAggregate aggregate = testSubject.createAggregateRoot("id2", snapshotEvent);

        assertNotNull(aggregate.getContext(), "ContextAware method not invoked");
    }

    @Configuration
    @ComponentScan(basePackages = {"org.axonframework.spring.eventsourcing.context"})
    static class Context {

        // Wired to ensure an EventStore is present for the aggregate to allow event sourcing.
        @Bean
        public LegacyEmbeddedEventStore eventStore() {
            return LegacyEmbeddedEventStore.builder()
                                           .storageEngine(new LegacyInMemoryEventStorageEngine())
                                           .build();
        }

        /**
         * The below three bean methods are a copy of the
         * {@code org.axonframework.springboot.autoconfig.InfraConfiguration} to set up the basics to auto-wired
         * Aggregates.
         * <p>
         * Copied to keep this test Spring Boot agnostic.
         */
        @Bean
        public static SpringAggregateLookup springAggregateLookup() {
            return new SpringAggregateLookup();
        }

//        @Bean
//        public SpringAxonConfiguration springAxonConfiguration(LegacyConfigurer configurer) {
//            return new SpringAxonConfiguration(configurer);
//        }

//        @Bean
//        public SpringConfigurer springAxonConfigurer(ConfigurableListableBeanFactory beanFactory,
//                                                     List<ConfigurerModule> configurerModules,
//                                                     List<ModuleConfiguration> moduleConfigurations) {
//            SpringConfigurer configurer = new SpringConfigurer(beanFactory);
//            moduleConfigurations.forEach(configurer::registerModule);
//            configurerModules.forEach(c -> c.configureModule(configurer));
//            return configurer;
//        }
    }
}
