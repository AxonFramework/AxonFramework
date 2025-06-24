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

package org.axonframework.springboot;

import com.thoughtworks.xstream.XStream;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.axonframework.eventsourcing.LegacyEventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.LegacyEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.LegacyInMemoryEventStorageEngine;
import org.axonframework.modelling.command.LegacyGenericJpaRepository;
import org.axonframework.modelling.command.LegacyRepository;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.springboot.utils.TestSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.axonframework.common.StringUtils.lowerCaseFirstCharacterOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating {@link LegacyRepository} beans are wired as intended to an external command handler.
 *
 * @author Steven van Beelen
 */
@Disabled("TODO #3496")
class RepositoryWiringTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withUserConfiguration(DefaultContext.class)
                                                               .withPropertyValues("axon.axonserver.enabled=false");
    }

    @Test
    void aggregateRepositoryIsWiredToExternalCommandHandler() {
        testApplicationContext.withUserConfiguration(SingleAggregateContext.class).run(context -> {
            SingleAggregateContext.ExternalCommandHandlerForAggregateOne externalHandler =
                    context.getBean(SingleAggregateContext.ExternalCommandHandlerForAggregateOne.class);
            assertNotNull(externalHandler);

            LegacyRepository<SingleAggregateContext.AggregateOne> repositoryFromHandler = externalHandler.getRepository();
            assertNotNull(repositoryFromHandler);
            assertEquals(repositoryFromHandler.getClass(), LegacyEventSourcingRepository.class);
            Object repositoryFromContext =
                    context.getBean(repositoryBeanName(SingleAggregateContext.AggregateOne.class));
            assertNotNull(repositoryFromContext);

            assertEquals(repositoryFromHandler, repositoryFromContext);
        });
    }

    @Test
    void aggregateRepositoriesAreWiredToExternalCommandHandlerBasedOnGenerics() {
        testApplicationContext.withUserConfiguration(SeveralAggregatesContext.class).run(context -> {
            SeveralAggregatesContext.ExternalCommandHandlerWiringThroughGenerics externalHandler =
                    context.getBean(SeveralAggregatesContext.ExternalCommandHandlerWiringThroughGenerics.class);
            assertNotNull(externalHandler);

            LegacyRepository<SeveralAggregatesContext.AggregateOne> repositoryOneFromHandler =
                    externalHandler.getRepositoryOne();
            assertNotNull(repositoryOneFromHandler);
            assertEquals(repositoryOneFromHandler.getClass(), LegacyEventSourcingRepository.class);
            Object repositoryOneFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateOne.class));
            assertNotNull(repositoryOneFromContext);
            assertEquals(repositoryOneFromHandler, repositoryOneFromContext);

            LegacyRepository<SeveralAggregatesContext.AggregateTwo> repositoryTwoFromHandler =
                    externalHandler.getRepositoryTwo();
            assertNotNull(repositoryTwoFromHandler);
            assertEquals(repositoryTwoFromHandler.getClass(), LegacyEventSourcingRepository.class);
            Object repositoryTwoFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateTwo.class));
            assertNotNull(repositoryTwoFromContext);
            assertEquals(repositoryTwoFromHandler, repositoryTwoFromContext);

            LegacyRepository<SeveralAggregatesContext.AggregateThree> repositoryThreeFromHandler =
                    externalHandler.getRepositoryThree();
            assertNotNull(repositoryThreeFromHandler);
            assertEquals(repositoryThreeFromHandler.getClass(), LegacyEventSourcingRepository.class);
            Object repositoryThreeFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateThree.class));
            assertNotNull(repositoryThreeFromContext);
            assertEquals(repositoryThreeFromHandler, repositoryThreeFromContext);
        });
    }

    @Test
    void aggregateRepositoriesAreWiredToExternalCommandHandlerBasedOnBeanName() {
        testApplicationContext.withUserConfiguration(SeveralAggregatesContext.class).run(context -> {
            SeveralAggregatesContext.ExternalCommandHandlerWiringThroughBeanNames externalHandler =
                    context.getBean(SeveralAggregatesContext.ExternalCommandHandlerWiringThroughBeanNames.class);
            assertNotNull(externalHandler);

            LegacyRepository<?> repositoryOneFromHandler = externalHandler.getRepositoryOne();
            assertNotNull(repositoryOneFromHandler);
            assertEquals(repositoryOneFromHandler.getClass(), LegacyEventSourcingRepository.class);
            Object repositoryOneFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateOne.class));
            assertNotNull(repositoryOneFromContext);
            assertEquals(repositoryOneFromHandler, repositoryOneFromContext);

            LegacyRepository<?> repositoryTwoFromHandler = externalHandler.getRepositoryTwo();
            assertNotNull(repositoryTwoFromHandler);
            assertEquals(repositoryTwoFromHandler.getClass(), LegacyEventSourcingRepository.class);
            Object repositoryTwoFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateTwo.class));
            assertNotNull(repositoryTwoFromContext);
            assertEquals(repositoryTwoFromHandler, repositoryTwoFromContext);

            LegacyRepository<?> repositoryThreeFromHandler = externalHandler.getRepositoryThree();
            assertNotNull(repositoryThreeFromHandler);
            assertEquals(repositoryThreeFromHandler.getClass(), LegacyEventSourcingRepository.class);
            Object repositoryThreeFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateThree.class));
            assertNotNull(repositoryThreeFromContext);
            assertEquals(repositoryThreeFromHandler, repositoryThreeFromContext);
        });
    }

    @Test
    void statedStoredAggregateRepositoryIsWiredToExternalCommandHandler() {
        testApplicationContext.withUserConfiguration(StateStoredAggregateContext.class).run(context -> {
            StateStoredAggregateContext.ExternalCommandHandlerForStateStoredAggregate externalHandler =
                    context.getBean(StateStoredAggregateContext.ExternalCommandHandlerForStateStoredAggregate.class);
            assertNotNull(externalHandler);

            LegacyRepository<StateStoredAggregateContext.StateStoredAggregate> repositoryFromHandler =
                    externalHandler.getRepository();
            assertNotNull(repositoryFromHandler);
            assertEquals(repositoryFromHandler.getClass(), LegacyGenericJpaRepository.class);
            Object repositoryFromContext =
                    context.getBean(repositoryBeanName(StateStoredAggregateContext.StateStoredAggregate.class));
            assertNotNull(repositoryFromContext);

            assertEquals(repositoryFromHandler, repositoryFromContext);
        });
    }

    private static String repositoryBeanName(Class<?> aggregateClass) {
        return lowerCaseFirstCharacterOf(aggregateClass.getSimpleName()) + "Repository";
    }

    @Configuration
    @EnableAutoConfiguration
    static class DefaultContext {

        @Bean
        public LegacyEventStorageEngine eventStorageEngine() {
            return new LegacyInMemoryEventStorageEngine();
        }

        @Bean
        public XStream xStream() {
            return TestSerializer.xStreamSerializer().getXStream();
        }
    }

    @Configuration
    static class SingleAggregateContext {

        @Aggregate
        static class AggregateOne {

            public AggregateOne() {
            }
        }

        @Component
        static class ExternalCommandHandlerForAggregateOne {

            private final LegacyRepository<AggregateOne> repository;

            ExternalCommandHandlerForAggregateOne(LegacyRepository<AggregateOne> repository) {
                this.repository = repository;
            }

            public LegacyRepository<AggregateOne> getRepository() {
                return repository;
            }
        }
    }

    @Configuration
    static class SeveralAggregatesContext {

        @Aggregate
        static class AggregateOne {

            public AggregateOne() {
            }
        }

        @Aggregate
        static class AggregateTwo {

            public AggregateTwo() {
            }
        }

        @Aggregate
        static class AggregateThree {

            public AggregateThree() {
            }
        }

        @Component
        static class ExternalCommandHandlerWiringThroughGenerics {

            private final LegacyRepository<AggregateOne> repositoryOne;
            private final LegacyRepository<AggregateTwo> repositoryTwo;
            private final LegacyRepository<AggregateThree> repositoryThree;

            ExternalCommandHandlerWiringThroughGenerics(LegacyRepository<AggregateOne> repositoryOne,
                                                        LegacyRepository<AggregateTwo> repositoryTwo,
                                                        LegacyRepository<AggregateThree> repositoryThree) {
                this.repositoryOne = repositoryOne;
                this.repositoryTwo = repositoryTwo;
                this.repositoryThree = repositoryThree;
            }

            public LegacyRepository<AggregateOne> getRepositoryOne() {
                return repositoryOne;
            }

            public LegacyRepository<AggregateTwo> getRepositoryTwo() {
                return repositoryTwo;
            }

            public LegacyRepository<AggregateThree> getRepositoryThree() {
                return repositoryThree;
            }
        }

        @Component
        static class ExternalCommandHandlerWiringThroughBeanNames {

            private final LegacyRepository<?> repositoryOne;
            private final LegacyRepository<?> repositoryTwo;
            private final LegacyRepository<?> repositoryThree;

            ExternalCommandHandlerWiringThroughBeanNames(
                    @Qualifier("aggregateOneRepository") LegacyRepository<?> repositoryOne,
                    @Qualifier("aggregateTwoRepository") LegacyRepository<?> repositoryTwo,
                    @Qualifier("aggregateThreeRepository") LegacyRepository<?> repositoryThree
            ) {
                this.repositoryOne = repositoryOne;
                this.repositoryTwo = repositoryTwo;
                this.repositoryThree = repositoryThree;
            }

            public LegacyRepository<?> getRepositoryOne() {
                return repositoryOne;
            }

            public LegacyRepository<?> getRepositoryTwo() {
                return repositoryTwo;
            }

            public LegacyRepository<?> getRepositoryThree() {
                return repositoryThree;
            }
        }
    }

    @Configuration
    static class StateStoredAggregateContext {

        @Entity
        @Aggregate
        static class StateStoredAggregate {

            @SuppressWarnings("unused") @Id
            private final String aggregateId = "some-id";

            public StateStoredAggregate() {
            }
        }

        @Component
        static class ExternalCommandHandlerForStateStoredAggregate {

            private final LegacyRepository<StateStoredAggregate> repository;

            ExternalCommandHandlerForStateStoredAggregate(LegacyRepository<StateStoredAggregate> repository) {
                this.repository = repository;
            }

            public LegacyRepository<StateStoredAggregate> getRepository() {
                return repository;
            }
        }
    }
}
