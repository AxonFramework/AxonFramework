/*
 * Copyright (c) 2010-2026. Axon Framework
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

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.axonframework.messaging.eventhandling.annotation.TimestampParameterResolverFactory;
import org.axonframework.messaging.eventsourcing.LegacyEventSourcingRepository;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.messaging.core.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.core.annotation.HandlerDefinition;
import org.axonframework.messaging.core.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.timeout.HandlerTimeoutHandlerEnhancerDefinition;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.GenericJpaRepository;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.test.FixtureExecutionException;
import org.junit.jupiter.api.*;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the following behavior around the fixture's {@link Repository}:
 * <ul>
 *     <li>The default {@code Repository} of the fixture can be wired in message handling methods.</li>
 *     <li>A registered {@code Repository} in the fixture can be wired in message handling methods.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link RepositoryProvider} is registered after the {@code Repository} is defined.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link ParameterResolverFactory} is registered after the {@code Repository} is defined.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link HandlerDefinition} is registered after the {@code Repository} is defined.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link HandlerEnhancerDefinition} is registered after the {@code Repository} is defined.</li>
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
    @Disabled("TODO #3195 - Migration Module")
    void fixturesDefaultRepositoryCanBeWiredAsMessageHandlingParameter() {
        testSubject.givenNoPriorActivity()
                   .when("some-command")
                   .expectEvents("some-command_" + LegacyEventSourcingRepository.class.getSimpleName());
    }

    @Test
    @Disabled("TODO #3195 - Migration Module")
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
                                    .entityManagerProvider(
                                            new SimpleEntityManagerProvider(mock(EntityManager.class))
                                    )
                                    .eventBus(new SimpleEventBus())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerRepositoryProvider(new RepositoryProvider() {
                         @Override
                         public <T> Repository<T> repositoryFor(@Nonnull Class<T> aggregateType) {
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
                         public <T> Repository<T> repositoryFor(@Nonnull Class<T> aggregateType) {
                             return null;
                         }
                     }));
    }

    @Test
    void registeringParameterResolversThrowsFixtureExecutionExceptionAfterRegisteringTheRepository() {
        Repository<MyAggregate> testRepository =
                GenericJpaRepository.builder(MyAggregate.class)
                                    .entityManagerProvider(
                                            new SimpleEntityManagerProvider(mock(EntityManager.class))
                                    )
                                    .eventBus(new SimpleEventBus())
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
                                    .entityManagerProvider(
                                            new SimpleEntityManagerProvider(mock(EntityManager.class))
                                    )
                                    .eventBus(new SimpleEventBus())
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
                                    .entityManagerProvider(
                                            new SimpleEntityManagerProvider(mock(EntityManager.class))
                                    )
                                    .eventBus(new SimpleEventBus())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class, () -> testSubject.registerHandlerEnhancerDefinition(
                new HandlerTimeoutHandlerEnhancerDefinition(null)
        ));
    }

    @Test
    void registeringHandlerEnhancersThrowsFixtureExecutionExceptionAfterRetrievingTheRepository() {
        testSubject.getRepository();

        assertThrows(FixtureExecutionException.class, () -> testSubject.registerHandlerEnhancerDefinition(
                new HandlerTimeoutHandlerEnhancerDefinition(null)
        ));
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

    private static class CustomRepository extends LegacyEventSourcingRepository<MyAggregate> {

        protected CustomRepository(Builder builder) {
            super(builder);
        }

        public static Builder builder() {
            return new Builder(MyAggregate.class);
        }

        private static class Builder extends LegacyEventSourcingRepository.Builder<MyAggregate> {

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
