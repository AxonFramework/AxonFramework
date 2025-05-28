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

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Id;
import jakarta.persistence.Persistence;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.common.caching.WeakReferenceCache;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.utils.TestSerializer;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.axonframework.deadline.quartz.QuartzDeadlineManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.async.FullConcurrencyPolicy;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.LegacyCachingEventSourcingRepository;
import org.axonframework.eventsourcing.LegacyEventSourcingRepository;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.AbstractSnapshotEventEntry;
import org.axonframework.eventsourcing.eventstore.LegacyEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.LegacyInMemoryEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.DomainEventEntry;
import org.axonframework.eventsourcing.eventstore.jpa.LegacyJpaEventStorageEngine;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.interceptors.TransactionManagingInterceptor;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.LegacyGenericJpaRepository;
import org.axonframework.modelling.command.VersionedAggregateIdentifier;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.annotation.QueryHandler;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanFactory;
import org.junit.jupiter.api.*;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.axonframework.config.AggregateConfigurer.defaultConfiguration;
import static org.axonframework.config.AggregateConfigurer.jpaMappedConfiguration;
import static org.axonframework.config.ConfigAssertions.assertExpectedModules;
import static org.axonframework.config.utils.AssertUtils.assertRetryingWithin;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating several {@link LegacyDefaultConfigurer} operations.
 *
 * @author Allard Buijze
 */
class DefaultConfigurerTest {

