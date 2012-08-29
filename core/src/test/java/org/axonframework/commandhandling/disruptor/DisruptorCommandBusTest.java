/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.MultiThreadedClaimStrategy;
import com.lmax.disruptor.SingleThreadedClaimStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.commandhandling.RollbackOnAllExceptionsConfiguration;
import org.axonframework.commandhandling.annotation.TargetAggregateIdentifier;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.AbstractEventSourcedEntity;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DisruptorCommandBusTest {

    private static final int COMMAND_COUNT = 100 * 1000;
    private CountingEventBus eventBus;
    private StubHandler stubHandler;
    private InMemoryEventStore inMemoryEventStore;
    private DisruptorCommandBus testSubject;
    private String aggregateIdentifier;

    @Before
    public void setUp() throws Exception {
        aggregateIdentifier = UUID.randomUUID().toString();
        eventBus = new CountingEventBus();
        stubHandler = new StubHandler();
        inMemoryEventStore = new InMemoryEventStore();

        inMemoryEventStore.appendEvents(StubAggregate.class.getSimpleName(), new SimpleDomainEventStream(
                new GenericDomainEventMessage<StubDomainEvent>(aggregateIdentifier, 0, new StubDomainEvent())));
    }

    @After
    public void tearDown() {
        testSubject.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCallbackInvokedBeforeUnitOfWorkCleanup() throws Throwable {
        CommandHandlerInterceptor mockHandlerInterceptor = mock(CommandHandlerInterceptor.class);
        CommandDispatchInterceptor mockDispatchInterceptor = mock(CommandDispatchInterceptor.class);
        when(mockDispatchInterceptor.handle(isA(CommandMessage.class))).thenAnswer(new Parameter(0));
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        testSubject = new DisruptorCommandBus(
                inMemoryEventStore, eventBus,
                new DisruptorConfiguration().setInvokerInterceptors(Arrays.asList(mockHandlerInterceptor))
                                            .setDispatchInterceptors(Arrays.asList(mockDispatchInterceptor))
                                            .setClaimStrategy(new SingleThreadedClaimStrategy(8))
                                            .setWaitStrategy(new SleepingWaitStrategy())
                                            .setExecutor(customExecutor)
                                            .setInvokerThreadCount(2)
                                            .setPublisherThreadCount(3));
        testSubject.subscribe(StubCommand.class, stubHandler);
        stubHandler.setRepository(testSubject
                                          .createRepository(new GenericAggregateFactory<StubAggregate>(StubAggregate.class)));
        final UnitOfWorkListener mockUnitOfWorkListener = mock(UnitOfWorkListener.class);
        when(mockUnitOfWorkListener.onEventRegistered(isA(UnitOfWork.class), any(EventMessage.class)))
                .thenAnswer(new Parameter(1));
        when(mockHandlerInterceptor.handle(any(CommandMessage.class),
                                           any(UnitOfWork.class),
                                           any(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        ((UnitOfWork) invocation.getArguments()[1]).registerListener(mockUnitOfWorkListener);
                        return ((InterceptorChain) invocation.getArguments()[2]).proceed();
                    }
                });
        CommandMessage<StubCommand> command = new GenericCommandMessage<StubCommand>(
                new StubCommand(aggregateIdentifier));
        CommandCallback mockCallback = mock(CommandCallback.class);
        testSubject.dispatch(command, mockCallback);

        testSubject.stop();
        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        InOrder inOrder = inOrder(mockDispatchInterceptor,
                                  mockHandlerInterceptor,
                                  mockUnitOfWorkListener,
                                  mockCallback);
        inOrder.verify(mockDispatchInterceptor).handle(isA(CommandMessage.class));
        inOrder.verify(mockHandlerInterceptor).handle(any(CommandMessage.class),
                                                      any(UnitOfWork.class),
                                                      any(InterceptorChain.class));
        inOrder.verify(mockUnitOfWorkListener).onPrepareCommit(any(UnitOfWork.class), any(Set.class), any(List.class));
        inOrder.verify(mockUnitOfWorkListener).afterCommit(isA(UnitOfWork.class));
        inOrder.verify(mockUnitOfWorkListener).onCleanup(isA(UnitOfWork.class));

        verify(mockCallback).onSuccess(any());
    }

    @SuppressWarnings("unchecked")
    @Test(timeout = 10000)
    public void testAggregatesBlacklistedAndRecoveredOnError_WithAutoReschedule() throws Throwable {
        CommandHandlerInterceptor mockInterceptor = mock(CommandHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        CommandCallback mockCallback = dispatchCommands(mockInterceptor,
                                                        customExecutor,
                                                        new GenericCommandMessage<ErrorCommand>(
                                                                new ErrorCommand(aggregateIdentifier)));
        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        verify(mockCallback, times(990)).onSuccess(any());
        verify(mockCallback, times(10)).onFailure(isA(RuntimeException.class));
    }

    @SuppressWarnings("unchecked")
    @Test(timeout = 10000)
    public void testAggregatesBlacklistedAndRecoveredOnError_WithoutReschedule() throws Throwable {
        CommandHandlerInterceptor mockInterceptor = mock(CommandHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();

        CommandCallback mockCallback = dispatchCommands(mockInterceptor,
                                                        customExecutor,
                                                        new GenericCommandMessage<ErrorCommand>(
                                                                new ErrorCommand(aggregateIdentifier)));

        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        verify(mockCallback, times(990)).onSuccess(any());
        verify(mockCallback, times(10)).onFailure(isA(RuntimeException.class));
    }

    private CommandCallback dispatchCommands(CommandHandlerInterceptor mockInterceptor, ExecutorService customExecutor,
                                             GenericCommandMessage<ErrorCommand> errorCommand)
            throws Throwable {
        inMemoryEventStore.storedEvents.clear();
        testSubject = new DisruptorCommandBus(
                inMemoryEventStore, eventBus,
                new DisruptorConfiguration().setInvokerInterceptors(Arrays.asList(mockInterceptor))
                                            .setClaimStrategy(new MultiThreadedClaimStrategy(8))
                                            .setWaitStrategy(new SleepingWaitStrategy())
                                            .setExecutor(customExecutor)
                                            .setRollbackConfiguration(new RollbackOnAllExceptionsConfiguration())
                                            .setInvokerThreadCount(2)
                                            .setPublisherThreadCount(3));
        testSubject.subscribe(StubCommand.class, stubHandler);
        testSubject.subscribe(CreateCommand.class, stubHandler);
        testSubject.subscribe(ErrorCommand.class, stubHandler);
        stubHandler.setRepository(testSubject
                                          .createRepository(new GenericAggregateFactory<StubAggregate>(StubAggregate.class)));
        final UnitOfWorkListener mockUnitOfWorkListener = mock(UnitOfWorkListener.class);
        when(mockUnitOfWorkListener.onEventRegistered(isA(UnitOfWork.class), any(EventMessage.class)))
                .thenAnswer(new Parameter(1));
        when(mockInterceptor.handle(any(CommandMessage.class), any(UnitOfWork.class), any(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        ((UnitOfWork) invocation.getArguments()[1]).registerListener(mockUnitOfWorkListener);
                        return ((InterceptorChain) invocation.getArguments()[2]).proceed();
                    }
                });
        testSubject.dispatch(new GenericCommandMessage<CreateCommand>(new CreateCommand(aggregateIdentifier)));
        CommandCallback mockCallback = mock(CommandCallback.class);
        for (int t = 0; t < 1000; t++) {
            CommandMessage command;
            if (t % 100 == 10) {
                command = errorCommand;
            } else {
                command = new GenericCommandMessage<StubCommand>(new StubCommand(aggregateIdentifier));
            }
            testSubject.dispatch(command, mockCallback);
        }

        testSubject.stop();
        return mockCallback;
    }

    @Test
    public void testCreateAggregate() {
        inMemoryEventStore.storedEvents.clear();
        eventBus = mock(CountingEventBus.class);

        testSubject = new DisruptorCommandBus(inMemoryEventStore, eventBus,
                                              new DisruptorConfiguration()
                                                      .setClaimStrategy(new SingleThreadedClaimStrategy(8))
                                                      .setWaitStrategy(new SleepingWaitStrategy())
                                                      .setInvokerThreadCount(2)
                                                      .setPublisherThreadCount(3));
        testSubject.subscribe(StubCommand.class, stubHandler);
        testSubject.subscribe(CreateCommand.class, stubHandler);
        testSubject.subscribe(ErrorCommand.class, stubHandler);
        stubHandler.setRepository(
                testSubject.createRepository(new GenericAggregateFactory<StubAggregate>(StubAggregate.class)));

        testSubject.dispatch(new GenericCommandMessage<Object>(new CreateCommand(aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage lastEvent = inMemoryEventStore.storedEvents.get(aggregateIdentifier);

        // we expect 2 events, 1 from aggregate constructor, one from doSomething method invocation
        assertEquals(1, lastEvent.getSequenceNumber());
        // check that both events are published in a single call
        verify(eventBus).publish(isA(EventMessage.class), isA(EventMessage.class));
        assertEquals(aggregateIdentifier, lastEvent.getAggregateIdentifier());
    }

    @Test(expected = IllegalStateException.class)
    public void testCommandRejectedAfterShutdown() throws InterruptedException {
        testSubject = new DisruptorCommandBus(inMemoryEventStore, eventBus);
        testSubject.subscribe(StubCommand.class, stubHandler);
        stubHandler.setRepository(testSubject
                                          .createRepository(new GenericAggregateFactory<StubAggregate>(StubAggregate.class)));

        testSubject.stop();
        testSubject.dispatch(new GenericCommandMessage<Object>(new Object()));
    }

    @Test
    public void testCommandProcessedAndEventsStored() throws InterruptedException {
        testSubject = new DisruptorCommandBus(inMemoryEventStore, eventBus);
        testSubject.subscribe(StubCommand.class, stubHandler);
        stubHandler.setRepository(testSubject
                                          .createRepository(new GenericAggregateFactory<StubAggregate>(StubAggregate.class)));

        for (int i = 0; i < COMMAND_COUNT; i++) {
            CommandMessage<StubCommand> command = new GenericCommandMessage<StubCommand>(
                    new StubCommand(aggregateIdentifier));
            testSubject.dispatch(command);
        }

        inMemoryEventStore.countDownLatch.await(5, TimeUnit.SECONDS);
        eventBus.publisherCountDown.await(1, TimeUnit.SECONDS);
        assertEquals("Seems that some events are not published", 0, eventBus.publisherCountDown.getCount());
        assertEquals("Seems that some events are not stored", 0, inMemoryEventStore.countDownLatch.getCount());
    }

    @Test(timeout = 10000)
    public void testCommandsAgainstInexistentAggregatesOnlyRetriedOnce() throws Throwable {
        inMemoryEventStore.storedEvents.clear();
        testSubject = new DisruptorCommandBus(inMemoryEventStore, eventBus);
        stubHandler.setRepository(testSubject
                                          .createRepository(new GenericAggregateFactory<StubAggregate>(StubAggregate.class)));
        StubHandler spy = spy(stubHandler);
        testSubject.subscribe(StubCommand.class, spy);
        List<CommandMessage<StubCommand>> dispatchedCommands = new ArrayList<CommandMessage<StubCommand>>();
        for (int i = 0; i < 10; i++) {
            CommandMessage<StubCommand> subsequentCommand = new GenericCommandMessage<StubCommand>(
                    new StubCommand(aggregateIdentifier));
            testSubject.dispatch(subsequentCommand, NoOpCallback.INSTANCE);
            dispatchedCommands.add(subsequentCommand);
        }
        testSubject.stop();
        assertTrue(inMemoryEventStore.storedEvents.isEmpty());
        for (CommandMessage<StubCommand> command : dispatchedCommands) {
            // subsequent commands could be retried more that once, as the aggregate they work in is blacklisted
            verify(spy, times(2)).handle(same(command), isA(UnitOfWork.class));
        }
    }

    private static class StubAggregate extends AbstractEventSourcedAggregateRoot {

        private static final long serialVersionUID = 8192033940704210095L;

        private String identifier;

        private StubAggregate(String identifier) {
            this.identifier = identifier;
            apply(new SomethingDoneEvent());
        }

        @SuppressWarnings("UnusedDeclaration")
        public StubAggregate() {
        }

        @Override
        public Object getIdentifier() {
            return identifier;
        }

        public void doSomething() {
            apply(new SomethingDoneEvent());
        }

        public void createFailingEvent() {
            apply(new FailingEvent());
        }

        @Override
        protected void handle(DomainEventMessage event) {
            identifier = (String) event.getAggregateIdentifier();
        }

        @Override
        protected Collection<AbstractEventSourcedEntity> getChildEntities() {
            return Collections.emptyList();
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private final Map<String, DomainEventMessage> storedEvents = new ConcurrentHashMap<String, DomainEventMessage>();
        private final CountDownLatch countDownLatch = new CountDownLatch((int) (COMMAND_COUNT + 1L));

        @Override
        public void appendEvents(String type, DomainEventStream events) {
            if (!events.hasNext()) {
                return;
            }
            String key = events.peek().getAggregateIdentifier().toString();
            DomainEventMessage<?> lastEvent = null;
            while (events.hasNext()) {
                countDownLatch.countDown();
                lastEvent = events.next();
                if (FailingEvent.class.isAssignableFrom(lastEvent.getPayloadType())) {
                    throw new RuntimeException("This is a failing event. EventStore refuses to store that");
                }
            }
            storedEvents.put(key, lastEvent);
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier) {
            DomainEventMessage message = storedEvents.get(identifier.toString());
            if (message == null) {
                throw new EventStreamNotFoundException(type, identifier);
            }
            return new SimpleDomainEventStream(Collections.singletonList(message));
        }
    }

    private static class StubCommand {

        @TargetAggregateIdentifier
        private Object agregateIdentifier;

        public StubCommand(Object agregateIdentifier) {
            this.agregateIdentifier = agregateIdentifier;
        }

        public Object getAggregateIdentifier() {
            return agregateIdentifier;
        }
    }

    private static class ErrorCommand extends StubCommand {

        public ErrorCommand(Object agregateIdentifier) {
            super(agregateIdentifier);
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

        public CreateCommand(Object agregateIdentifier) {
            super(agregateIdentifier);
        }
    }

    private static class StubHandler implements CommandHandler<StubCommand> {

        private Repository<StubAggregate> repository;

        private StubHandler() {
        }

        @Override
        public Object handle(CommandMessage<StubCommand> command, UnitOfWork unitOfWork) throws Throwable {
            if (ExceptionCommand.class.isAssignableFrom(command.getPayloadType())) {
                throw ((ExceptionCommand) command.getPayload()).getException();
            } else if (CreateCommand.class.isAssignableFrom(command.getPayloadType())) {
                StubAggregate aggregate = new StubAggregate(command.getPayload().getAggregateIdentifier().toString());
                repository.add(aggregate);
                aggregate.doSomething();
            } else {
                StubAggregate aggregate = repository.load(command.getPayload().getAggregateIdentifier());
                if (ErrorCommand.class.isAssignableFrom(command.getPayloadType())) {
                    aggregate.createFailingEvent();
                } else {
                    aggregate.doSomething();
                }
            }

            return Void.TYPE;
        }

        public void setRepository(Repository<StubAggregate> repository) {
            this.repository = repository;
        }
    }

    private static class StubDomainEvent {

    }

    private static class CountingEventBus implements EventBus {

        private final CountDownLatch publisherCountDown = new CountDownLatch(COMMAND_COUNT);

        @Override
        public void publish(EventMessage... events) {
            publisherCountDown.countDown();
        }

        @Override
        public void subscribe(EventListener eventListener) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public void unsubscribe(EventListener eventListener) {
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }

    /**
     * @author Allard Buijze
     */
    static class FailingEvent {

    }

    private static class Parameter implements Answer<Object> {

        private final int index;

        private Parameter(int index) {
            this.index = index;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            return invocation.getArguments()[index];
        }
    }
}
