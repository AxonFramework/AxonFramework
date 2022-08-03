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

package org.axonframework.disruptor.commandhandling;

import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolution;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.disruptor.commandhandling.utils.MockException;
import org.axonframework.disruptor.commandhandling.utils.SomethingDoneEvent;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.SnapshotTrigger;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlerInvocationException;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.AnnotationCommandTargetResolver;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.saga.SagaScopeDescriptor;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.lang.reflect.Executable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import static java.util.Collections.singletonList;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.disruptor.commandhandling.utils.AssertUtils.assertWithin;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DisruptorCommandBus}.
 *
 * @author Allard Buijze
 */
class DisruptorCommandBusTest {

    private static final int COMMAND_COUNT = 100 * 1000;
    private static final String COMMAND_RETURN_VALUE = "dummyVal";

    private static AtomicInteger messageHandlingCounter;

    private StubHandler stubHandler;
    private InMemoryEventStore eventStore;
    private String aggregateIdentifier;
    private TransactionManager mockTransactionManager;
    private ParameterResolverFactory parameterResolverFactory;

    private DisruptorCommandBus testSubject;

    @BeforeEach
    void setUp() {
        aggregateIdentifier = UUID.randomUUID().toString();
        stubHandler = new StubHandler();
        eventStore = new InMemoryEventStore();
        eventStore.publish(singletonList(
                new GenericDomainEventMessage<>("StubAggregate", aggregateIdentifier, 0, new StubDomainEvent())
        ));
        parameterResolverFactory = spy(ClasspathParameterResolverFactory.forClass(DisruptorCommandBusTest.class));
        messageHandlingCounter = new AtomicInteger(0);

        testSubject = DisruptorCommandBus.builder()
                                         .coolingDownPeriod(1000)
                                         .commandTargetResolver(AnnotationCommandTargetResolver.builder().build())
                                         .build();
    }

