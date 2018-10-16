/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.disruptor.commandhandling.utils.MockException;
import org.axonframework.disruptor.commandhandling.utils.SomethingDoneEvent;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.Repository;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.modelling.saga.SagaScopeDescriptor;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.SnapshotTrigger;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlerInvocationException;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.lang.reflect.Executable;
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

import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DisruptorCommandBusTest {

    private static final int COMMAND_COUNT = 100 * 1000;

    private StubHandler stubHandler;
    private InMemoryEventStore eventStore;
    private DisruptorCommandBus testSubject;
    private String aggregateIdentifier;
    private TransactionManager mockTransactionManager;
    private ParameterResolverFactory parameterResolverFactory;
    private static AtomicInteger messageHandlingCounter;

    @Before
    public void setUp() {
        aggregateIdentifier = UUID.randomUUID().toString();
        stubHandler = new StubHandler();
        eventStore = new InMemoryEventStore();
        eventStore.publish(singletonList(
                new GenericDomainEventMessage<>("type", aggregateIdentifier, 0, new StubDomainEvent())));
        parameterResolverFactory = spy(ClasspathParameterResolverFactory.forClass(DisruptorCommandBusTest.class));
        messageHandlingCounter = new AtomicInteger(0);
    }

    @After
    public void tearDown() {
        testSubject.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCallbackInvokedBeforeUnitOfWorkCleanup() throws Exception {
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
    public void testPublishUnsupportedCommand() {
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        testSubject = DisruptorCommandBus.builder()
                                         .bufferSize(8)
                                         .producerType(ProducerType.SINGLE)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .executor(customExecutor)
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .build();
        CommandCallback callback = mock(CommandCallback.class);
        testSubject.dispatch(asCommandMessage("Test"), callback);
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
    public void testEventStreamsDecoratedOnReadAndWrite() throws InterruptedException {
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
    public void usesProvidedParameterResolverFactoryToResolveParameters() {
        testSubject = DisruptorCommandBus.builder().build();
        testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                     parameterResolverFactory);

        verify(parameterResolverFactory, atLeastOnce()).createInstance(isA(Executable.class),
                                                                       isA(java.lang.reflect.Parameter[].class),
                                                                       anyInt());
        verifyNoMoreInteractions(parameterResolverFactory);
    }

    @Test
    public void testEventPublicationExecutedWithinTransaction() throws Exception {
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
    @Test(timeout = 10000)
    public void testAggregatesBlacklistedAndRecoveredOnError_WithAutoReschedule() throws Exception {
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
    @Test(timeout = 10000)
    public void testAggregatesBlacklistedAndRecoveredOnError_WithoutReschedule() throws Exception {
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
    public void testCreateAggregate() {
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

    @SuppressWarnings("unchecked")
    @Test
    public void testMessageMonitoring() {
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

    @Test(expected = IllegalStateException.class)
    public void testCommandRejectedAfterShutdown() {
        testSubject = DisruptorCommandBus.builder().build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        stubHandler.setRepository(testSubject.createRepository(eventStore,
                                                               new GenericAggregateFactory<>(StubAggregate.class)));

        testSubject.stop();
        testSubject.dispatch(asCommandMessage(new Object()));
    }

    @Test(timeout = 10000)
    public void testCommandProcessedAndEventsStored() throws InterruptedException {
        testSubject = DisruptorCommandBus.builder().build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        stubHandler.setRepository(testSubject.createRepository(eventStore,
                                                               new GenericAggregateFactory<>(StubAggregate.class)));

        for (int i = 0; i < COMMAND_COUNT; i++) {
            CommandMessage<StubCommand> command = asCommandMessage(new StubCommand(aggregateIdentifier));
            testSubject.dispatch(command);
        }

        eventStore.countDownLatch.await(5, TimeUnit.SECONDS);
        assertEquals("Seems that some events are not stored", 0, eventStore.countDownLatch.getCount());
    }

    @Test
    public void testCanResolveReturnsTrueForMatchingAggregateDescriptor() {
        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertTrue(testRepository.canResolve(new AggregateScopeDescriptor(
                StubAggregate.class.getSimpleName(), aggregateIdentifier)
        ));
    }

    @Test
    public void testCanResolveReturnsFalseNonAggregateScopeDescriptorImplementation() {
        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertFalse(testRepository.canResolve(new SagaScopeDescriptor("some-saga-type", aggregateIdentifier)));
    }

    @Test
    public void testCanResolveReturnsFalseForNonMatchingAggregateType() {
        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertFalse(testRepository.canResolve(new AggregateScopeDescriptor(
                "other-non-matching-type", aggregateIdentifier
        )));
    }

    @Test
    public void testSendDeliversMessageAtDescribedAggregateInstance() throws Exception {
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), aggregateIdentifier);

        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        testRepository.send(testMsg, testDescriptor);

        assertEquals(1, messageHandlingCounter.get());
    }

    @Test(expected = MessageHandlerInvocationException.class)
    public void testSendThrowsMessageHandlerInvocationExceptionIfHandleFails() throws Exception {
        DeadlineMessage<FailingEvent> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new FailingEvent());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), aggregateIdentifier);

        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        testRepository.send(testMsg, testDescriptor);
    }

    @Test
    public void testSendFailsSilentlyOnAggregateNotFoundException() throws Exception {
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), "some-other-aggregate-id");

        testSubject = DisruptorCommandBus.builder().build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        testRepository.send(testMsg, testDescriptor);

        assertEquals(0, messageHandlingCounter.get());
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

            return null;
        }

        public void setRepository(Repository<StubAggregate> repository) {
            this.repository = repository;
        }
    }

    private static class StubDomainEvent {

    }

    static class FailingEvent {

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
