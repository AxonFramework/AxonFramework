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

import com.thoughtworks.xstream.XStream;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.utils.TestSerializer;
import org.axonframework.modelling.command.Repository;
import org.axonframework.spring.stereotype.Aggregate;
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
 * Test class validating {@link Repository} beans are wired as intended to an external command handler.
 *
 * @author Steven van Beelen
 */
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

            Repository<SingleAggregateContext.AggregateOne> repositoryFromHandler = externalHandler.getRepository();
            assertNotNull(repositoryFromHandler);
            Object repositoryFromContext =
                    context.getBean(repositoryBeanName(SingleAggregateContext.AggregateOne.class));
            assertNotNull(repositoryFromContext);

            assertEquals(repositoryFromHandler, repositoryFromContext);
        });
    }

    @Disabled
    @Test
    void aggregateRepositoriesAreWiredToExternalCommandHandlerBasedOnGenerics() {
        testApplicationContext.withUserConfiguration(SeveralAggregatesContext.class).run(context -> {
            SeveralAggregatesContext.ExternalCommandHandlerWiringThroughGenerics externalHandler =
                    context.getBean(SeveralAggregatesContext.ExternalCommandHandlerWiringThroughGenerics.class);
            assertNotNull(externalHandler);

            Repository<SeveralAggregatesContext.AggregateOne> repositoryOneFromHandler =
                    externalHandler.getRepositoryOne();
            assertNotNull(repositoryOneFromHandler);
            Object repositoryOneFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateOne.class));
            assertNotNull(repositoryOneFromContext);
            assertEquals(repositoryOneFromHandler, repositoryOneFromContext);

            Repository<SeveralAggregatesContext.AggregateTwo> repositoryTwoFromHandler =
                    externalHandler.getRepositoryTwo();
            assertNotNull(repositoryTwoFromHandler);
            Object repositoryTwoFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateTwo.class));
            assertNotNull(repositoryTwoFromContext);
            assertEquals(repositoryTwoFromHandler, repositoryTwoFromContext);

            Repository<SeveralAggregatesContext.AggregateThree> repositoryThreeFromHandler =
                    externalHandler.getRepositoryThree();
            assertNotNull(repositoryThreeFromHandler);
            Object repositoryThreeFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateThree.class));
            assertNotNull(repositoryThreeFromContext);
            assertEquals(repositoryThreeFromHandler, repositoryThreeFromContext);
        });
    }

    @Disabled
    @Test
    void aggregateRepositoriesAreWiredToExternalCommandHandlerBasedOnBeanName() {
        testApplicationContext.withUserConfiguration(SeveralAggregatesContext.class).run(context -> {
            SeveralAggregatesContext.ExternalCommandHandlerWiringThroughBeanNames externalHandler =
                    context.getBean(SeveralAggregatesContext.ExternalCommandHandlerWiringThroughBeanNames.class);
            assertNotNull(externalHandler);

            Repository<?> repositoryOneFromHandler = externalHandler.getRepositoryOne();
            assertNotNull(repositoryOneFromHandler);
            Object repositoryOneFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateOne.class));
            assertNotNull(repositoryOneFromContext);
            assertEquals(repositoryOneFromHandler, repositoryOneFromContext);

            Repository<?> repositoryTwoFromHandler = externalHandler.getRepositoryTwo();
            assertNotNull(repositoryTwoFromHandler);
            Object repositoryTwoFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateTwo.class));
            assertNotNull(repositoryTwoFromContext);
            assertEquals(repositoryTwoFromHandler, repositoryTwoFromContext);

            Repository<?> repositoryThreeFromHandler = externalHandler.getRepositoryThree();
            assertNotNull(repositoryThreeFromHandler);
            Object repositoryThreeFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateThree.class));
            assertNotNull(repositoryThreeFromContext);
            assertEquals(repositoryThreeFromHandler, repositoryThreeFromContext);
        });
    }

    private static String repositoryBeanName(Class<?> aggregateClass) {
        return lowerCaseFirstCharacterOf(aggregateClass.getSimpleName()) + "Repository";
    }

    @Configuration
    @EnableAutoConfiguration
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
    static class SingleAggregateContext {

        @Aggregate
        static class AggregateOne {

            public AggregateOne() {
            }
        }

        @Component
        static class ExternalCommandHandlerForAggregateOne {

            private final Repository<AggregateOne> repository;

            ExternalCommandHandlerForAggregateOne(Repository<AggregateOne> repository) {
                this.repository = repository;
            }

            public Repository<AggregateOne> getRepository() {
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

            private final Repository<AggregateOne> repositoryOne;
            private final Repository<AggregateTwo> repositoryTwo;
            private final Repository<AggregateThree> repositoryThree;

            ExternalCommandHandlerWiringThroughGenerics(Repository<AggregateOne> repositoryOne,
                                                        Repository<AggregateTwo> repositoryTwo,
                                                        Repository<AggregateThree> repositoryThree) {
                this.repositoryOne = repositoryOne;
                this.repositoryTwo = repositoryTwo;
                this.repositoryThree = repositoryThree;
            }

            public Repository<AggregateOne> getRepositoryOne() {
                return repositoryOne;
            }

            public Repository<AggregateTwo> getRepositoryTwo() {
                return repositoryTwo;
            }

            public Repository<AggregateThree> getRepositoryThree() {
                return repositoryThree;
            }
        }

        @Component
        static class ExternalCommandHandlerWiringThroughBeanNames {

            private final Repository<?> repositoryOne;
            private final Repository<?> repositoryTwo;
            private final Repository<?> repositoryThree;

            ExternalCommandHandlerWiringThroughBeanNames(
                    @Qualifier("aggregateOneRepository") Repository<?> repositoryOne,
                    @Qualifier("aggregateTwoRepository") Repository<?> repositoryTwo,
                    @Qualifier("aggregateThreeRepository") Repository<?> repositoryThree
            ) {
                this.repositoryOne = repositoryOne;
                this.repositoryTwo = repositoryTwo;
                this.repositoryThree = repositoryThree;
            }

            public Repository<?> getRepositoryOne() {
                return repositoryOne;
            }

            public Repository<?> getRepositoryTwo() {
                return repositoryTwo;
            }

            public Repository<?> getRepositoryThree() {
                return repositoryThree;
            }
        }
    }
}
