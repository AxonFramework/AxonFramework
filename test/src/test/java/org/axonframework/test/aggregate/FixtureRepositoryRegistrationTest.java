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

package org.axonframework.test.aggregate;

import jakarta.persistence.EntityManager;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.TimestampParameterResolverFactory;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.GenericJpaRepository;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.saga.SagaMethodMessageHandlerDefinition;
import org.axonframework.test.FixtureExecutionException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Test class validating the following behavior around the fixture's {@link Repository}:
 * <ul>
 *     <li>The default {@code Repository} of the fixture can be wired in message handling methods.</li>
 *     <li>A registered {@code Repository} in the fixture can be wired in message handling methods.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link RepositoryProvider} is registered after the {@code Repository} is defined.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link org.axonframework.messaging.annotation.ParameterResolverFactory} is registered after the {@code Repository} is defined.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link org.axonframework.messaging.annotation.HandlerDefinition} is registered after the {@code Repository} is defined.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link org.axonframework.messaging.annotation.HandlerEnhancerDefinition} is registered after the {@code Repository} is defined.</li>
 * </ul>
 *
 * @author Steven van Beelen
 */
class FixtureRepositoryRegistrationTest {

    private AggregateTestFixture<MyAggregate> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AggregateTestFixture<>(MyAggregate.class);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void fixturesDefaultRepositoryCanBeWiredAsMessageHandlingParameter() {
        testSubject.givenNoPriorActivity()
                   .when("some-command")
                   .expectEvents("some-command_" + EventSourcingRepository.class.getSimpleName());
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void customRepositoryCanBeWiredAsMessageHandlingParameter() {
        Repository<MyAggregate> testRepository =
                CustomRepository.builder()
                                .eventStore(testSubject.getEventStore())
                                .build();

        testSubject.registerRepository(testRepository)
                   .givenNoPriorActivity()
                   .when("some-command")
                   .expectEvents("some-command_" + testRepository.getClass().getSimpleName());
    }

    @Test
    void registeringTheRepositoryProviderThrowsFixtureExecutionExceptionAfterRegisteringTheRepository() {
        Repository<MyAggregate> testRepository =
                GenericJpaRepository.builder(MyAggregate.class)
                                    .entityManagerProvider(new SimpleEntityManagerProvider(mock(EntityManager.class)))
                                    .eventBus(SimpleEventBus.builder().build())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerRepositoryProvider(new RepositoryProvider() {
                         @Override
                         public <T> Repository<T> repositoryFor(@NotNull Class<T> aggregateType) {
                             return null;
                         }
                     }));
    }

    @Test
    void registeringTheRepositoryProviderThrowsFixtureExecutionExceptionAfterRetrievingTheRepository() {
        testSubject.getRepository();

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerRepositoryProvider(new RepositoryProvider() {
                         @Override
                         public <T> Repository<T> repositoryFor(@NotNull Class<T> aggregateType) {
                             return null;
                         }
                     }));
    }

    @Test
    void registeringParameterResolversThrowsFixtureExecutionExceptionAfterRegisteringTheRepository() {
        Repository<MyAggregate> testRepository =
                GenericJpaRepository.builder(MyAggregate.class)
                                    .entityManagerProvider(new SimpleEntityManagerProvider(mock(EntityManager.class)))
                                    .eventBus(SimpleEventBus.builder().build())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerParameterResolverFactory(new TimestampParameterResolverFactory()));
    }

    @Test
    void registeringParameterResolversThrowsFixtureExecutionExceptionAfterRetrievingTheRepository() {
        testSubject.getRepository();

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerParameterResolverFactory(new TimestampParameterResolverFactory()));
    }

    @Test
    void registeringHandlerDefinitionThrowsFixtureExecutionExceptionAfterRegisteringTheRepository() {
        Repository<MyAggregate> testRepository =
                GenericJpaRepository.builder(MyAggregate.class)
                                    .entityManagerProvider(new SimpleEntityManagerProvider(mock(EntityManager.class)))
                                    .eventBus(SimpleEventBus.builder().build())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerHandlerDefinition(new AnnotatedMessageHandlingMemberDefinition()));
    }

    @Test
    void registeringHandlerDefinitionThrowsFixtureExecutionExceptionAfterRetrievingTheRepository() {
        testSubject.getRepository();

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerHandlerDefinition(new AnnotatedMessageHandlingMemberDefinition()));
    }

    @Test
    void registeringHandlerEnhancersThrowsFixtureExecutionExceptionAfterRegisteringTheRepository() {
        Repository<MyAggregate> testRepository =
                GenericJpaRepository.builder(MyAggregate.class)
                                    .entityManagerProvider(new SimpleEntityManagerProvider(mock(EntityManager.class)))
                                    .eventBus(SimpleEventBus.builder().build())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerHandlerEnhancerDefinition(new SagaMethodMessageHandlerDefinition()));
    }

    @Test
    void registeringHandlerEnhancersThrowsFixtureExecutionExceptionAfterRetrievingTheRepository() {
        testSubject.getRepository();

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerHandlerEnhancerDefinition(new SagaMethodMessageHandlerDefinition()));
    }

    private static class MyAggregate {

        @AggregateIdentifier
        private String aggregateId;

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(String command, Repository<MyAggregate> repository) {
            apply(command + "_" + repository.getClass().getSimpleName());
        }

        @EventSourcingHandler
        public void on(String event) {
            aggregateId = event;
        }

        @SuppressWarnings("unused")
        public MyAggregate() {
            // Required by Axon Framework
        }
    }

    private static class CustomRepository extends EventSourcingRepository<MyAggregate> {

        protected CustomRepository(Builder builder) {
            super(builder);
        }

        public static Builder builder() {
            return new Builder(MyAggregate.class);
        }

        private static class Builder extends EventSourcingRepository.Builder<MyAggregate> {

            protected Builder(Class<MyAggregate> aggregateType) {
                super(aggregateType);
            }

            @SuppressWarnings("unchecked")
            public CustomRepository build() {
                return new CustomRepository(this);
            }
        }
    }
}