    private static final GenericCommandMessage<String> TEST_COMMAND =
            new GenericCommandMessage<>(new MessageType("command"), "test");

    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManagerFactory = Persistence.createEntityManagerFactory("defaultConfigurerJpaTestEventStore");
        entityManager = entityManagerFactory.createEntityManager();
    }

    @AfterEach
    void tearDown() {
        entityManager.close();
        entityManagerFactory.close();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void defaultConfigurationWithEventSourcing() throws Exception {
        LegacyConfiguration config =
                LegacyDefaultConfigurer.defaultConfiguration()
                                       .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                       .configureCommandBus(c -> new SimpleCommandBus(c.getComponent(
                                               Executor.class,
                                               Executors::newSingleThreadExecutor)
                                       ))
                                       .configureAggregate(StubAggregate.class)
                                       .buildConfiguration();
        config.start();

        var result = config.commandBus().dispatch(TEST_COMMAND, null);
        assertEquals("test", result.get().getPayload());
        assertNotNull(config.repository(StubAggregate.class));
        assertEquals(LegacyEventSourcingRepository.class, config.repository(StubAggregate.class).getClass());
        assertEquals(2, config.getModules().size());
        assertExpectedModules(config, AggregateConfiguration.class, AxonIQConsoleModule.class);
    }

    @Test
    void defaultConfigurationWithTrackingProcessorConfigurationInMainConfig() {
        LegacyConfigurer configurer = LegacyDefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing().registerEventHandler(c -> (EventMessageHandler) (event, ctx) -> null);
        LegacyConfiguration config = configurer.registerComponent(
                                                       TrackingEventProcessorConfiguration.class,
                                                       c -> TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                               )
                                               .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                               .start();
        try {
            TrackingEventProcessor processor = config.eventProcessingConfiguration()
                                                     .eventProcessor(getClass().getPackage().getName(),
                                                                     TrackingEventProcessor.class).orElseThrow(
                            RuntimeException::new);
            assertRetryingWithin(Duration.ofSeconds(5),
                                 () -> assertEquals(2,
                                                    config.getComponent(TokenStore.class)
                                                          .fetchSegments(processor.getName()).length));
        } finally {
            config.shutdown();
        }
    }

    @Test
    void defaultConfigurationWithTrackingProcessorExplicitlyConfigured() {
        LegacyConfigurer configurer = LegacyDefaultConfigurer.defaultConfiguration();
        String processorName = "myProcessor";
        configurer.eventProcessing()
                  .registerTrackingEventProcessor(processorName,
                                                  LegacyConfiguration::eventStore,
                                                  c -> TrackingEventProcessorConfiguration.forParallelProcessing(2))
                  .byDefaultAssignTo(processorName)
                  .registerDefaultSequencingPolicy(c -> new FullConcurrencyPolicy())
                  .registerEventHandler(c -> (EventMessageHandler) (event, ctx) -> null);
        LegacyConfiguration config = configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                               .start();
        try {
            TrackingEventProcessor processor = config.eventProcessingConfiguration().eventProcessor(processorName,
                                                                                                    TrackingEventProcessor.class)
                                                     .orElseThrow(RuntimeException::new);
            assertRetryingWithin(Duration.ofSeconds(5),
                                 () -> assertEquals(2,
                                                    config.getComponent(TokenStore.class)
                                                          .fetchSegments(processor.getName()).length));
        } finally {
            config.shutdown();
        }
    }

    @Test
    void defaultConfigurationWithTrackingProcessorAutoStartDisabledDoesNotComplainAtShutdown() {
        LegacyConfigurer configurer = LegacyDefaultConfigurer.defaultConfiguration();
        String processorName = "myProcessor";
        configurer.eventProcessing()
                  .registerTrackingEventProcessor(processorName,
                                                  LegacyConfiguration::eventStore,
                                                  c -> TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                                                          .andAutoStart(false))
                  .byDefaultAssignTo(processorName)
                  .registerDefaultSequencingPolicy(c -> new FullConcurrencyPolicy())
                  .registerEventHandler(c -> (EventMessageHandler) (event, ctx) -> null);
        LegacyConfiguration config = configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                               .start();

        TrackingEventProcessor processor = config.eventProcessingConfiguration().eventProcessor(processorName,
                                                                                                TrackingEventProcessor.class)
                                                 .orElseThrow(RuntimeException::new);
        try {
            assertFalse(processor.isRunning());
        } finally {
            assertDoesNotThrow(config::shutdown);
        }
    }

    @Test
    void defaultConfigurationWithTrackingProcessorAutoStartDisabled() {
        LegacyConfigurer configurer = LegacyDefaultConfigurer.defaultConfiguration();
        String processorName = "myProcessor";
        configurer.eventProcessing()
                  .registerTrackingEventProcessor(processorName,
                                                  LegacyConfiguration::eventStore,
                                                  c -> TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                                                          .andAutoStart(false))
                  .byDefaultAssignTo(processorName)
                  .registerDefaultSequencingPolicy(c -> new FullConcurrencyPolicy())
                  .registerEventHandler(c -> (EventMessageHandler) (event, ctx) -> null);
        LegacyConfiguration config = configurer.configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                               .start();
        try {
            TrackingEventProcessor processor = config.eventProcessingConfiguration().eventProcessor(processorName,
                                                                                                    TrackingEventProcessor.class)
                                                     .orElseThrow(RuntimeException::new);
            assertFalse(processor.isRunning());
            processor.start();
            assertTrue(processor.isRunning());
        } finally {
            config.shutdown();
        }
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void defaultConfigurationWithUpcaster() {
        AtomicInteger counter = new AtomicInteger();
        LegacyConfiguration config =
                LegacyDefaultConfigurer.defaultConfiguration()
                                       .configureEmbeddedEventStore(
                                               c -> LegacyJpaEventStorageEngine.builder()
                                                                               .snapshotSerializer(c.serializer())
                                                                               .upcasterChain(c.upcasterChain())
                                                                               .persistenceExceptionResolver(c.getComponent(
                                                                                       PersistenceExceptionResolver.class
                                                                               ))
                                                                               .entityManagerProvider(() -> entityManager)
                                                                               .transactionManager(c.getComponent(
                                                                                       TransactionManager.class
                                                                               ))
                                                                               .eventSerializer(c.serializer())
                                                                               .build())
                                       .configureAggregate(
                                               defaultConfiguration(StubAggregate.class)
                                                       .configureCommandTargetResolver(
                                                               c -> command -> new VersionedAggregateIdentifier(
                                                                       command.getPayload(), null
                                                               )
                                                       )
                                       )
                                       .registerEventUpcaster(c -> events -> {
                                           counter.incrementAndGet();
                                           return events;
                                       })
                                       .configureTransactionManager(c -> new EntityManagerTransactionManager(
                                               entityManager
                                       ))
                                       .configureSerializer(configuration -> TestSerializer.xStreamSerializer())
                                       .buildConfiguration();

        config.start();

        config.commandGateway()
              .sendAndWait(TEST_COMMAND);
        CommandMessage<String> testCommand = new GenericCommandMessage<>(
                new GenericMessage<>(new MessageType("update"), "test")
        );
        config.commandGateway().sendAndWait(testCommand);
        assertEquals(1, counter.get());
        assertNotNull(config.repository(StubAggregate.class));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void jpaConfigurationWithInitialTransactionManagerJpaRepository() throws Exception {
        EntityManagerTransactionManager transactionManager = spy(new EntityManagerTransactionManager(entityManager));
        LegacyConfiguration config =
                LegacyDefaultConfigurer.jpaConfiguration(() -> entityManager, transactionManager)
                                       .configureCommandBus(c -> {
                                           SimpleCommandBus commandBus = new SimpleCommandBus(Runnable::run);
                                           return new InterceptingCommandBus(
                                                   commandBus,
                                                   List.of(new TransactionManagingInterceptor<>(c.getComponent(
                                                           TransactionManager.class
                                                   ))),
                                                   Collections.emptyList()
                                           );
                                       })
                                       .configureAggregate(
                                               defaultConfiguration(StubAggregate.class)
                                                       .configureRepository(
                                                               c -> LegacyGenericJpaRepository.builder(StubAggregate.class)
                                                                                              .entityManagerProvider(new SimpleEntityManagerProvider(
                                                                                                      entityManager
                                                                                              ))
                                                                                              .eventBus(c.eventBus())
                                                                                              .parameterResolverFactory(
                                                                                                      c.parameterResolverFactory())
                                                                                              .build()
                                                       )
                                       )
                                       .configureSerializer(c -> TestSerializer.xStreamSerializer())
                                       .buildConfiguration();

        config.start();
        var result = config.commandBus().dispatch(TEST_COMMAND, null);
        assertEquals("test", result.get().getPayload());
        assertNotNull(config.repository(StubAggregate.class));
        assertEquals(2, config.getModules().size());
        assertExpectedModules(config, AggregateConfiguration.class, AxonIQConsoleModule.class);

        verify(transactionManager, times(2)).startTransaction();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void jpaConfigurationWithInitialTransactionManagerJpaRepositoryFromConfiguration() throws Exception {
        EntityManagerTransactionManager transactionManager = spy(new EntityManagerTransactionManager(entityManager));
        LegacyConfiguration config =
                LegacyDefaultConfigurer.jpaConfiguration(() -> entityManager, transactionManager)
                                       .configureSerializer(c -> TestSerializer.xStreamSerializer())
                                       .configureCommandBus(c -> new InterceptingCommandBus(
                                               new SimpleCommandBus(Runnable::run),
                                               List.of(new TransactionManagingInterceptor<>(c.getComponent(
                                                       TransactionManager.class
                                               ))),
                                               Collections.emptyList()))
                                       .configureAggregate(jpaMappedConfiguration(StubAggregate.class))
                                       .buildConfiguration();

        config.start();
        var result = config.commandBus().dispatch(TEST_COMMAND, null);
        assertEquals("test", result.get().getPayload());
        assertNotNull(config.repository(StubAggregate.class));
        assertTrue(config.getModules().stream().anyMatch(m -> m instanceof AggregateConfiguration));

        verify(transactionManager, times(2)).startTransaction();
    }

    @Test
    void missingEntityManagerProviderIsReported() {
        LegacyConfiguration config =
                LegacyDefaultConfigurer.defaultConfiguration()
                                       .configureCommandBus(c -> new InterceptingCommandBus(
                                               new SimpleCommandBus(Runnable::run),
                                               List.of(new TransactionManagingInterceptor<>(c.getComponent(
                                                       TransactionManager.class
                                               ))),
                                               Collections.emptyList()
                                       ))
                                       .configureAggregate(jpaMappedConfiguration(StubAggregate.class))
                                       .buildConfiguration();

        assertThrows(LifecycleHandlerInvocationException.class, config::start);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void jpaConfigurationWithJpaRepository() throws Exception {
        EntityManagerTransactionManager transactionManager = spy(new EntityManagerTransactionManager(entityManager));
        LegacyConfiguration config =
                LegacyDefaultConfigurer.jpaConfiguration(() -> entityManager)
                                       .registerComponent(TransactionManager.class, c -> transactionManager)
                                       .configureCommandBus(c -> new InterceptingCommandBus(
                                               new SimpleCommandBus(Runnable::run),
                                               List.of(new TransactionManagingInterceptor<>(c.getComponent(
                                                       TransactionManager.class
                                               ))),
                                               Collections.emptyList())
                                       )
                                       .configureAggregate(defaultConfiguration(StubAggregate.class).configureRepository(
                                               c -> LegacyGenericJpaRepository.builder(StubAggregate.class)
                                                                              .entityManagerProvider(new SimpleEntityManagerProvider(
                                                                                      entityManager
                                                                              ))
                                                                              .eventBus(c.eventBus())
                                                                              .parameterResolverFactory(c.parameterResolverFactory())
                                                                              .build())
                                       )
                                       .configureSerializer(c -> TestSerializer.xStreamSerializer())
                                       .buildConfiguration();

        config.start();
        var result = config.commandBus().dispatch(TEST_COMMAND, null);
        assertEquals("test", result.get().getPayload());
        assertNotNull(config.repository(StubAggregate.class));
        assertEquals(2, config.getModules().size());
        assertExpectedModules(config, AggregateConfiguration.class, AxonIQConsoleModule.class);

        verify(transactionManager, times(2)).startTransaction();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void defaultConfigurationWithMonitors() throws Exception {
        MessageCollectingMonitor defaultMonitor = new MessageCollectingMonitor();
        MessageCollectingMonitor commandBusMonitor = new MessageCollectingMonitor();

        LegacyConfiguration config = LegacyDefaultConfigurer.defaultConfiguration()
                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                                            .configureAggregate(StubAggregate.class)
                                                            .configureMessageMonitor(c -> (t, n) -> defaultMonitor)
                                                            .configureMessageMonitor(
                                                                    CommandBus.class,
                                                                    "commandBus",
                                                                    c -> commandBusMonitor
                                                            )
                                                            .buildConfiguration();
        config.start();

        var result = config.commandBus().dispatch(TEST_COMMAND, null);
        assertEquals("test", result.get().getPayload());
        assertEquals(1, defaultMonitor.getMessages().size());
        assertEquals(1, commandBusMonitor.getMessages().size());
    }

    @Test
    @Disabled("#3360 Broken due to ConfigurationParameterResolverFactory")
    void registerSeveralModules() {
        LegacyConfiguration config = LegacyDefaultConfigurer.defaultConfiguration()
                                                            .configureAggregate(StubAggregate.class)
                                                            .configureAggregate(Object.class)
                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                                            .start();

        assertEquals(3, config.getModules().size());
        assertExpectedModules(config,
                              AggregateConfiguration.class,
                              AggregateConfiguration.class,
                              AxonIQConsoleModule.class);
    }

    @Test
    void queryUpdateEmitterConfigurationPropagatedToTheQueryBus() {
        QueryUpdateEmitter queryUpdateEmitter = SimpleQueryUpdateEmitter.builder().build();
        LegacyConfiguration configuration = LegacyDefaultConfigurer.defaultConfiguration()
                                                                   .configureQueryUpdateEmitter(c -> queryUpdateEmitter)
                                                                   .buildConfiguration();
        assertEquals(queryUpdateEmitter, configuration.queryBus().queryUpdateEmitter());
        assertEquals(queryUpdateEmitter, configuration.queryUpdateEmitter());
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void defaultConfigurationWithCache() throws Exception {
        LegacyConfiguration config = LegacyDefaultConfigurer.defaultConfiguration()
                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                                            .configureCommandBus(c -> new SimpleCommandBus(Runnable::run))
                                                            .configureAggregate(defaultConfiguration(StubAggregate.class).configureCache(
                                                                    c -> new WeakReferenceCache()
                                                            ))
                                                            .buildConfiguration();
        config.start();

        var result = config.commandBus().dispatch(TEST_COMMAND, null);
        assertEquals("test", result.get().getPayload());
        assertNotNull(config.repository(StubAggregate.class));
        assertEquals(LegacyCachingEventSourcingRepository.class, config.repository(StubAggregate.class).getClass());
    }

    @Test
    @Disabled("#3360 Broken due to ConfigurationParameterResolverFactory")
    void configuredSnapshotterDefaultsToAggregateSnapshotter() {
        Snapshotter defaultSnapshotter = LegacyDefaultConfigurer.jpaConfiguration(() -> entityManager)
                                                                .configureSerializer(
                                                                        configuration -> TestSerializer.xStreamSerializer()
                                                                )
                                                                .configureAggregate(StubAggregate.class)
                                                                .buildConfiguration().snapshotter();

        assertInstanceOf(AggregateSnapshotter.class, defaultSnapshotter);
    }

    @Test
    void defaultSnapshotterDefaultsToNoOpWhenNoAggregatesAreKnown() {
        Snapshotter defaultSnapshotter =
                LegacyDefaultConfigurer.jpaConfiguration(() -> entityManager)
                                       .configureSerializer(configuration -> TestSerializer.xStreamSerializer())
                                       .buildConfiguration().snapshotter();

        assertFalse(defaultSnapshotter instanceof AggregateSnapshotter);
    }

    @Test
    @Disabled("#3360 Broken due to ConfigurationParameterResolverFactory")
    void configureSnapshotterSetsCustomSnapshotter() {
        Snapshotter expectedSnapshotter = mock(Snapshotter.class);

        AggregateConfigurer<StubAggregate> aggregateConfigurer = defaultConfiguration(StubAggregate.class).configureSnapshotTrigger(
                configuration -> {
                    Snapshotter resultSnapshotter = configuration.snapshotter();
                    assertEquals(expectedSnapshotter, resultSnapshotter);
                    return new EventCountSnapshotTriggerDefinition(resultSnapshotter, 42);
                });

        LegacyConfiguration result = LegacyDefaultConfigurer.defaultConfiguration()
                                                            .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                                            .configureSnapshotter(configuration -> expectedSnapshotter)
                                                            .configureAggregate(aggregateConfigurer)
                                                            .buildConfiguration();
        result.start();

        assertEquals(expectedSnapshotter, result.snapshotter());
        assertEquals(expectedSnapshotter, result.getComponent(Snapshotter.class));
    }

    @Test
    void configurationSnapshotFilterContainsConfiguredSnapshotFilters() {
        AtomicBoolean filteredFirst = new AtomicBoolean(false);
        SnapshotFilter testFilterOne = snapshotData -> {
            filteredFirst.set(true);
            return true;
        };
        AggregateConfigurer<StubAggregate> aggregateConfigurerOne = AggregateConfigurer.defaultConfiguration(
                StubAggregate.class).configureSnapshotFilter(configuration -> testFilterOne);

        AtomicBoolean filteredSecond = new AtomicBoolean(false);
        SnapshotFilter testFilterTwo = snapshotData -> {
            filteredSecond.set(true);
            return false;
        };
        AggregateConfigurer<StubAggregate> aggregateConfigurerTwo = AggregateConfigurer.defaultConfiguration(
                StubAggregate.class).configureSnapshotFilter(configuration -> testFilterTwo);

        LegacyConfiguration resultConfig = LegacyDefaultConfigurer.defaultConfiguration()
                                                                  .configureAggregate(aggregateConfigurerOne)
                                                                  .configureAggregate(aggregateConfigurerTwo)
                                                                  .buildConfiguration();

        SnapshotFilter snapshotFilter = resultConfig.snapshotFilter();
        boolean result = snapshotFilter.allow(mock(DomainEventData.class));
        assertFalse(result);
        assertTrue(filteredFirst.get());
        assertTrue(filteredSecond.get());
    }

    @Test
    void aggregateSnapshotFilterIsAddedToTheEventStore() {
        AtomicBoolean filteredFirst = new AtomicBoolean(false);
        SnapshotFilter testFilterOne = snapshotData -> {
            filteredFirst.set(true);
            return true;
        };
        AggregateConfigurer<StubAggregate> aggregateConfigurerOne = AggregateConfigurer.defaultConfiguration(
                StubAggregate.class).configureSnapshotFilter(configuration -> testFilterOne);

        AtomicBoolean filteredSecond = new AtomicBoolean(false);
        SnapshotFilter testFilterTwo = snapshotData -> {
            filteredSecond.set(true);
            return true;
        };
        AggregateConfigurer<StubAggregate> aggregateConfigurerTwo = AggregateConfigurer.defaultConfiguration(
                StubAggregate.class).configureSnapshotFilter(configuration -> testFilterTwo);

        Serializer serializer = TestSerializer.xStreamSerializer();
        EntityManagerTransactionManager transactionManager = spy(new EntityManagerTransactionManager(entityManager));

        DomainEventMessage<String> testDomainEvent = new GenericDomainEventMessage<>(
                "StubAggregate", "some-aggregate-id", 0, new MessageType("event"), "some-payload"
        );
        DomainEventData<byte[]> snapshotData =
                new AbstractSnapshotEventEntry<>(testDomainEvent, serializer, byte[].class) {
                };
        DomainEventData<byte[]> domainEventData = new DomainEventEntry(testDomainEvent, serializer);
        // Firstly snapshot data will be retrieved (and filtered), secondly event data.
        doReturn(Stream.of(snapshotData), Collections.singletonList(domainEventData)).when(transactionManager)
                                                                                     .fetchInTransaction(any());

        LegacyConfiguration resultConfig = LegacyDefaultConfigurer.jpaConfiguration(() -> entityManager)
                                                                  .configureSerializer(
                                                                          configuration -> serializer
                                                                  )
                                                                  .configureTransactionManager(configuration -> transactionManager)
                                                                  .configureAggregate(aggregateConfigurerOne)
                                                                  .configureAggregate(aggregateConfigurerTwo)
                                                                  .buildConfiguration();

        LegacyEventStore resultEventStore = resultConfig.eventStore();
        resultEventStore.readEvents("some-aggregate-id");

        assertTrue(filteredFirst.get());
        assertTrue(filteredSecond.get());
    }

    @Test
    void defaultConfiguredDeadlineManager() {
        DeadlineManager result = LegacyDefaultConfigurer.defaultConfiguration().buildConfiguration().deadlineManager();

        assertInstanceOf(SimpleDeadlineManager.class, result);
    }

    @Test
    void customConfiguredDeadlineManager() throws SchedulerException {
        Scheduler mockScheduler = mock(Scheduler.class);
        when(mockScheduler.getContext()).thenReturn(mock(SchedulerContext.class));

        DeadlineManager result =
                LegacyDefaultConfigurer.defaultConfiguration()
                                       .configureDeadlineManager(
                                               config -> QuartzDeadlineManager.builder()
                                                                              .scheduler(mockScheduler)
                                                                              .scopeAwareProvider(config.scopeAwareProvider())
                                                                              .serializer(TestSerializer.xStreamSerializer())
                                                                              .build()
                                       )
                                       .buildConfiguration().deadlineManager();

        assertInstanceOf(QuartzDeadlineManager.class, result);
    }

    @Test
    void defaultConfiguredSpanFactory() {
        SpanFactory result = LegacyDefaultConfigurer.defaultConfiguration().buildConfiguration().spanFactory();

        assertInstanceOf(NoOpSpanFactory.class, result);
    }

    @Test
    void customConfiguredSpanFactory() {
        SpanFactory custom = mock(SpanFactory.class);

        SpanFactory result = LegacyDefaultConfigurer.defaultConfiguration().configureSpanFactory((config) -> custom)
                                                    .buildConfiguration().spanFactory();

        assertSame(custom, result);
    }

    @Test
    void defaultConfiguredScopeAwareProvider() {
        ScopeAwareProvider result = LegacyDefaultConfigurer.defaultConfiguration().buildConfiguration()
                                                           .scopeAwareProvider();

        assertInstanceOf(ConfigurationScopeAwareProvider.class, result);
    }

    @Test
    void whenStubAggregateRegisteredWithRegisterMessageHandler_thenRightThingsCalled() {
        LegacyConfigurer configurer = spy(LegacyDefaultConfigurer.defaultConfiguration());
        configurer.registerMessageHandler(c -> new StubAggregate());

        verify(configurer, times(1)).registerCommandHandler(any());
        verify(configurer, times(1)).eventProcessing();
        verify(configurer, never()).registerQueryHandler(any());
    }

    @Test
    void whenQueryHandlerRegisteredWithRegisterMessageHandler_thenRightThingsCalled() {
        LegacyConfigurer configurer = spy(LegacyDefaultConfigurer.defaultConfiguration());
        configurer.registerMessageHandler(c -> new StubQueryHandler());

        verify(configurer, never()).registerCommandHandler(any());
        verify(configurer, never()).eventProcessing();
        verify(configurer, times(1)).registerQueryHandler(any());
    }

    @Test
    void registeringNoUpcastersAndNoUpcasterChainComponentReturnsEmptyUpcasterChain() {
        //noinspection unchecked
        Stream<IntermediateEventRepresentation> mockStream = mock(Stream.class);

        EventUpcasterChain result = LegacyDefaultConfigurer.defaultConfiguration().buildConfiguration().upcasterChain();

        result.upcast(mockStream);
        // Since the upcaster chain is empty, the Stream is not interacted with.
        verifyNoInteractions(mockStream);
    }

    @Test
    void registeringUpcastersReturnsUpcasterChainOverRulingRegisteredUpcasterChainComponent() {
        //noinspection unchecked
        Stream<IntermediateEventRepresentation> mockStream = mock(Stream.class);

        AtomicBoolean firstUpcasterInvocation = new AtomicBoolean(false);
        EventUpcaster firstUpcaster = mock(EventUpcaster.class);
        when(firstUpcaster.upcast(any())).thenAnswer(it -> {
            firstUpcasterInvocation.set(true);
            return it.getArgument(0);
        });
        AtomicBoolean secondUpcasterInvocation = new AtomicBoolean(false);
        EventUpcaster secondUpcaster = mock(EventUpcaster.class);
        when(secondUpcaster.upcast(any())).thenAnswer(it -> {
            secondUpcasterInvocation.set(true);
            return it.getArgument(0);
        });
        EventUpcasterChain testUpcasterChain = mock(EventUpcasterChain.class);

        EventUpcasterChain result = LegacyDefaultConfigurer.defaultConfiguration()
                                                           .registerEventUpcaster(c -> firstUpcaster)
                                                           .registerEventUpcaster(c -> secondUpcaster)
                                                           .registerComponent(
                                                                   EventUpcasterChain.class,
                                                                   c -> testUpcasterChain
                                                           )
                                                           .buildConfiguration()
                                                           .upcasterChain();

        result.upcast(mockStream);

        assertTrue(firstUpcasterInvocation.get());
        assertTrue(secondUpcasterInvocation.get());
        verifyNoInteractions(testUpcasterChain);
    }

    @Test
    void shuttingDownTheConfigurationBeforeItStartedWithConfiguredMessageHandlersDoesNotCauseAnyExceptions() {
        LegacyConfiguration configuration = LegacyDefaultConfigurer.defaultConfiguration()
                                                                   .registerCommandHandler(c -> new Object())
                                                                   .registerEventHandler(c -> new Object())
                                                                   .registerQueryHandler(c -> new Object())
                                                                   .registerMessageHandler(c -> new Object())
                                                                   .buildConfiguration();
        assertDoesNotThrow(configuration::shutdown);
    }

    @SuppressWarnings("unused") @Entity(name = "StubAggregate") private static class StubAggregate {

        @SuppressWarnings("FieldCanBeLocal") @Id @AggregateIdentifier private String id;

        public StubAggregate() {
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(String command, CommandBus commandBus) {
            apply(command);
        }

        public static StubAggregate create(String command, CommandBus commandBus) {
            StubAggregate aggregate = new StubAggregate();
            aggregate.handle(command, commandBus);
            return aggregate;
        }

        @CommandHandler(commandName = "update")
        public void update(String command) {
            apply(1L);
        }

        @EventSourcingHandler
        protected void on(String event) {
            this.id = event;
        }
    }

    private static class StubQueryHandler {

        @QueryHandler
        public String handle(String query) {
            return "foo";
        }
    }

    private static class EntityManagerTransactionManager implements TransactionManager {

        private final EntityManager em;

        public EntityManagerTransactionManager(EntityManager em) {
            this.em = em;
        }

        @Override
        public Transaction startTransaction() {
            EntityTransaction tx = em.getTransaction();
            if (tx.isActive()) {
                return new Transaction() {
                    @Override
                    public void commit() {
                    }

                    @Override
                    public void rollback() {
                    }
                };
            }
            tx.begin();
            return new Transaction() {
                @Override
                public void commit() {
                    tx.commit();
                }

                @Override
                public void rollback() {
                    tx.rollback();
                }
            };
        }
    }
}
