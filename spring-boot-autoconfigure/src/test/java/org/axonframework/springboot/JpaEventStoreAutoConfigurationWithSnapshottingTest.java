/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.utils.TestSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Primary;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating auto configured snapshotting logic.
 *
 * @author Steven van Beelen
 */
@ContextConfiguration(classes = JpaEventStoreAutoConfigurationWithSnapshottingTest.TestContext.class)
@ExtendWith(SpringExtension.class)
@EnableAutoConfiguration(exclude = {AxonServerAutoConfiguration.class})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class JpaEventStoreAutoConfigurationWithSnapshottingTest {

    private static final String AGGREGATE_ID = "some-aggregate";

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private CommandGateway commandGateway;
    @Autowired
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        TestContext.SNAPSHOT_FILTER_INVOKED.set(false);
    }

    @Test
    void testSnapshotterAndSnapshotTriggerDefinitionAreInvoked() {
        SnapshotTriggerDefinition snapshotTriggerDefinition =
                applicationContext.getBean(SnapshotTriggerDefinition.class);
        assertNotNull(snapshotTriggerDefinition);
        Snapshotter snapshotter = applicationContext.getBean(Snapshotter.class);
        assertNotNull(snapshotter);
        assertNotNull(applicationContext.getBean(JpaEventStorageEngine.class));

        commandGateway.send(new TestContext.CreateCommand(AGGREGATE_ID));
        commandGateway.send(new TestContext.UpdateCommand(AGGREGATE_ID));

        verify(snapshotTriggerDefinition, atLeastOnce()).prepareTrigger(TestContext.TestAggregate.class);
        verify(snapshotter, atLeastOnce()).scheduleSnapshot(TestContext.TestAggregate.class, AGGREGATE_ID);
    }

    @Test
    void testSnapshotFilterIsInvoked() {
        SnapshotFilter snapshotFilter = applicationContext.getBean(SnapshotFilter.class);
        assertNotNull(snapshotFilter);
        assertNotNull(applicationContext.getBean(JpaEventStorageEngine.class));

        commandGateway.send(new TestContext.CreateCommand(AGGREGATE_ID));
        commandGateway.send(new TestContext.UpdateCommand(AGGREGATE_ID));

        eventStore.readEvents(AGGREGATE_ID);

        assertTrue(TestContext.SNAPSHOT_FILTER_INVOKED.get());
    }

    @Configuration
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
            return TestSerializer.secureXStreamSerializer();
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