    @AfterEach
    void tearDown() {
        testSubject.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCallbackInvokedBeforeUnitOfWorkCleanup() throws Exception {
        MessageHandlerInterceptor<CommandMessage<?>> mockHandlerInterceptor = mock(MessageHandlerInterceptor.class);
        MessageDispatchInterceptor<CommandMessage<?>> mockDispatchInterceptor = mock(MessageDispatchInterceptor.class);
        when(mockDispatchInterceptor.handle(isA(CommandMessage.class))).thenAnswer(new Parameter(0));
        ExecutorService customExecutor = Executors.newCachedThreadPool();

        testSubject = DisruptorCommandBus.builder()
                                         .dispatchInterceptors(singletonList(mockDispatchInterceptor))
                                         .invokerInterceptors(singletonList(mockHandlerInterceptor))
                                         .bufferSize(8)
                                         .producerType(ProducerType.SINGLE)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .executor(customExecutor)
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        GenericAggregateFactory<StubAggregate> aggregateFactory = new GenericAggregateFactory<>(StubAggregate.class);
        stubHandler.setRepository(testSubject.createRepository(eventStore, aggregateFactory, parameterResolverFactory));
        Consumer<UnitOfWork<CommandMessage<?>>> mockPrepareCommitConsumer = mock(Consumer.class);
        Consumer<UnitOfWork<CommandMessage<?>>> mockAfterCommitConsumer = mock(Consumer.class);
        Consumer<UnitOfWork<CommandMessage<?>>> mockCleanUpConsumer = mock(Consumer.class);
        when(mockHandlerInterceptor.handle(any(UnitOfWork.class), any(InterceptorChain.class)))
                .thenAnswer(invocation -> {
                    final UnitOfWork<CommandMessage<?>> unitOfWork = (UnitOfWork<CommandMessage<?>>) invocation
                            .getArguments()[0];
                    unitOfWork.onPrepareCommit(mockPrepareCommitConsumer);
                    unitOfWork.afterCommit(mockAfterCommitConsumer);
                    unitOfWork.onCleanup(mockCleanUpConsumer);
                    return ((InterceptorChain) invocation.getArguments()[1]).proceed();
                });
        CommandMessage<StubCommand> command = asCommandMessage(new StubCommand(aggregateIdentifier));
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        testSubject.dispatch(command, mockCallback);

        testSubject.stop();
        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        InOrder inOrder = inOrder(mockDispatchInterceptor, mockHandlerInterceptor, mockPrepareCommitConsumer,
                                  mockAfterCommitConsumer, mockCleanUpConsumer, mockCallback);
        inOrder.verify(mockDispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(mockHandlerInterceptor).handle(any(UnitOfWork.class), any(InterceptorChain.class));
        inOrder.verify(mockPrepareCommitConsumer).accept(isA(UnitOfWork.class));
        inOrder.verify(mockAfterCommitConsumer).accept(isA(UnitOfWork.class));
        inOrder.verify(mockCleanUpConsumer).accept(isA(UnitOfWork.class));

        verify(mockCallback).onResult(eq(command), any());
    }

    @Test
    void testPublishUnsupportedCommand() {
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        //noinspection unchecked
        CommandCallback<Object, Object> callback = mock(CommandCallback.class);
        testSubject = DisruptorCommandBus.builder()
                                         .bufferSize(8)
                                         .producerType(ProducerType.SINGLE)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .executor(customExecutor)
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .defaultCommandCallback(callback)
                                         .build();
        testSubject.dispatch(asCommandMessage("Test"));
        customExecutor.shutdownNow();
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<?>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        Throwable exceptionResult = commandResultMessageCaptor.getValue().exceptionResult();
        assertEquals(NoHandlerForCommandException.class, exceptionResult.getClass());
        assertTrue(exceptionResult.getMessage().contains(String.class.getSimpleName()));
    }

    @Test
    void testEventStreamsDecoratedOnReadAndWrite() throws InterruptedException {
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        testSubject = DisruptorCommandBus.builder()
                                         .bufferSize(8)
                                         .producerType(ProducerType.SINGLE)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .executor(customExecutor)
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);

        SnapshotTriggerDefinition snapshotTriggerDefinition = mock(SnapshotTriggerDefinition.class);
        SnapshotTrigger snapshotTrigger = mock(SnapshotTrigger.class);
        when(snapshotTriggerDefinition.prepareTrigger(any())).thenReturn(snapshotTrigger);

        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                             snapshotTriggerDefinition));

        CommandMessage<StubCommand> command = asCommandMessage(new StubCommand(aggregateIdentifier));
        //noinspection unchecked
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        testSubject.dispatch(command, mockCallback);
        testSubject.dispatch(command);

