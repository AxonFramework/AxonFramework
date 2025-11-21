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

package org.axonframework.config;

/**
 * Test class to validate the {@link AggregateConfigurer}'s inner workings.
 *
 * @author Steven van Beelen
 */
public class AggregateConfigurerTest {

//    private LegacyConfiguration mockConfiguration;
//    private LegacyEventStore testEventStore;
//    private final RevisionResolver revisionResolver = Mockito.mock(AnnotationRevisionResolver.class);
//
//    private AggregateConfigurer<TestAggregate> testSubject;
//
//    @BeforeEach
//    public void setUp() {
//        mockConfiguration = mock(LegacyConfiguration.class);
//
//        testEventStore = mock(LegacyEventStore.class);
//        when(mockConfiguration.eventBus()).thenReturn(testEventStore);
//        when(mockConfiguration.eventStore()).thenReturn(testEventStore);
//
//        ParameterResolverFactory testParameterResolverFactory = mock(ParameterResolverFactory.class);
//        when(mockConfiguration.parameterResolverFactory()).thenReturn(testParameterResolverFactory);
//        when(mockConfiguration.getComponent(eq(AggregateMetaModelFactory.class), any()))
//                .thenReturn(new AnnotatedAggregateMetaModelFactory(
//                        testParameterResolverFactory, new AnnotatedMessageHandlingMemberDefinition()
//                ));
//
//        when(revisionResolver.revisionOf(TestAggregate.class)).thenReturn("1.0");
//
//        testSubject = new AggregateConfigurer<>(TestAggregate.class);
//    }
//
//    @Test
//    @Disabled
//    void polymorphicConfig() {
//        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class)
//                                                                        .withSubtypes(B.class);
//
//        LegacyConfiguration configuration = LegacyDefaultConfigurer.defaultConfiguration()
//                                                                   .configureAggregate(aggregateConfigurer)
//                                                                   .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
//                                                                   .buildConfiguration();
//        configuration.start();
//
//        CommandGateway commandGateway = configuration.commandGateway();
//        String aggregateAId = commandGateway.send(new CreateACommand("123"), null, String.class)
//                                            .join();
//        String aggregateBId = commandGateway.send(new CreateBCommand("456"), null, String.class)
//                                            .join();
//        String result1 = commandGateway.send(new DoSomethingCommand(aggregateAId), null, String.class)
//                                       .join();
//        String result2 = commandGateway.send(new DoSomethingCommand(aggregateBId), null, String.class)
//                                       .join();
//        String result3 = commandGateway.send(new BSpecificCommand(aggregateBId), null, String.class)
//                                       .join();
//        assertEquals("A123", result1);
//        assertEquals("B456", result2);
//        assertEquals("bSpecific456", result3);
//
//        configuration.shutdown();
//    }
//
//    @Test
//    void aggregateFactoryConfiguration() {
//        AggregateFactory<TestAggregate> expectedAggregateFactory = new GenericAggregateFactory<>(TestAggregate.class);
//
//        testSubject.configureAggregateFactory(configuration -> expectedAggregateFactory);
//
//        assertEquals(expectedAggregateFactory, testSubject.aggregateFactory());
//    }
//
//    @Test
//    void snapshotFilterConfiguration() {
//        SnapshotFilter testFilter = snapshotData -> true;
//
//        testSubject.configureSnapshotFilter(configuration -> testFilter);
//
//        assertEquals(testFilter, testSubject.snapshotFilter());
//    }
//
//    @Test
//    void aggregateConfigurationCreatesRevisionSnapshotFilterForAggregateWithRevision() {
//        DomainEventMessage snapshotEvent = new GenericDomainEventMessage(
//                TestAggregateWithRevision.class.getName(), "some-aggregate-id", 0,
//                new MessageType("snapshot"), new TestAggregateWithRevision()
//        );
//        DomainEventData<byte[]> testDomainEventData =
//                new SnapshotEventEntry(snapshotEvent, JacksonSerializer.defaultSerializer());
//
//        AggregateConfigurer<TestAggregateWithRevision> revisionAggregateConfigurerTestSubject =
//                new AggregateConfigurer<>(TestAggregateWithRevision.class);
//
//        revisionAggregateConfigurerTestSubject.initialize(mockConfiguration);
//
//        SnapshotFilter result = revisionAggregateConfigurerTestSubject.snapshotFilter();
//
//        assertInstanceOf(RevisionSnapshotFilter.class, result);
//        assertTrue(result.allow(testDomainEventData));
//    }
//
//    @Test
//    void aggregateConfigurationThrowsAxonConfigExceptionWhenCreatingRevisionSnapshotFilterForUndefinedDeclaredType() {
//        //noinspection unchecked
//        AggregateModel<TestAggregateWithRevision> mockModel = mock(AggregateModel.class);
//        when(mockModel.declaredType(TestAggregateWithRevision.class)).thenReturn(Optional.empty());
//        AggregateMetaModelFactory mockModelFactory = mock(AggregateMetaModelFactory.class);
//        when(mockModelFactory.createModel(eq(TestAggregateWithRevision.class), any())).thenReturn(mockModel);
//        when(mockConfiguration.getComponent(eq(AggregateMetaModelFactory.class), any())).thenReturn(mockModelFactory);
//
//        AggregateConfigurer<TestAggregateWithRevision> undefinedDeclaredAggregateTypeTestSubject =
//                new AggregateConfigurer<>(TestAggregateWithRevision.class);
//
//        undefinedDeclaredAggregateTypeTestSubject.initialize(mockConfiguration);
//
//        assertThrows(AxonConfigurationException.class, undefinedDeclaredAggregateTypeTestSubject::snapshotFilter);
//    }
//
//    @Test
//    @Disabled
//    void configureLockFactoryForEventSourcedAggregate() {
//        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
//        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class)
//                                                                        .configureLockFactory(config -> lockFactory);
//
//        LegacyConfiguration config = LegacyDefaultConfigurer.defaultConfiguration()
//                                                            .configureAggregate(aggregateConfigurer)
//                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
//                                                            .buildConfiguration();
//        config.start();
//
//        CommandGateway commandGateway = config.commandGateway();
//        String testAggregateIdentifier = "123";
//        commandGateway.send(new CreateACommand(testAggregateIdentifier), null)
//                      .getResultMessage()
//                      .join();
//        verify(lockFactory).obtainLock(testAggregateIdentifier);
//
//        config.shutdown();
//    }
//
//    @Test
//    @Disabled
//    void configureSpanFactoryForEventSourcedAggregate() {
//        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class);
//
//        TestSpanFactory testSpanFactory = new TestSpanFactory();
//        LegacyConfiguration config = LegacyDefaultConfigurer.defaultConfiguration()
//                                                            .configureAggregate(aggregateConfigurer)
//                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
//                                                            .configureSpanFactory(c -> testSpanFactory)
//                                                            .buildConfiguration();
//        config.start();
//
//        CommandGateway commandGateway = config.commandGateway();
//        String testAggregateIdentifier = "123";
//        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
//
//        testSpanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
//
//        config.shutdown();
//    }
//
//    @Test
//    @Disabled
//    void configureLockFactoryForStateStoredAggregateWithConfiguredEntityManagerProviderComponent() {
//        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
//        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.jpaMappedConfiguration(A.class)
//                                                                        .configureLockFactory(config -> lockFactory);
//
//        LegacyConfiguration config = LegacyDefaultConfigurer.defaultConfiguration()
//                                                            .configureAggregate(aggregateConfigurer)
//                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
//                                                            .registerComponent(
//                                                                    EntityManagerProvider.class,
//                                                                    c -> new SimpleEntityManagerProvider(mock(
//                                                                            EntityManager.class))
//                                                            )
//                                                            .buildConfiguration();
//        config.start();
//
//        CommandGateway commandGateway = config.commandGateway();
//        String testAggregateIdentifier = "123";
//        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
//
//        config.shutdown();
//    }
//
//    @Test
//    @Disabled
//    void configureSpanFactoryForStateStoredAggregateWithConfiguredEntityManagerProviderComponent() {
//        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
//        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.jpaMappedConfiguration(A.class)
//                                                                        .configureLockFactory(config -> lockFactory);
//
//        TestSpanFactory testSpanFactory = new TestSpanFactory();
//        LegacyConfiguration config = LegacyDefaultConfigurer.defaultConfiguration()
//                                                            .configureAggregate(aggregateConfigurer)
//                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
//                                                            .registerComponent(
//                                                                    EntityManagerProvider.class,
//                                                                    c -> new SimpleEntityManagerProvider(mock(
//                                                                            EntityManager.class))
//                                                            )
//                                                            .configureSpanFactory(c -> testSpanFactory)
//                                                            .buildConfiguration();
//        config.start();
//
//        CommandGateway commandGateway = config.commandGateway();
//        String testAggregateIdentifier = "123";
//        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
//        testSpanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
//
//        config.shutdown();
//    }
//
//    @Test
//    @Disabled
//    void configureLockFactoryForStateStoredAggregate() {
//        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
//        AggregateConfigurer<A> aggregateConfigurer =
//                AggregateConfigurer.jpaMappedConfiguration(
//                                           A.class, new SimpleEntityManagerProvider(mock(EntityManager.class))
//                                   )
//                                   .configureLockFactory(config -> lockFactory);
//
//        LegacyConfiguration config = LegacyDefaultConfigurer.defaultConfiguration()
//                                                            .configureAggregate(aggregateConfigurer)
//                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
//                                                            .buildConfiguration();
//        config.start();
//
//        CommandGateway commandGateway = config.commandGateway();
//        String testAggregateIdentifier = "123";
//        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
//        verify(lockFactory).obtainLock(testAggregateIdentifier);
//
//        config.shutdown();
//    }
//
//    @Test
//    @Disabled
//    void configureSpanFactoryForStateStoredAggregate() {
//        AggregateConfigurer<A> aggregateConfigurer =
//                AggregateConfigurer.jpaMappedConfiguration(
//                        A.class, new SimpleEntityManagerProvider(mock(EntityManager.class))
//                );
//
//        TestSpanFactory testSpanFactory = new TestSpanFactory();
//        LegacyConfiguration config = LegacyDefaultConfigurer.defaultConfiguration()
//                                                            .configureAggregate(aggregateConfigurer)
//                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
//                                                            .configureSpanFactory(c -> testSpanFactory)
//                                                            .buildConfiguration();
//        config.start();
//
//        CommandGateway commandGateway = config.commandGateway();
//        String testAggregateIdentifier = "123";
//        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
//
//        testSpanFactory.verifySpanCompleted("CommandBus.dispatchCommand");
//
//        config.shutdown();
//    }
//
//    @Test
//    @Disabled("TODO 1769")
//    void nullRevisionEventAndNullRevisionAggregateAllowed() {
//        DomainEventMessage snapshotEvent = new GenericDomainEventMessage(
//                TestAggregate.class.getSimpleName(), "some-aggregate-id", 0,
//                new MessageType("snapshot"), new TestAggregate()
//        );
//
//        DomainEventData<byte[]> testDomainEventData =
//                new SnapshotEventEntry(snapshotEvent, JacksonSerializer.defaultSerializer());
//
//        AggregateConfigurer<TestAggregate> revisionAggregateConfigurerTestSubject =
//                new AggregateConfigurer<>(TestAggregate.class);
//
//        revisionAggregateConfigurerTestSubject.initialize(mockConfiguration);
//
//        SnapshotFilter result = revisionAggregateConfigurerTestSubject.snapshotFilter();
//
//        assertInstanceOf(RevisionSnapshotFilter.class, result);
//        assertTrue(result.allow(testDomainEventData));
//    }
//
//    @Test
//    @Disabled("TODO 1769")
//    void nonNullEventRevisionAndNullAggregateRevisionNotAllowed() {
//        DomainEventMessage snapshotEvent = new GenericDomainEventMessage(
//                TestAggregate.class.getSimpleName(), "some-aggregate-id", 0,
//                new MessageType("snapshot"), new TestAggregate()
//        );
//        Serializer serializer = JacksonSerializer.defaultSerializer();
//
//        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);
//
//        AggregateConfigurer<TestAggregate> revisionAggregateConfigurerTestSubject =
//                new AggregateConfigurer<>(TestAggregate.class);
//
//        revisionAggregateConfigurerTestSubject.initialize(mockConfiguration);
//
//        SnapshotFilter result = revisionAggregateConfigurerTestSubject.snapshotFilter();
//
//        assertInstanceOf(RevisionSnapshotFilter.class, result);
//        assertFalse(result.allow(testDomainEventData));
//    }
//
//    @Test
//    @Disabled
//    void configuredCreationPolicyAggregateFactoryIsUsedDuringAggregateConstruction() {
//        AtomicBoolean counter = new AtomicBoolean(false);
//
//        CreationPolicyAggregateFactory<A> testFactory = identifier -> {
//            counter.set(true);
//            A aggregateA = new A();
//            aggregateA.handle(identifier != null ? identifier.toString() : "null");
//            return aggregateA;
//        };
//
//        AggregateConfigurer<A> testAggregateConfigurer =
//                AggregateConfigurer.defaultConfiguration(A.class)
//                                   .configureCreationPolicyAggregateFactory(c -> testFactory);
//
//        LegacyConfiguration testConfig = LegacyDefaultConfigurer.defaultConfiguration()
//                                                                .configureAggregate(testAggregateConfigurer)
//                                                                .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
//                                                                .start();
//
//        CreationPolicyAggregateFactory<A> resultFactory = testAggregateConfigurer.creationPolicyAggregateFactory();
//        assertEquals(testFactory, resultFactory);
//
//        testConfig.commandGateway()
//                  .sendAndWait(new AlwaysCreationPolicyCommand("some-id"));
//        assertTrue(counter.get());
//    }
//
//    private static class TestAggregate {
//
//        TestAggregate() {
//            // No-op constructor
//        }
//    }
//
//    @Revision("some-revision")
//    private static class TestAggregateWithRevision {
//
//        TestAggregateWithRevision() {
//            // No-op constructor
//        }
//    }
//
//    private record CreateACommand(String id) {
//
//    }
//
//    private record AlwaysCreationPolicyCommand(@TargetAggregateIdentifier String id) {
//
//    }
//
//    private record ACreatedEvent(String id) {
//
//    }
//
//    private record CreateBCommand(String id) {
//
//    }
//
//    private record DoSomethingCommand(@TargetAggregateIdentifier String id) {
//
//    }
//
//    private record BSpecificCommand(@TargetAggregateIdentifier String id) {
//
//    }
//
//    private static class A {
//
//        @AggregateIdentifier
//        protected String id;
//
//        public A() {
//        }
//
//        @CommandHandler
//        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
//        public void handle(CreateACommand cmd) {
//            handle(cmd.id());
//        }
//
//        public void handle(String id) {
//            apply(new ACreatedEvent(id));
//        }
//
//        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
//        @CommandHandler
//        public void handle(AlwaysCreationPolicyCommand command) {
//            apply(new ACreatedEvent(id));
//        }
//
//        @EventSourcingHandler
//        public void on(ACreatedEvent evt) {
//            this.id = evt.id();
//        }
//
//        @CommandHandler
//        public String handle(DoSomethingCommand cmd) {
//            return this.getClass().getSimpleName() + cmd.id();
//        }
//
//        @SuppressWarnings("unused")
//        public String getId() {
//            return id;
//        }
//    }
//
//    private static class B extends A {
//
//        public B() {
//        }
//
//        @CommandHandler
//        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
//        public void handle(CreateBCommand cmd) {
//            super.handle(cmd.id());
//        }
//
//        @CommandHandler
//        public String handle(BSpecificCommand cmd) {
//            return "bSpecific" + cmd.id();
//        }
//    }
}
