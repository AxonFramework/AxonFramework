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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.springboot.utils.TestSerializer;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating auto configured snapshotting logic.
 *
 * @author Steven van Beelen
 */
class JpaEventStoreAutoConfigurationWithSnapshottingTest {

    private static final String AGGREGATE_ID = "some-aggregate";

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        TestContext.SNAPSHOT_FILTER_INVOKED.set(false);
        testContext = new ApplicationContextRunner()
                .withPropertyValues("axon.axonserver.enabled=false")
                .withUserConfiguration(TestContext.class);
    }

    @Test
    void snapshotterAndSnapshotTriggerDefinitionAreInvoked() {
        testContext.run(context -> {
            SnapshotTriggerDefinition snapshotTriggerDefinition =
                    context.getBean(SnapshotTriggerDefinition.class);
            assertNotNull(snapshotTriggerDefinition);
            Snapshotter snapshotter = context.getBean(Snapshotter.class);
            assertNotNull(snapshotter);
            assertNotNull(context.getBean(JpaEventStorageEngine.class));

            CommandGateway commandGateway = context.getBean(CommandGateway.class);
            commandGateway.send(new TestContext.CreateCommand(AGGREGATE_ID), ProcessingContext.NONE);
            commandGateway.send(new TestContext.UpdateCommand(AGGREGATE_ID), ProcessingContext.NONE);

            verify(snapshotTriggerDefinition, atLeastOnce()).prepareTrigger(TestContext.TestAggregate.class);
            verify(snapshotter, atLeastOnce()).scheduleSnapshot(TestContext.TestAggregate.class, AGGREGATE_ID);
        });
    }

    @Test
    void snapshotFilterIsInvoked() {
        testContext.run(context -> {
            SnapshotFilter snapshotFilter = context.getBean(SnapshotFilter.class);
            assertNotNull(snapshotFilter);
            assertNotNull(context.getBean(JpaEventStorageEngine.class));

            CommandGateway commandGateway = context.getBean(CommandGateway.class);
            commandGateway.send(new TestContext.CreateCommand(AGGREGATE_ID), ProcessingContext.NONE);
            commandGateway.send(new TestContext.UpdateCommand(AGGREGATE_ID), ProcessingContext.NONE);

            EventStore eventStore = context.getBean(EventStore.class);
            eventStore.readEvents(AGGREGATE_ID);

            assertTrue(TestContext.SNAPSHOT_FILTER_INVOKED.get());
        });
    }

    @Configuration
    @EnableAutoConfiguration
    protected static class TestContext {

        protected static final AtomicBoolean SNAPSHOT_FILTER_INVOKED = new AtomicBoolean(false);

        @Bean
        public Snapshotter snapshotter(EventStore eventStore, TransactionManager transactionManager) {
            return spy(AggregateSnapshotter.builder()
                                           .aggregateFactories(new GenericAggregateFactory<>(TestAggregate.class))
                                           .eventStore(eventStore)
                                           .transactionManager(transactionManager)
                                           .build());
        }

        @Bean
        public SnapshotTriggerDefinition snapshotTriggerDefinition(Snapshotter snapshotter) {
            return spy(new EventCountSnapshotTriggerDefinition(snapshotter, 1));
        }

        @Bean
        public SnapshotFilter snapshotFilter() {
            return snapshotData -> {
                SNAPSHOT_FILTER_INVOKED.set(true);
                return true;
            };
        }

        @Bean
        @Primary
        public Serializer serializer() {
            return TestSerializer.xStreamSerializer();
        }

        public static class CreateCommand {

            @TargetAggregateIdentifier
            private final String aggregateIdentifier;

            public CreateCommand(String aggregateIdentifier) {
                this.aggregateIdentifier = aggregateIdentifier;
            }

            public String getAggregateIdentifier() {
                return aggregateIdentifier;
            }
        }

        public static class CreatedEvent {

            private final String aggregateIdentifier;

            public CreatedEvent(String aggregateIdentifier) {
                this.aggregateIdentifier = aggregateIdentifier;
            }

            public String getAggregateIdentifier() {
                return aggregateIdentifier;
            }
        }

        public static class UpdateCommand {

            @TargetAggregateIdentifier
            private final String aggregateIdentifier;

            public UpdateCommand(String aggregateIdentifier) {
                this.aggregateIdentifier = aggregateIdentifier;
            }

            public String getAggregateIdentifier() {
                return aggregateIdentifier;
            }
        }


        @SuppressWarnings("unused")
        public static class UpdatedEvent {

            private final String aggregateIdentifier;

            public UpdatedEvent(String aggregateIdentifier) {
                this.aggregateIdentifier = aggregateIdentifier;
            }

            public String getAggregateIdentifier() {
                return aggregateIdentifier;
            }
        }

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        @Aggregate(snapshotTriggerDefinition = "snapshotTriggerDefinition", snapshotFilter = "snapshotFilter")
        public static class TestAggregate {

            @AggregateIdentifier
            private String aggregateIdentifier;

            public TestAggregate() {
                // Required default constructor
            }

            @CommandHandler
            public TestAggregate(CreateCommand command) {
                AggregateLifecycle.apply(new CreatedEvent(command.getAggregateIdentifier()));
            }

            @CommandHandler
            public void handle(UpdateCommand command) {
                AggregateLifecycle.apply(new UpdatedEvent(command.getAggregateIdentifier()));
            }

            @EventHandler
            public void on(CreatedEvent event) {
                aggregateIdentifier = event.getAggregateIdentifier();
            }
        }
    }
}