        testSubject.stop();
        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));

        // invoked 3 times, 1 during initialization, and 2 for the executed commands
        InOrder inOrder = inOrder(snapshotTrigger);
        inOrder.verify(snapshotTrigger).eventHandled(isA(DomainEventMessage.class));
        inOrder.verify(snapshotTrigger).initializationFinished();
        inOrder.verify(snapshotTrigger, times(2)).eventHandled(isA(DomainEventMessage.class));
    }

    @Test
    void usesProvidedParameterResolverFactoryToResolveParameters() {
        testSubject = DisruptorCommandBus.builder().build();
        testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        verify(parameterResolverFactory, atLeastOnce()).createInstance(
                isA(Executable.class), isA(java.lang.reflect.Parameter[].class), anyInt()
        );
        verifyNoMoreInteractions(parameterResolverFactory);
    }

    @Test
    void testEventPublicationExecutedWithinTransaction() throws Exception {
        //noinspection unchecked
        MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor = mock(MessageHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        Transaction mockTransaction = mock(Transaction.class);
        mockTransactionManager = mock(TransactionManager.class);
        when(mockTransactionManager.startTransaction()).thenReturn(mockTransaction);

        dispatchCommands(mockInterceptor, customExecutor, asCommandMessage(new ErrorCommand(aggregateIdentifier)));

        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));

        verify(mockTransactionManager, times(991)).startTransaction();
        verify(mockTransaction, times(991)).commit();
        verifyNoMoreInteractions(mockTransaction, mockTransactionManager);
    }

    @Test
    @Timeout(value = 10)
    void testAggregatesBlacklistedAndRecoveredOnError_WithAutoReschedule() throws Exception {
        //noinspection unchecked
        MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor = mock(MessageHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        CommandCallback<Object, Object> mockCallback = dispatchCommands(
                mockInterceptor, customExecutor, asCommandMessage(new ErrorCommand(aggregateIdentifier))
        );
        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback, times(1000)).onResult(any(), commandResultMessageCaptor.capture());
        assertEquals(10, commandResultMessageCaptor.getAllValues()
                                                   .stream()
                                                   .filter(ResultMessage::isExceptional)
                                                   .count());
    }

    @Test
    @Timeout(value = 10)
    void testAggregatesBlacklistedAndRecoveredOnError_WithoutReschedule() throws Exception {
        //noinspection unchecked
        MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor = mock(MessageHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();

        CommandCallback<Object, Object> mockCallback = dispatchCommands(
                mockInterceptor, customExecutor, asCommandMessage(new ErrorCommand(aggregateIdentifier))
        );

        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback, times(1000)).onResult(any(), commandResultMessageCaptor.capture());
        assertEquals(10, commandResultMessageCaptor.getAllValues()
                                                   .stream()
                                                   .filter(ResultMessage::isExceptional)
                                                   .count());
    }

    private CommandCallback<Object, Object> dispatchCommands(
            MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor,
            ExecutorService customExecutor,
            CommandMessage<ErrorCommand> errorCommand
    ) throws Exception {
        eventStore.storedEvents.clear();

        testSubject = DisruptorCommandBus.builder()
                                         .invokerInterceptors(singletonList(mockInterceptor))
                                         .bufferSize(8)
                                         .producerType(ProducerType.MULTI)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .executor(customExecutor)
                                         .rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE)
                                         .transactionManager(mockTransactionManager)
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(ErrorCommand.class.getName(), stubHandler);
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );

        //noinspection unchecked
        when(mockInterceptor.handle(any(UnitOfWork.class), any(InterceptorChain.class))).thenAnswer(
                invocation -> ((InterceptorChain) invocation.getArguments()[1]).proceed()
        );
        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));
        //noinspection unchecked
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        for (int t = 0; t < 1000; t++) {
            CommandMessage<?> command;
            if (t % 100 == 10) {
                command = errorCommand;
            } else {
                command = asCommandMessage(new StubCommand(aggregateIdentifier));
            }
            testSubject.dispatch(command, mockCallback);
        }

        testSubject.stop();
        return mockCallback;
    }

    @Test
    void testCreateAggregate() {
        eventStore.storedEvents.clear();
        testSubject = DisruptorCommandBus.builder()
                                         .bufferSize(8)
                                         .producerType(ProducerType.SINGLE)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(ErrorCommand.class.getName(), stubHandler);
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );

        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage<?> lastEvent = eventStore.storedEvents.get(aggregateIdentifier);

        // we expect 2 events, 1 from aggregate constructor, one from doSomething method invocation
        assertEquals(1, lastEvent.getSequenceNumber());
        assertEquals(aggregateIdentifier, lastEvent.getAggregateIdentifier());
    }

    @Test
    void testCreateOrUpdateAggregateWithPreviousAggregate() {
        eventStore.storedEvents.clear();
        testSubject = DisruptorCommandBus.builder()
                                         .bufferSize(8)
                                         .producerType(ProducerType.SINGLE)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .build();

        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateOrUpdateCommand.class.getName(), stubHandler);
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );

        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new CreateOrUpdateCommand(aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage<?> lastEvent = eventStore.storedEvents.get(aggregateIdentifier);

        // we expect 3 events, 1 from aggregate constructor, 2 from doSomething method invocation
        assertEquals(2, lastEvent.getSequenceNumber());
        assertEquals(aggregateIdentifier, lastEvent.getAggregateIdentifier());
    }


    @Test
    void testCreateOrUpdateAggregateWithoutPreviousAggregate() {
        eventStore.storedEvents.clear();
        testSubject = DisruptorCommandBus.builder()
                                         .bufferSize(8)
                                         .producerType(ProducerType.SINGLE)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .build();

        testSubject.subscribe(CreateOrUpdateCommand.class.getName(), stubHandler);
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );

        testSubject.dispatch(asCommandMessage(new CreateOrUpdateCommand(aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage<?> lastEvent = eventStore.storedEvents.get(aggregateIdentifier);

        // we expect 2 events, 1 from aggregate constructor, one from doSomething method invocation
        assertEquals(1, lastEvent.getSequenceNumber());
        assertEquals(aggregateIdentifier, lastEvent.getAggregateIdentifier());
    }

    @Test
    void testCommandReturnsAValue() {
        eventStore.storedEvents.clear();
        testSubject = DisruptorCommandBus.builder()
                                         .bufferSize(8)
                                         .producerType(ProducerType.SINGLE)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .build();
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );

        FutureCallback<Object, Object> futureCallback = new FutureCallback<>();

        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)), futureCallback);
        testSubject.stop();

        assertEquals(COMMAND_RETURN_VALUE, futureCallback.getResult().getPayload());
    }

    @Test
    void testMessageMonitoring() {
        eventStore.storedEvents.clear();
        final AtomicLong successCounter = new AtomicLong();
        final AtomicLong failureCounter = new AtomicLong();
        final AtomicLong ignoredCounter = new AtomicLong();

        testSubject = DisruptorCommandBus.builder()
                                         .bufferSize(8)
                                         .messageMonitor(msg -> new MessageMonitor.MonitorCallback() {
                                             @Override
                                             public void reportSuccess() {
                                                 successCounter.incrementAndGet();
                                             }

                                             @Override
                                             public void reportFailure(Throwable cause) {
                                                 failureCounter.incrementAndGet();
                                             }

                                             @Override
                                             public void reportIgnored() {
                                                 ignoredCounter.incrementAndGet();
                                             }
                                         })
                                         .build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(ErrorCommand.class.getName(), stubHandler);
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );

        String aggregateIdentifier2 = UUID.randomUUID().toString();
        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new ErrorCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier)));

        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier2)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier2)));
        testSubject.dispatch(asCommandMessage(new ErrorCommand(aggregateIdentifier2)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier2)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier2)));

        //noinspection unchecked
        CommandCallback<Object, Object> callback = mock(CommandCallback.class);
        testSubject.dispatch(asCommandMessage(new UnknownCommand(aggregateIdentifier2)), callback);

        testSubject.stop();

        assertEquals(8, successCounter.get());
        assertEquals(3, failureCounter.get());
        assertEquals(0, ignoredCounter.get());
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void testCommandRejectedAfterShutdown() {
        testSubject = DisruptorCommandBus.builder().build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );

        testSubject.stop();
        assertThrows(IllegalStateException.class, () -> testSubject.dispatch(asCommandMessage(new Object())));
    }

    @Test
    @Timeout(value = 10)
    void testCommandProcessedAndEventsStored() throws InterruptedException {
        testSubject = DisruptorCommandBus.builder().build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );

        for (int i = 0; i < COMMAND_COUNT; i++) {
            CommandMessage<StubCommand> command = asCommandMessage(new StubCommand(aggregateIdentifier));
            testSubject.dispatch(command);
        }

        //noinspection ResultOfMethodCallIgnored
        eventStore.countDownLatch.await(5, TimeUnit.SECONDS);

        assertEquals(0, eventStore.countDownLatch.getCount(), "Seems that some events are not stored");
    }

    @Test
    void testCanResolveReturnsTrueForMatchingAggregateDescriptor() {
        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertTrue(testRepository.canResolve(new AggregateScopeDescriptor(
                StubAggregate.class.getSimpleName(), aggregateIdentifier)
        ));
    }

    @Test
    void testCanResolveReturnsFalseNonAggregateScopeDescriptorImplementation() {
        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertFalse(testRepository.canResolve(new SagaScopeDescriptor("some-saga-type", aggregateIdentifier)));
    }

    @Test
    void testCanResolveReturnsFalseForNonMatchingAggregateType() {
        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertFalse(testRepository.canResolve(new AggregateScopeDescriptor(
                "other-non-matching-type", aggregateIdentifier
        )));
    }

    @Test
    void testSendDeliversMessageAtDescribedAggregateInstance() throws Exception {
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), aggregateIdentifier);

        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        testRepository.send(testMsg, testDescriptor);

        assertEquals(1, messageHandlingCounter.get());
    }

    @Test
    void testSendThrowsMessageHandlerInvocationExceptionIfHandleFails() {
        DeadlineMessage<FailingEvent> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new FailingEvent(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), aggregateIdentifier);

        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertThrows(MessageHandlerInvocationException.class, () -> testRepository.send(testMsg, testDescriptor));
    }

    @Test
    void testSendFailsSilentlyOnAggregateNotFoundException() throws Exception {
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), "some-other-aggregate-id");

        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        testRepository.send(testMsg, testDescriptor);

        assertEquals(0, messageHandlingCounter.get());
    }

    @Test
    void testDuplicateCommandHandlerResolverSetsTheExpectedHandler() throws Exception {
        DuplicateCommandHandlerResolver testDuplicateCommandHandlerResolver =
                DuplicateCommandHandlerResolution.silentOverride();

        testSubject = DisruptorCommandBus.builder()
                                         .duplicateCommandHandlerResolver(testDuplicateCommandHandlerResolver)
                                         .build();

        StubHandler initialHandler = spy(new StubHandler());
        StubHandler duplicateHandler = spy(new StubHandler());
        CommandMessage<Object> testMessage = asCommandMessage("Say hi!");

        // Subscribe the initial handler
        testSubject.subscribe(String.class.getName(), initialHandler);
        // Then, subscribe a duplicate
        testSubject.subscribe(String.class.getName(), duplicateHandler);

        // And after dispatching a test command, it should be handled by the initial handler
        FutureCallback<Object, Object> callback = new FutureCallback<>();
        testSubject.dispatch(testMessage, callback);

        assertTrue(callback.awaitCompletion(2, TimeUnit.SECONDS), "Expected command to complete");
        verify(initialHandler, never()).handle(testMessage);
        verify(duplicateHandler).handle(testMessage);
    }

    @Test
    void testCreateRepository() {
        assertDoesNotThrow(() -> testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), (RepositoryProvider) null
        ));

        assertDoesNotThrow(() -> testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class),
                ClasspathParameterResolverFactory.forClass(StubAggregate.class),
                ClasspathHandlerDefinition.forClass(StubAggregate.class), null
        ));
    }

    @Test
    void testCommandIsRescheduledForCorruptAggregateState() throws Exception {
        int expectedNumberOfInvocations = 1;
        AtomicInteger invocationCounter = new AtomicInteger(0);

        CommandMessage<String> testCommand = asCommandMessage("some-command");
        //noinspection unchecked
        MessageHandler<CommandMessage<?>> testHandler = mock(MessageHandler.class);
        when(testHandler.canHandle(any())).thenReturn(true);
        when(testHandler.handle(testCommand)).thenThrow(AggregateStateCorruptedException.class)
                                             .thenReturn("happy-now");

        testSubject = DisruptorCommandBus.builder()
                                         .rescheduleCommandsOnCorruptState(true)
                                         .build();
        testSubject.subscribe(String.class.getName(), testHandler);

        testSubject.dispatch(testCommand, (command, result) -> {
            invocationCounter.incrementAndGet();
            assertFalse(result.isExceptional());
            assertEquals("happy-now", result.getPayload());
        });
        assertWithin(500, TimeUnit.MILLISECONDS,
                     () -> assertEquals(expectedNumberOfInvocations, invocationCounter.get()));
    }

    @Test
    void testCommandIsNotRescheduledForCorruptAggregateState() throws Exception {
        int expectedNumberOfInvocations = 1;
        AtomicInteger invocationCounter = new AtomicInteger(0);

        CommandMessage<String> testCommand = asCommandMessage("some-command");
        //noinspection unchecked
        MessageHandler<CommandMessage<?>> testHandler = mock(MessageHandler.class);
        when(testHandler.canHandle(any())).thenReturn(true);
        when(testHandler.handle(testCommand)).thenThrow(AggregateStateCorruptedException.class)
                                             .thenReturn("happy-now");

        testSubject = DisruptorCommandBus.builder()
                                         .rescheduleCommandsOnCorruptState(false)
                                         .build();
        testSubject.subscribe(String.class.getName(), testHandler);

        testSubject.dispatch(testCommand, (command, result) -> {
            invocationCounter.incrementAndGet();
            assertTrue(result.isExceptional());
            assertEquals(AggregateStateCorruptedException.class, result.exceptionResult().getClass());
        });
        assertWithin(50, TimeUnit.MILLISECONDS,
                     () -> assertEquals(expectedNumberOfInvocations, invocationCounter.get()));
    }

    @Test
    void testPublisherInterceptors() throws Exception {
        int expectedNumberOfInvocations = 1;
        AtomicInteger invocationCounter = new AtomicInteger(0);

        CommandMessage<ExceptionCommand> testCommand = asCommandMessage("some-command");
        //noinspection unchecked
        MessageHandler<CommandMessage<?>> testHandler = mock(MessageHandler.class);
        when(testHandler.canHandle(any())).thenReturn(true);
        when(testHandler.handle(testCommand)).thenReturn("handled");

        testSubject = DisruptorCommandBus.builder()
                                         .publisherInterceptors(Collections.singletonList(
                                                 (unitOfWork, interceptorChain) -> {
                                                     invocationCounter.incrementAndGet();
                                                     return interceptorChain.proceed();
                                                 }
                                         ))
                                         .build();
        testSubject.subscribe(String.class.getName(), testHandler);

        testSubject.dispatch(testCommand);
        assertWithin(50, TimeUnit.MILLISECONDS,
                     () -> assertEquals(expectedNumberOfInvocations, invocationCounter.get()));
    }

    @Test
    void testBuildWithZeroOrNegativeCoolingDownPeriodThrowsAxonConfigurationException() {
        DisruptorCommandBus.Builder builderTestSubject = DisruptorCommandBus.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.coolingDownPeriod(0));

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.coolingDownPeriod(-1));
    }

    @Test
    void testBuildWithNullCommandTargetResolverThrowsAxonConfigurationException() {
        DisruptorCommandBus.Builder builderTestSubject = DisruptorCommandBus.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.commandTargetResolver(null));
    }

    private static class DeadlinePayload {

    }

    private static class StubAggregate {

        @AggregateIdentifier
        private String identifier;

        private StubAggregate(String identifier) {
            this.identifier = identifier;
            apply(new SomethingDoneEvent());
        }

        @SuppressWarnings("unused")
        public StubAggregate() {
        }

        public String getIdentifier() {
            return identifier;
        }

        public void doSomething() {
            apply(new SomethingDoneEvent());
        }

        public void createFailingEvent() {
            apply(new FailingEvent());
        }

        @DeadlineHandler
        public void handle(@SuppressWarnings("unused") FailingEvent deadline) {
            throw new IllegalArgumentException();
        }

        @DeadlineHandler
        public void handle(@SuppressWarnings("unused") DeadlinePayload deadline) {
            messageHandlingCounter.getAndIncrement();
        }

        @EventSourcingHandler
        protected void handle(EventMessage<?> event) {
            identifier = ((DomainEventMessage<?>) event).getAggregateIdentifier();
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private final Map<String, DomainEventMessage<?>> storedEvents = new ConcurrentHashMap<>();
        private final CountDownLatch countDownLatch = new CountDownLatch((int) (COMMAND_COUNT + 1L));

        @Override
        public DomainEventStream readEvents(@Nonnull String aggregateIdentifier) {
            DomainEventMessage<?> message = storedEvents.get(aggregateIdentifier);
            return message == null ? DomainEventStream.empty() : DomainEventStream.of(message);
        }

        @Override
        public void publish(@Nonnull List<? extends EventMessage<?>> events) {
            if (events == null || events.isEmpty()) {
                return;
            }
            String key = ((DomainEventMessage<?>) events.get(0)).getAggregateIdentifier();
            DomainEventMessage<?> lastEvent = null;
            for (EventMessage<?> event : events) {
                countDownLatch.countDown();
                lastEvent = (DomainEventMessage<?>) event;
                if (FailingEvent.class.isAssignableFrom(lastEvent.getPayloadType())) {
                    throw new MockException("This is a failing event. EventStore refuses to store that");
                }
            }
            storedEvents.put(key, lastEvent);
        }

        @Override
        public TrackingEventStream openStream(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration registerDispatchInterceptor(
                @Nonnull MessageDispatchInterceptor<? super EventMessage<?>> dispatchInterceptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot) {
        }
    }

    private static class StubCommand {

        @TargetAggregateIdentifier
        private final Object aggregateIdentifier;

        public StubCommand(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier.toString();
        }
    }

    private static class ErrorCommand extends StubCommand {

        public ErrorCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class ExceptionCommand extends StubCommand {

        private final Exception exception;

        public ExceptionCommand(Object aggregateIdentifier, Exception exception) {
            super(aggregateIdentifier);
            this.exception = exception;
        }

        public Exception getException() {
            return exception;
        }
    }

    private static class CreateCommand extends StubCommand {

        public CreateCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class CreateOrUpdateCommand extends StubCommand {

        public CreateOrUpdateCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class UnknownCommand extends StubCommand {

        public UnknownCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class StubHandler implements MessageHandler<CommandMessage<?>> {

        private Repository<StubAggregate> repository;

        private StubHandler() {
        }

        @Override
        public Object handle(CommandMessage<?> command) throws Exception {
            StubCommand payload = (StubCommand) command.getPayload();
            if (ExceptionCommand.class.isAssignableFrom(command.getPayloadType())) {
                throw ((ExceptionCommand) command.getPayload()).getException();
            } else if (CreateCommand.class.isAssignableFrom(command.getPayloadType())) {
                repository.newInstance(() -> new StubAggregate(payload.getAggregateIdentifier()))
                          .execute(StubAggregate::doSomething);
            } else if ((CreateOrUpdateCommand.class.isAssignableFrom(command.getPayloadType()))) {
                repository.loadOrCreate(payload.getAggregateIdentifier(),
                                        () -> new StubAggregate(payload.getAggregateIdentifier()))
                          .execute(StubAggregate::doSomething);
            } else {
                Aggregate<StubAggregate> aggregate = repository.load(payload.getAggregateIdentifier());
                if (ErrorCommand.class.isAssignableFrom(command.getPayloadType())) {
                    aggregate.execute(StubAggregate::createFailingEvent);
                } else {
                    aggregate.execute(StubAggregate::doSomething);
                }
            }

            return COMMAND_RETURN_VALUE;
        }

        public void setRepository(Repository<StubAggregate> repository) {
            this.repository = repository;
        }
    }

    private static class StubDomainEvent {

    }

    private static class FailingEvent {

    }

    private static class Parameter implements Answer<Object> {

        private final int index;

        private Parameter(int index) {
            this.index = index;
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            return invocation.getArguments()[index];
        }
    }
}
