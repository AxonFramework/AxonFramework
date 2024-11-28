/*
 * Copyright (c) 2010-2024. Axon Framework
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

import com.thoughtworks.xstream.XStream;
import jakarta.persistence.EntityManager;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.axonframework.eventsourcing.snapshotting.RevisionSnapshotFilter;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.CreationPolicyAggregateFactory;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.inspection.AggregateMetaModelFactory;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.CompactDriver;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.axonframework.config.utils.TestSerializer.xStreamSerializer;
import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
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
    private final RevisionResolver revisionResolver = Mockito.mock(AnnotationRevisionResolver.class);

    private AggregateConfigurer<TestAggregate> testSubject;

    @BeforeEach
    public void setUp() {
        mockConfiguration = mock(Configuration.class);

        testEventStore = mock(EventStore.class);
        when(mockConfiguration.eventBus()).thenReturn(testEventStore);
        when(mockConfiguration.eventStore()).thenReturn(testEventStore);

        ParameterResolverFactory testParameterResolverFactory = mock(ParameterResolverFactory.class);
        when(mockConfiguration.parameterResolverFactory()).thenReturn(testParameterResolverFactory);
        when(mockConfiguration.getComponent(eq(AggregateMetaModelFactory.class), any()))
                .thenReturn(new AnnotatedAggregateMetaModelFactory(
                        testParameterResolverFactory, new AnnotatedMessageHandlingMemberDefinition()
                ));

        when(revisionResolver.revisionOf(TestAggregate.class)).thenReturn("1.0");

        testSubject = new AggregateConfigurer<>(TestAggregate.class);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void polymorphicConfig() {
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class)
                                                                        .withSubtypes(B.class);

        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .configureAggregate(aggregateConfigurer)
                                                       .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                       .buildConfiguration();
        configuration.start();

        CommandGateway commandGateway = configuration.commandGateway();
        String aggregateAId = commandGateway.send(new CreateACommand("123"), ProcessingContext.NONE, String.class)
                                            .join();
        String aggregateBId = commandGateway.send(new CreateBCommand("456"), ProcessingContext.NONE, String.class)
                                            .join();
        String result1 = commandGateway.send(new DoSomethingCommand(aggregateAId), ProcessingContext.NONE, String.class)
                                       .join();
        String result2 = commandGateway.send(new DoSomethingCommand(aggregateBId), ProcessingContext.NONE, String.class)
                                       .join();
        String result3 = commandGateway.send(new BSpecificCommand(aggregateBId), ProcessingContext.NONE, String.class)
                                       .join();
        assertEquals("A123", result1);
        assertEquals("B456", result2);
        assertEquals("bSpecific456", result3);

        configuration.shutdown();
    }

    @Test
    void aggregateFactoryConfiguration() {
        AggregateFactory<TestAggregate> expectedAggregateFactory = new GenericAggregateFactory<>(TestAggregate.class);

        testSubject.configureAggregateFactory(configuration -> expectedAggregateFactory);

        assertEquals(expectedAggregateFactory, testSubject.aggregateFactory());
    }

    @Test
    void snapshotFilterConfiguration() {
        SnapshotFilter testFilter = snapshotData -> true;

        testSubject.configureSnapshotFilter(configuration -> testFilter);

        assertEquals(testFilter, testSubject.snapshotFilter());
    }

    @Test
    void aggregateConfigurationCreatesRevisionSnapshotFilterForAggregateWithRevision() {
        DomainEventMessage<TestAggregateWithRevision> snapshotEvent = new GenericDomainEventMessage<>(
                TestAggregateWithRevision.class.getName(), "some-aggregate-id", 0,
                QualifiedNameUtils.fromDottedName("test.snapshot"), new TestAggregateWithRevision()
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, xStreamSerializer());

        AggregateConfigurer<TestAggregateWithRevision> revisionAggregateConfigurerTestSubject =
                new AggregateConfigurer<>(TestAggregateWithRevision.class);

        revisionAggregateConfigurerTestSubject.initialize(mockConfiguration);

        SnapshotFilter result = revisionAggregateConfigurerTestSubject.snapshotFilter();

        assertInstanceOf(RevisionSnapshotFilter.class, result);
        assertTrue(result.allow(testDomainEventData));
    }

    @Test
    void aggregateConfigurationThrowsAxonConfigExceptionWhenCreatingRevisionSnapshotFilterForUndefinedDeclaredType() {
        //noinspection unchecked
        AggregateModel<TestAggregateWithRevision> mockModel = mock(AggregateModel.class);
        when(mockModel.declaredType(TestAggregateWithRevision.class)).thenReturn(Optional.empty());
        AggregateMetaModelFactory mockModelFactory = mock(AggregateMetaModelFactory.class);
        when(mockModelFactory.createModel(eq(TestAggregateWithRevision.class), any())).thenReturn(mockModel);
        when(mockConfiguration.getComponent(eq(AggregateMetaModelFactory.class), any())).thenReturn(mockModelFactory);

        AggregateConfigurer<TestAggregateWithRevision> undefinedDeclaredAggregateTypeTestSubject =
                new AggregateConfigurer<>(TestAggregateWithRevision.class);

        undefinedDeclaredAggregateTypeTestSubject.initialize(mockConfiguration);

        assertThrows(AxonConfigurationException.class, undefinedDeclaredAggregateTypeTestSubject::snapshotFilter);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void configureLockFactoryForEventSourcedAggregate() {
        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class)
                                                                        .configureLockFactory(config -> lockFactory);

        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.send(new CreateACommand(testAggregateIdentifier), ProcessingContext.NONE)
                      .getResultMessage()
                      .join();
        verify(lockFactory).obtainLock(testAggregateIdentifier);

        config.shutdown();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void configureSpanFactoryForEventSourcedAggregate() {
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class);

        TestSpanFactory testSpanFactory = new TestSpanFactory();
        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .configureSpanFactory(c -> testSpanFactory)
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));

        testSpanFactory.verifySpanCompleted("CommandBus.dispatchCommand");

        config.shutdown();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void configureLockFactoryForStateStoredAggregateWithConfiguredEntityManagerProviderComponent() {
        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.jpaMappedConfiguration(A.class)
                                                                        .configureLockFactory(config -> lockFactory);

        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .registerComponent(
                                                        EntityManagerProvider.class,
                                                        c -> new SimpleEntityManagerProvider(mock(EntityManager.class))
                                                )
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));

        config.shutdown();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void configureSpanFactoryForStateStoredAggregateWithConfiguredEntityManagerProviderComponent() {
        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.jpaMappedConfiguration(A.class)
                                                                        .configureLockFactory(config -> lockFactory);

        TestSpanFactory testSpanFactory = new TestSpanFactory();
        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .registerComponent(
                                                        EntityManagerProvider.class,
                                                        c -> new SimpleEntityManagerProvider(mock(EntityManager.class))
                                                )
                                                .configureSpanFactory(c -> testSpanFactory)
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
        testSpanFactory.verifySpanCompleted("CommandBus.dispatchCommand");

        config.shutdown();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void configureLockFactoryForStateStoredAggregate() {
        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
        AggregateConfigurer<A> aggregateConfigurer =
                AggregateConfigurer.jpaMappedConfiguration(
                                           A.class, new SimpleEntityManagerProvider(mock(EntityManager.class))
                                   )
                                   .configureLockFactory(config -> lockFactory);

        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
        verify(lockFactory).obtainLock(testAggregateIdentifier);

        config.shutdown();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void configureSpanFactoryForStateStoredAggregate() {
        AggregateConfigurer<A> aggregateConfigurer =
                AggregateConfigurer.jpaMappedConfiguration(
                        A.class, new SimpleEntityManagerProvider(mock(EntityManager.class))
                );

        TestSpanFactory testSpanFactory = new TestSpanFactory();
        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .configureSpanFactory(c -> testSpanFactory)
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));

        testSpanFactory.verifySpanCompleted("CommandBus.dispatchCommand");

        config.shutdown();
    }

    @Test
    void nullRevisionEventAndNullRevisionAggregateAllowed() {
        DomainEventMessage<TestAggregate> snapshotEvent = new GenericDomainEventMessage<>(
                TestAggregate.class.getSimpleName(), "some-aggregate-id", 0,
                QualifiedNameUtils.fromDottedName("test.snapshot"), new TestAggregate()
        );

        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, xStreamSerializer());

        AggregateConfigurer<TestAggregate> revisionAggregateConfigurerTestSubject =
                new AggregateConfigurer<>(TestAggregate.class);

        revisionAggregateConfigurerTestSubject.initialize(mockConfiguration);

        SnapshotFilter result = revisionAggregateConfigurerTestSubject.snapshotFilter();

        assertInstanceOf(RevisionSnapshotFilter.class, result);
        assertTrue(result.allow(testDomainEventData));
    }

    @Test
    void nonNullEventRevisionAndNullAggregateRevisionNotAllowed() {
        DomainEventMessage<TestAggregate> snapshotEvent = new GenericDomainEventMessage<>(
                TestAggregate.class.getSimpleName(), "some-aggregate-id", 0,
                QualifiedNameUtils.fromDottedName("test.snapshot"), new TestAggregate()
        );
        Serializer serializer = XStreamSerializer.builder()
                                                 .xStream(new XStream(new CompactDriver()))
                                                 .revisionResolver(revisionResolver)
                                                 .build();

        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        AggregateConfigurer<TestAggregate> revisionAggregateConfigurerTestSubject =
                new AggregateConfigurer<>(TestAggregate.class);

        revisionAggregateConfigurerTestSubject.initialize(mockConfiguration);

        SnapshotFilter result = revisionAggregateConfigurerTestSubject.snapshotFilter();

        assertInstanceOf(RevisionSnapshotFilter.class, result);
        assertFalse(result.allow(testDomainEventData));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void configuredCreationPolicyAggregateFactoryIsUsedDuringAggregateConstruction() {
        AtomicBoolean counter = new AtomicBoolean(false);

        CreationPolicyAggregateFactory<A> testFactory = identifier -> {
            counter.set(true);
            A aggregateA = new A();
            aggregateA.handle(identifier != null ? identifier.toString() : "null");
            return aggregateA;
        };

        AggregateConfigurer<A> testAggregateConfigurer =
                AggregateConfigurer.defaultConfiguration(A.class)
                                   .configureCreationPolicyAggregateFactory(c -> testFactory);

        Configuration testConfig = DefaultConfigurer.defaultConfiguration()
                                                    .configureAggregate(testAggregateConfigurer)
                                                    .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                    .start();

        CreationPolicyAggregateFactory<A> resultFactory = testAggregateConfigurer.creationPolicyAggregateFactory();
        assertEquals(testFactory, resultFactory);

        testConfig.commandGateway()
                  .sendAndWait(new AlwaysCreationPolicyCommand("some-id"));
        assertTrue(counter.get());
    }

    private static class TestAggregate {

        TestAggregate() {
            // No-op constructor
        }
    }

    @Revision("some-revision")
    private static class TestAggregateWithRevision {

        TestAggregateWithRevision() {
            // No-op constructor
        }
    }

    private record CreateACommand(String id) {

    }

    private record AlwaysCreationPolicyCommand(@TargetAggregateIdentifier String id) {

    }

    private record ACreatedEvent(String id) {

    }

    private record CreateBCommand(String id) {

    }

    private record DoSomethingCommand(@TargetAggregateIdentifier String id) {

    }

    private record BSpecificCommand(@TargetAggregateIdentifier String id) {

    }

    private static class A {

        @AggregateIdentifier
        protected String id;

        public A() {
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateACommand cmd) {
            handle(cmd.id());
        }

        public void handle(String id) {
            apply(new ACreatedEvent(id));
        }

        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        @CommandHandler
        public void handle(AlwaysCreationPolicyCommand command) {
            apply(new ACreatedEvent(id));
        }

        @EventSourcingHandler
        public void on(ACreatedEvent evt) {
            this.id = evt.id();
        }

        @CommandHandler
        public String handle(DoSomethingCommand cmd) {
            return this.getClass().getSimpleName() + cmd.id();
        }

        @SuppressWarnings("unused")
        public String getId() {
            return id;
        }
    }

    private static class B extends A {

        public B() {
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateBCommand cmd) {
            super.handle(cmd.id());
        }

        @CommandHandler
        public String handle(BSpecificCommand cmd) {
            return "bSpecific" + cmd.id();
        }
    }
}
