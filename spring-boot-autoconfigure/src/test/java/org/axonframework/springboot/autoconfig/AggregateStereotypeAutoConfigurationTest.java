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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.common.Registration;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.lock.Lock;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.GenericJpaRepository;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.VersionedAggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating whether the {@link org.axonframework.spring.stereotype.Aggregate} stereotype annotation with
 * the configurable bean names sets an Aggregate correctly.
 *
 * @author Steven van Beelen
 */
class AggregateStereotypeAutoConfigurationTest {

    private static final String AGGREGATE_IDENTIFIER = "aggregateIdentifier";

    private static AtomicBoolean snapshotFilterInvoked;
    private static AtomicBoolean commandTargetResolverInvoked;
    private static AtomicBoolean cacheInvoked;
    private static AtomicBoolean lockFactoryInvoked;

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        snapshotFilterInvoked = new AtomicBoolean(false);
        commandTargetResolverInvoked = new AtomicBoolean(false);
        cacheInvoked = new AtomicBoolean(false);
        lockFactoryInvoked = new AtomicBoolean(false);

        testApplicationContext = new ApplicationContextRunner().withUserConfiguration(TestContext.class)
                                                               .withPropertyValues("axon.axonserver.enabled:false");
    }

    @Test
    void aggregateStereotypeConfiguration() {
        testApplicationContext.run(context -> {
            // Publish the first command to create the TestAggregate
            CommandGateway commandGateway = context.getBean(DefaultCommandGateway.class);
            String aggregateId = commandGateway.sendAndWait(new CreateTestAggregate());

            SnapshotTriggerDefinition snapshotTriggerDefinition =
                    context.getBean("testSnapshotTriggerDefinition", SnapshotTriggerDefinition.class);
            verify(snapshotTriggerDefinition).prepareTrigger(TestContext.TestAggregate.class);
            assertTrue(cacheInvoked.get());
            assertTrue(lockFactoryInvoked.get());

            // Publish the second command to trigger the SnapshotFilter and CommandTargetResolver
            commandGateway.sendAndWait(new UpdateTestAggregate(aggregateId));

            assertTrue(snapshotFilterInvoked.get());
            assertTrue(commandTargetResolverInvoked.get());

            EventStore eventStore = context.getBean("eventBus", EventStore.class);
            assertTrue(eventStore.readEvents(aggregateId)
                                 .asStream()
                                 .allMatch(event -> Objects.equals(event.getType(), "testType")));
        });
    }

    /**
     * By configuring a custom {@link Repository} through the {@link Aggregate} stereotype, you remove the bean
     * definitions of the {@link SnapshotTriggerDefinition}, {@link Cache}, and the {@link LockFactory}. This holds as
     * the framework directly configures these components on the {@code Repository}. This test also asserts that the
     * {@link SnapshotFilter} is <b>not</b> invoked, since the {@code SnapshotTriggerDefinition} is no longer defined on
     * the {@code Repository}.
     */
    @Test
    void aggregateStereotypeWithCustomizedRepository() {
        testApplicationContext.run(context -> {
            // Publish the first command to create the TestAggregate
            CommandGateway commandGateway = context.getBean(DefaultCommandGateway.class);
            String aggregateId = commandGateway.sendAndWait(new CreateCustomRepoTestAggregate());

            //noinspection unchecked
            Repository<TestContext.CustomRepoTestAggregate> testRepository =
                    context.getBean("testRepository", Repository.class);
            verify(testRepository).newInstance(any());

            SnapshotTriggerDefinition snapshotTriggerDefinition =
                    context.getBean("testSnapshotTriggerDefinition", SnapshotTriggerDefinition.class);
            verifyNoInteractions(snapshotTriggerDefinition);
            assertFalse(cacheInvoked.get());
            assertFalse(lockFactoryInvoked.get());

            // Publish the second command to trigger the SnapshotFilter and CommandTargetResolver
            commandGateway.sendAndWait(new UpdateCustomRepoTestAggregate(aggregateId));

            verify(testRepository).load(aggregateId, null);
            assertTrue(commandTargetResolverInvoked.get());

            verifyNoInteractions(snapshotTriggerDefinition);
            assertFalse(snapshotFilterInvoked.get());
            assertFalse(cacheInvoked.get());
            assertFalse(lockFactoryInvoked.get());

            EventStore eventStore = context.getBean("eventBus", EventStore.class);
            assertTrue(eventStore.readEvents(aggregateId)
                                 .asStream()
                                 .allMatch(event -> Objects.equals(event.getType(), "testTypeWithCustomRepository")));
        });
    }

    @Test
    void aggregateWithEntityManagerAnnotationIsAutoconfiguredWitDefaultJpaRepository() {
        testApplicationContext.run(context -> {
            String beanName = context.getBeanNamesForType(TestContext.SimpleStateStoredAggregate.class)[0];
            assertTrue(context.containsBean(beanName + "Repository"));
            Object actual = context.getBean(beanName + "Repository");
            assertTrue(actual instanceof GenericJpaRepository, "Expected Jpa repository to have been configured");
        });
    }

    @Test
    void aggregateWithEntityManagerAnnotationIsAutoconfiguredWitExistingJpaRepository() {
        String beanName = "org.axonframework.springboot.autoconfig.AggregateStereotypeAutoConfigurationTest$TestContext$SimpleStateStoredAggregate";
        Repository mockRepo = mock(Repository.class);
        testApplicationContext.withBean(beanName + "Repository", Repository.class, () -> mockRepo)
                              .run(context -> {
                                  assertTrue(context.containsBean(beanName + "Repository"));
                                  Object actual = context.getBean(beanName + "Repository");
                                  assertSame(mockRepo, actual, "Expected defined repository to have been configured");
                              });
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestContext {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        @Aggregate(
                snapshotTriggerDefinition = "testSnapshotTriggerDefinition",
                snapshotFilter = "testSnapshotFilter",
                type = "testType",
                commandTargetResolver = "testCommandTargetResolver",
                cache = "testCache",
                lockFactory = "testLockFactory"
        )
        public static class TestAggregate {

            @AggregateIdentifier
            private String aggregateId;

            @CommandHandler
            public TestAggregate(CreateTestAggregate cmd) {
                apply(new TestAggregateCreated(cmd.getAggregateId()));
                // Publish multiple events to hit the snapshot event count when configured.
                apply(new TestAggregateUpdated(cmd.getAggregateId()));
                apply(new TestAggregateUpdated(cmd.getAggregateId()));
                apply(new TestAggregateUpdated(cmd.getAggregateId()));
            }

            @CommandHandler
            public void handle(UpdateTestAggregate command) {
                // Do nothing
            }

            @EventSourcingHandler
            public void on(TestAggregateCreated event) {
                aggregateId = event.getAggregateId();
            }

            private TestAggregate() {
                // Required by AggregateFactory
            }
        }

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        @Aggregate(
                repository = "testRepository",
                snapshotTriggerDefinition = "testSnapshotTriggerDefinition",
                snapshotFilter = "testSnapshotFilter",
                type = "testTypeWithCustomRepository",
                commandTargetResolver = "testCommandTargetResolver",
                cache = "testCache",
                lockFactory = "testLockFactory"
        )
        private static class CustomRepoTestAggregate {

            @AggregateIdentifier
            private String aggregateId;

            @CommandHandler
            public CustomRepoTestAggregate(CreateCustomRepoTestAggregate cmd) {
                apply(new CustomRepoTestAggregateCreated(cmd.getAggregateId()));
                // Publish multiple events to hit the snapshot event count when configured.
                apply(new CustomRepoTestAggregateUpdated(cmd.getAggregateId()));
                apply(new CustomRepoTestAggregateUpdated(cmd.getAggregateId()));
                apply(new CustomRepoTestAggregateUpdated(cmd.getAggregateId()));
            }

            @CommandHandler
            public void handle(UpdateCustomRepoTestAggregate command) {
                // Do nothing
            }

            @EventSourcingHandler
            public void on(CustomRepoTestAggregateCreated event) {
                aggregateId = event.getAggregateId();
            }

            private CustomRepoTestAggregate() {
                // Required by AggregateFactory
            }
        }

        @Entity(name = "simpleAggregate")
        @Aggregate
        private static class SimpleStateStoredAggregate {

            @Id
            private String aggregateId;

            public SimpleStateStoredAggregate() {
                // required for JPA
            }

            @CommandHandler
            public SimpleStateStoredAggregate(CreateStateStoredAggregateCommand command) {
                this.aggregateId = command.getAggregateId();
            }
        }

        @Bean
        public SnapshotTriggerDefinition testSnapshotTriggerDefinition(Snapshotter snapshotter) {
            return spy(new EventCountSnapshotTriggerDefinition(snapshotter, 3));
        }

        @Bean
        public SnapshotFilter testSnapshotFilter() {
            return domainEventData -> {
                snapshotFilterInvoked.set(true);
                return false;
            };
        }

        @Bean
        public CommandTargetResolver testCommandTargetResolver() {
            return command -> {
                commandTargetResolverInvoked.set(true);
                return new VersionedAggregateIdentifier(AGGREGATE_IDENTIFIER, null);
            };
        }

        @Bean
        public Cache testCache() {
            return new Cache() {
                @Override
                public <K, V> V get(K key) {
                    return null;
                }

                @Override
                public void put(Object key, Object value) {
                    cacheInvoked.set(true);
                }

                @Override
                public boolean putIfAbsent(Object key, Object value) {
                    return false;
                }

                @Override
                public boolean remove(Object key) {
                    return false;
                }

                @Override
                public void removeAll() {
                    // Do nothing.
                }

                @Override
                public boolean containsKey(Object key) {
                    return false;
                }

                @Override
                public Registration registerCacheEntryListener(EntryListener cacheEntryListener) {
                    return null;
                }

                @Override
                public <V> void computeIfPresent(Object key, UnaryOperator<V> update) {
                    // Do nothing.
                }
            };
        }

        @Bean
        public LockFactory testLockFactory() {
            return identifier -> {
                lockFactoryInvoked.set(true);
                return new Lock() {
                    @Override
                    public void release() {
                        // Do nothing
                    }

                    @Override
                    public boolean isHeld() {
                        return true;
                    }
                };
            };
        }

        @Bean
        public Repository<CustomRepoTestAggregate> testRepository(EventStore eventStore) {
            return spy(EventSourcingRepository.builder(CustomRepoTestAggregate.class)
                                              .eventStore(eventStore)
                                              .build());
        }
    }

    static class CreateStateStoredAggregateCommand {

        final String aggregateId;

        CreateStateStoredAggregateCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    static class CreateTestAggregate {

        private final String aggregateId;

        CreateTestAggregate() {
            this(AGGREGATE_IDENTIFIER);
        }

        CreateTestAggregate(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    static class TestAggregateCreated {

        private final String aggregateId;

        TestAggregateCreated(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    @SuppressWarnings("unused")
    static class UpdateTestAggregate {

        @TargetAggregateIdentifier
        private final String aggregateId;

        UpdateTestAggregate() {
            this(AGGREGATE_IDENTIFIER);
        }

        UpdateTestAggregate(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    @SuppressWarnings("unused")
    static class TestAggregateUpdated {

        private final String aggregateId;

        TestAggregateUpdated(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    static class CreateCustomRepoTestAggregate {

        private final String aggregateId;

        CreateCustomRepoTestAggregate() {
            this(AGGREGATE_IDENTIFIER);
        }

        CreateCustomRepoTestAggregate(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    static class CustomRepoTestAggregateCreated {

        private final String aggregateId;

        CustomRepoTestAggregateCreated(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    @SuppressWarnings("unused")
    static class UpdateCustomRepoTestAggregate {

        @TargetAggregateIdentifier
        private final String aggregateId;

        UpdateCustomRepoTestAggregate() {
            this(AGGREGATE_IDENTIFIER);
        }

        UpdateCustomRepoTestAggregate(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    @SuppressWarnings("unused")
    static class CustomRepoTestAggregateUpdated {

        private final String aggregateId;

        CustomRepoTestAggregateUpdated(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }
}
