/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.disruptor.commandhandling.utils.MockException;
import org.axonframework.disruptor.commandhandling.utils.SomethingDoneEvent;
import org.axonframework.eventhandling.*;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.SnapshotTrigger;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.*;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlerInvocationException;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.*;
import org.axonframework.modelling.saga.SagaScopeDescriptor;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Executable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.util.Collections.singletonList;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class DisruptorCommandBusTest {

    private static final int COMMAND_COUNT = 100 * 1000;
    private static final String COMMAND_RETURN_VALUE = "dummyVal";
    private static AtomicInteger messageHandlingCounter;
    private StubHandler stubHandler;
    private InMemoryEventStore eventStore;
    private DisruptorCommandBus testSubject;
    private String aggregateIdentifier;
    private TransactionManager mockTransactionManager;
    private ParameterResolverFactory parameterResolverFactory;

    @BeforeEach
    void setUp() {
        aggregateIdentifier = UUID.randomUUID().toString();
        stubHandler = new StubHandler();
        eventStore = new InMemoryEventStore();
        eventStore.publish(singletonList(
                new GenericDomainEventMessage<>("StubAggregate", aggregateIdentifier, 0, new StubDomainEvent())));
        parameterResolverFactory = spy(ClasspathParameterResolverFactory.forClass(DisruptorCommandBusTest.class));
        messageHandlingCounter = new AtomicInteger(0);
    }

    @AfterEach
    void tearDown() {
        testSubject.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCallbackInvokedBeforeUnitOfWorkCleanup() throws Exception {
        MessageHandlerInterceptor mockHandlerInterceptor = mock(MessageHandlerInterceptor.class);
        MessageDispatchInterceptor mockDispatchInterceptor = mock(MessageDispatchInterceptor.class);
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
        CommandCallback mockCallback = mock(CommandCallback.class);
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

    @SuppressWarnings("unchecked")
    @Test
    void testPublishUnsupportedCommand() {
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        CommandCallback callback = mock(CommandCallback.class);
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
        CommandCallback mockCallback = mock(CommandCallback.class);
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
        testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                     parameterResolverFactory);

        verify(parameterResolverFactory, atLeastOnce()).createInstance(isA(Executable.class),
                                                                       isA(java.lang.reflect.Parameter[].class),
                                                                       anyInt());
        verifyNoMoreInteractions(parameterResolverFactory);
    }

    @Test
    void testEventPublicationExecutedWithinTransaction() throws Exception {
        MessageHandlerInterceptor mockInterceptor = mock(MessageHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        Transaction mockTransaction = mock(Transaction.class);
        mockTransactionManager = mock(TransactionManager.class);
        when(mockTransactionManager.startTransaction()).thenReturn(mockTransaction);

        dispatchCommands(mockInterceptor, customExecutor,
                         asCommandMessage(new ErrorCommand(aggregateIdentifier)));

        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));

        verify(mockTransactionManager, times(991)).startTransaction();
        verify(mockTransaction, times(991)).commit();
        verifyNoMoreInteractions(mockTransaction, mockTransactionManager);
    }

    @SuppressWarnings({"unchecked", "Duplicates"})
    @Test
    @Timeout(value = 10)
    void testAggregatesBlacklistedAndRecoveredOnError_WithAutoReschedule() throws Exception {
        MessageHandlerInterceptor mockInterceptor = mock(MessageHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        CommandCallback mockCallback = dispatchCommands(mockInterceptor, customExecutor, asCommandMessage(
                new ErrorCommand(aggregateIdentifier)));
        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback, times(1000)).onResult(any(), commandResultMessageCaptor.capture());
        assertEquals(10, commandResultMessageCaptor.getAllValues()
                                                   .stream()
                                                   .filter(ResultMessage::isExceptional)
                                                   .count());
    }

    @SuppressWarnings({"unchecked", "Duplicates"})
    @Test
    @Timeout(value = 10)
    void testAggregatesBlacklistedAndRecoveredOnError_WithoutReschedule() throws Exception {
        MessageHandlerInterceptor mockInterceptor = mock(MessageHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();

        CommandCallback mockCallback = dispatchCommands(mockInterceptor, customExecutor, asCommandMessage(
                new ErrorCommand(aggregateIdentifier)));

        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback, times(1000)).onResult(any(), commandResultMessageCaptor.capture());
        assertEquals(10, commandResultMessageCaptor.getAllValues()
                                                   .stream()
                                                   .filter(ResultMessage::isExceptional)
                                                   .count());
    }

    private CommandCallback dispatchCommands(MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor,
                                             ExecutorService customExecutor,
                                             CommandMessage<ErrorCommand> errorCommand) throws Exception {
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
        stubHandler.setRepository(testSubject.createRepository(eventStore,
                                                               new GenericAggregateFactory<>(StubAggregate.class)));
        when(mockInterceptor.handle(any(UnitOfWork.class), any(InterceptorChain.class)))
                .thenAnswer(invocation -> ((InterceptorChain) invocation.getArguments()[1]).proceed());
        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));
        CommandCallback mockCallback = mock(CommandCallback.class);
        for (int t = 0; t < 1000; t++) {
            CommandMessage command;
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
        stubHandler.setRepository(testSubject.createRepository(eventStore,
                                                               new GenericAggregateFactory<>(StubAggregate.class)));


        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage lastEvent = eventStore.storedEvents.get(aggregateIdentifier);

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
        stubHandler.setRepository(testSubject.createRepository(eventStore,
                                                               new GenericAggregateFactory<>(StubAggregate.class)));

        FutureCallback futureCallback = new FutureCallback();

        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)), futureCallback);
        testSubject.stop();

        DomainEventMessage lastEvent = eventStore.storedEvents.get(aggregateIdentifier);

        assertEquals(COMMAND_RETURN_VALUE, futureCallback.getResult().getPayload());
    }

    @SuppressWarnings("unchecked")
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
        stubHandler.setRepository(testSubject.createRepository(eventStore,
                                                               new GenericAggregateFactory<>(StubAggregate.class)));

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

        CommandCallback callback = mock(CommandCallback.class);
        testSubject.dispatch(asCommandMessage(new UnknownCommand(aggregateIdentifier2)), callback);

        testSubject.stop();

        assertEquals(8, successCounter.get());
        assertEquals(3, failureCounter.get());
        assertEquals(0, ignoredCounter.get());
        ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor =
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
        stubHandler.setRepository(testSubject.createRepository(eventStore,
                                                               new GenericAggregateFactory<>(StubAggregate.class)));

        testSubject.stop();
        assertThrows(IllegalStateException.class, () -> testSubject.dispatch(asCommandMessage(new Object())));
    }

    @Test
    @Timeout(value = 10)
    void testCommandProcessedAndEventsStored() throws InterruptedException {
        testSubject = DisruptorCommandBus.builder().build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        stubHandler.setRepository(testSubject.createRepository(eventStore,
                                                               new GenericAggregateFactory<>(StubAggregate.class)));

        for (int i = 0; i < COMMAND_COUNT; i++) {
            CommandMessage<StubCommand> command = asCommandMessage(new StubCommand(aggregateIdentifier));
            testSubject.dispatch(command);
        }

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
    void testSendThrowsMessageHandlerInvocationExceptionIfHandleFails() throws Exception {
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
        DuplicateCommandHandlerResolver testDuplicateCommandHandlerResolver = DuplicateCommandHandlerResolution.silentOverride();

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

    private static class DeadlinePayload {

    }

    @SuppressWarnings("unused")
    private static class StubAggregate {

        @AggregateIdentifier
        private String identifier;

        private StubAggregate(String identifier) {
            this.identifier = identifier;
            apply(new SomethingDoneEvent());
        }

        @SuppressWarnings("UnusedDeclaration")
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
        public void handle(FailingEvent deadline) {
            throw new IllegalArgumentException();
        }

        @DeadlineHandler
        public void handle(DeadlinePayload deadline) {
            messageHandlingCounter.getAndIncrement();
        }

        @EventSourcingHandler
        protected void handle(EventMessage event) {
            identifier = ((DomainEventMessage) event).getAggregateIdentifier();
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private final Map<String, DomainEventMessage> storedEvents = new ConcurrentHashMap<>();
        private final CountDownLatch countDownLatch = new CountDownLatch((int) (COMMAND_COUNT + 1L));

        @Override
        public DomainEventStream readEvents(String aggregateIdentifier) {
            DomainEventMessage message = storedEvents.get(aggregateIdentifier);
            return message == null ? DomainEventStream.empty() : DomainEventStream.of(message);
        }

        @Override
        public void publish(List<? extends EventMessage<?>> events) {
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
        public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration registerDispatchInterceptor(
                MessageDispatchInterceptor<? super EventMessage<?>> dispatchInterceptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void storeSnapshot(DomainEventMessage<?> snapshot) {
        }
    }

    private static class StubCommand {

        @TargetAggregateIdentifier
        private Object aggregateIdentifier;

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
