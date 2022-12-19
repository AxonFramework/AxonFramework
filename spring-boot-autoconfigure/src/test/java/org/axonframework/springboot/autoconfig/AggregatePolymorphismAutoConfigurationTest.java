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

package org.axonframework.springboot.autoconfig;

import com.thoughtworks.xstream.XStream;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.spring.eventsourcing.SpringPrototypeAggregateFactory;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.springboot.utils.TestSerializer;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.common.StringUtils.lowerCaseFirstCharacterOf;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Test class validating Aggregate Polymorphism works as intended when using Spring Boot autoconfiguration.
 *
 * @author Steven van Beelen
 */
class AggregatePolymorphismAutoConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withUserConfiguration(DefaultContext.class)
                                                               .withPropertyValues("axon.axonserver.enabled:false");
    }

    @Test
    void polymorphicAggregateWiringHandlesCommandsAndIsEventSourcedAsExpected() {
        String catId = UUID.randomUUID().toString();
        String dogId = UUID.randomUUID().toString();
        String catFactoryBeanName = aggregateFactoryBeanNameFor(PolymorphicAggregateContext.Cat.class);
        String dogFactoryBeanName = aggregateFactoryBeanNameFor(PolymorphicAggregateContext.Dog.class);
        String animalFactoryBeanName = aggregateFactoryBeanNameFor(PolymorphicAggregateContext.Animal.class);

        testApplicationContext.withUserConfiguration(PolymorphicAggregateContext.class)
                              .run(context -> {
                                  // Only a single Aggregate Factory should exist, for both Cats and Dogs.
                                  assertThat(context).hasSingleBean(SpringPrototypeAggregateFactory.class);
                                  assertThat(context)
                                          .getBean(catFactoryBeanName, SpringPrototypeAggregateFactory.class)
                                          .isNull();
                                  assertThat(context)
                                          .getBean(dogFactoryBeanName, SpringPrototypeAggregateFactory.class)
                                          .isNull();
                                  assertThat(context)
                                          .getBean(animalFactoryBeanName, SpringPrototypeAggregateFactory.class)
                                          .isNotNull();

                                  CommandGateway commandGateway =
                                          context.getBean("commandGateway", CommandGateway.class);

                                  commandGateway.sendAndWait(
                                          new PolymorphicAggregateContext.CreateCatCommand(catId, "Felix")
                                  );
                                  commandGateway.sendAndWait(
                                          new PolymorphicAggregateContext.RenameAnimalCommand(catId, "Wokkel")
                                  );
                                  commandGateway.sendAndWait(
                                          new PolymorphicAggregateContext.CreateDogCommand(dogId, "Milou")
                                  );
                                  commandGateway.sendAndWait(
                                          new PolymorphicAggregateContext.RenameAnimalCommand(dogId, "Medor")
                                  );
                              });
    }

    private static String aggregateFactoryBeanNameFor(Class<?> aggregateClass) {
        return lowerCaseFirstCharacterOf(aggregateClass.getSimpleName()) + "AggregateFactory";
    }

    @Test
    void polymorphicAggregateWiringConstructsSingleRepository() {
        testApplicationContext.withUserConfiguration(PolymorphicAggregateContext.class)
                              .run(context -> {
                                  assertThat(context).hasSingleBean(Repository.class);
                                  assertThat(context).getBean(Repository.class)
                                                     .isInstanceOf(EventSourcingRepository.class);
                                  assertThat(context).getBean("animalRepository", Repository.class)
                                                     .isNotNull();
                              });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    static class DefaultContext {

        @Bean
        public EventStorageEngine eventStorageEngine() {
            return new InMemoryEventStorageEngine();
        }

        @Bean
        public XStream xStream() {
            return TestSerializer.xStreamSerializer().getXStream();
        }
    }

    @Configuration
    static class PolymorphicAggregateContext {

        @Aggregate
        public static class Cat extends Animal {

            @CommandHandler
            public Cat(CreateCatCommand command) {
                apply(new CatCreatedEvent(command.aggregateId, command.name));
            }

            @EventSourcingHandler
            public void on(CatCreatedEvent event) {
                this.aggregateId = event.aggregateId;
                this.name = event.name;
            }

            public Cat() {
                // Required by Axon
            }
        }

        public static class CreateCatCommand {

            @TargetAggregateIdentifier
            private final String aggregateId;
            private final String name;

            public CreateCatCommand(String aggregateId, String name) {
                this.aggregateId = aggregateId;
                this.name = name;
            }
        }

        public static class CatCreatedEvent {

            private final String aggregateId;
            private final String name;

            public CatCreatedEvent(String aggregateId, String name) {
                this.aggregateId = aggregateId;
                this.name = name;
            }
        }

        @Aggregate
        public static class Dog extends Animal {

            @CommandHandler
            public Dog(CreateDogCommand command) {
                apply(new DogCreatedEvent(command.aggregateId, command.name));
            }

            @EventSourcingHandler
            public void on(DogCreatedEvent event) {
                this.aggregateId = event.aggregateId;
                this.name = event.name;
            }

            public Dog() {
                // Required by Axon
            }
        }

        public static class CreateDogCommand {

            @TargetAggregateIdentifier
            private final String aggregateId;
            private final String name;

            public CreateDogCommand(String aggregateId, String name) {
                this.aggregateId = aggregateId;
                this.name = name;
            }
        }

        public static class DogCreatedEvent {

            private final String aggregateId;
            private final String name;

            public DogCreatedEvent(String aggregateId, String name) {
                this.aggregateId = aggregateId;
                this.name = name;
            }
        }

        @Aggregate
        public static abstract class Animal {

            @AggregateIdentifier
            protected String aggregateId;
            protected String name;

            @CommandHandler
            public void handle(RenameAnimalCommand command) {
                apply(new AnimalRenamedEvent(aggregateId, command.rename));
            }

            @EventSourcingHandler
            public void on(AnimalRenamedEvent event) {
                this.name = event.rename;
            }

            public Animal() {
            }
        }

        public static class RenameAnimalCommand {

            @SuppressWarnings({"FieldCanBeLocal", "unused"})
            @TargetAggregateIdentifier
            private final String aggregateId;
            private final String rename;

            public RenameAnimalCommand(String aggregateId, String rename) {
                this.aggregateId = aggregateId;
                this.rename = rename;
            }
        }

        public static class AnimalRenamedEvent {

            @SuppressWarnings({"FieldCanBeLocal", "unused"})
            private final String aggregateId;
            private final String rename;

            public AnimalRenamedEvent(String aggregateId, String rename) {
                this.aggregateId = aggregateId;
                this.rename = rename;
            }
        }
    }
}
