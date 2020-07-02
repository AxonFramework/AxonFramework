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

package org.axonframework.config;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.disruptor.commandhandling.DisruptorCommandBus;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.NoSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.inspection.AggregateMetaModelFactory;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class to validate the {@link AggregateConfigurer}'s inner workings.
 *
 * @author Steven van Beelen
 */
public class AggregateConfigurerTest {

    private Configuration mockConfiguration;

    private EventStore testEventStore;
    private ParameterResolverFactory testParameterResolverFactory;

    private AggregateConfigurer<TestAggregate> testSubject;

    @BeforeEach
    public void setUp() {
        mockConfiguration = mock(Configuration.class);

        testEventStore = mock(EventStore.class);
        when(mockConfiguration.eventBus()).thenReturn(testEventStore);
        when(mockConfiguration.eventStore()).thenReturn(testEventStore);

        testParameterResolverFactory = mock(ParameterResolverFactory.class);
        when(mockConfiguration.parameterResolverFactory()).thenReturn(testParameterResolverFactory);
        when(mockConfiguration.getComponent(eq(AggregateMetaModelFactory.class), any()))
                .thenReturn(new AnnotatedAggregateMetaModelFactory(testParameterResolverFactory,
                                                                   new AnnotatedMessageHandlingMemberDefinition()));

        testSubject = new AggregateConfigurer<>(TestAggregate.class);
    }

    @Test
    void testConfiguredDisruptorCommandBusCreatesTheRepository() {
        //noinspection unchecked
        Repository<Object> expectedRepository = mock(Repository.class);

        DisruptorCommandBus disruptorCommandBus = mock(DisruptorCommandBus.class);
        when(disruptorCommandBus.createRepository(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedRepository);
        when(mockConfiguration.commandBus()).thenReturn(disruptorCommandBus);

        testSubject.initialize(mockConfiguration);

        Repository<TestAggregate> resultRepository = testSubject.repository();

        assertEquals(expectedRepository, resultRepository);
        //noinspection unchecked
        verify(disruptorCommandBus).createRepository(
                eq(testEventStore), isA(GenericAggregateFactory.class), eq(NoSnapshotTriggerDefinition.INSTANCE),
                eq(testParameterResolverFactory), any(), any()
        );
    }

    @Test
    void testConfiguredDisruptorCommandBusAsLocalSegmentCreatesTheRepository() {
        //noinspection unchecked
        Repository<Object> expectedRepository = mock(Repository.class);

        DisruptorCommandBus disruptorCommandBus = mock(DisruptorCommandBus.class);
        when(disruptorCommandBus.createRepository(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedRepository);
        DistributedCommandBus distributedCommandBusImplementation = mock(DistributedCommandBus.class);
        when(distributedCommandBusImplementation.localSegment()).thenReturn(disruptorCommandBus);
        when(mockConfiguration.commandBus()).thenReturn(distributedCommandBusImplementation);

        testSubject.initialize(mockConfiguration);

        Repository<TestAggregate> resultRepository = testSubject.repository();

        assertEquals(expectedRepository, resultRepository);
        //noinspection unchecked
        verify(disruptorCommandBus).createRepository(
                eq(testEventStore), isA(GenericAggregateFactory.class), eq(NoSnapshotTriggerDefinition.INSTANCE),
                eq(testParameterResolverFactory), any(), any()
        );
    }

    @Test
    void testPolymorphicConfig() {
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class)
                                                                        .withSubtypes(B.class);

        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .configureAggregate(aggregateConfigurer)
                                                       .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                       .buildConfiguration();
        configuration.start();

        CommandGateway commandGateway = configuration.commandGateway();
        String aggregateAId = commandGateway.sendAndWait(new CreateACommand("123"));
        String aggregateBId = commandGateway.sendAndWait(new CreateBCommand("456"));
        String result1 = commandGateway.sendAndWait(new DoSomethingCommand(aggregateAId));
        String result2 = commandGateway.sendAndWait(new DoSomethingCommand(aggregateBId));
        String result3 = commandGateway.sendAndWait(new BSpecificCommand(aggregateBId));
        assertEquals("A123", result1);
        assertEquals("B456", result2);
        assertEquals("bSpecific456", result3);

        configuration.shutdown();
    }

    @Test
    void testAggregateFactoryConfiguration() {
        AggregateFactory<TestAggregate> expectedAggregateFactory = new GenericAggregateFactory<>(TestAggregate.class);

        testSubject.configureAggregateFactory(configuration -> expectedAggregateFactory);

        assertEquals(expectedAggregateFactory, testSubject.aggregateFactory());
    }

    @Test
    void testSnapshotFilterConfiguration() {
        SnapshotFilter testFilter = snapshotData -> true;

        testSubject.configureSnapshotFilter(configuration -> testFilter);

        assertEquals(testFilter, testSubject.snapshotFilter());
    }

    private static class TestAggregate {

        TestAggregate() {
            // No-op constructor
        }
    }

    private static class CreateACommand {

        private final String id;

        private CreateACommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class ACreatedEvent {

        private final String id;

        private ACreatedEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class CreateBCommand {

        private final String id;

        private CreateBCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class DoSomethingCommand {

        @TargetAggregateIdentifier
        private final String id;

        private DoSomethingCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class BSpecificCommand {

        @TargetAggregateIdentifier
        private final String id;

        private BSpecificCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class A {

        @AggregateIdentifier
        protected String id;

        public A() {
        }

        @CommandHandler
        public A(CreateACommand cmd) {
            this(cmd.getId());
        }

        public A(String id) {
            apply(new ACreatedEvent(id));
        }

        @EventSourcingHandler
        public void on(ACreatedEvent evt) {
            this.id = evt.getId();
        }

        @CommandHandler
        public String handle(DoSomethingCommand cmd) {
            return this.getClass().getSimpleName() + cmd.getId();
        }
    }

    private static class B extends A {

        public B() {
        }

        @CommandHandler
        public B(CreateBCommand cmd) {
            super(cmd.getId());
        }

        @CommandHandler
        public String handle(BSpecificCommand cmd) {
            return "bSpecific" + cmd.getId();
        }
    }
}