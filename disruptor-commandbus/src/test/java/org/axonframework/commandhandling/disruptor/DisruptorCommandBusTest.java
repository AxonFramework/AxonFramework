/*
 * Copyright (c) 2010-2011. Axon Framework
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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.commandhandling.MetaDataCommandTargetResolver;
import org.axonframework.commandhandling.RollbackOnAllExceptionsConfiguration;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.AbstractEventSourcedEntity;
import org.axonframework.eventsourcing.AggregateInitializer;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.AggregateNotFoundException;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private DisruptorCommandBus<StubAggregate> testSubject;
    private final String aggregateIdentifier = "MyID";
    private static final String TARGET_AGGREGATE_PROPERTY = "target-aggregate";

    @Before
    public void setUp() throws Exception {
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
        CommandHandlerInterceptor mockInterceptor = mock(CommandHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        testSubject = new DisruptorCommandBus<StubAggregate>(
                new GenericAggregateFactory<StubAggregate>(StubAggregate.class), inMemoryEventStore, eventBus,
                new MetaDataCommandTargetResolver(TARGET_AGGREGATE_PROPERTY),
                new DisruptorConfiguration().setInvokerInterceptors(Arrays.asList(mockInterceptor))
                                            .setClaimStrategy(new SingleThreadedClaimStrategy(8))
                                            .setWaitStrategy(new SleepingWaitStrategy())
                                            .setExecutor(customExecutor));
        testSubject.subscribe(StubCommand.class, stubHandler);
        stubHandler.setRepository(testSubject);
        final UnitOfWorkListener mockUnitOfWorkListener = mock(UnitOfWorkListener.class);
        when(mockUnitOfWorkListener.onEventRegistered(any(EventMessage.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArguments()[0];
            }
        });
        when(mockInterceptor.handle(any(CommandMessage.class), any(UnitOfWork.class), any(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        ((UnitOfWork) invocation.getArguments()[1]).registerListener(mockUnitOfWorkListener);
                        return ((InterceptorChain) invocation.getArguments()[2]).proceed();
                    }
                });
        CommandMessage<StubCommand> command = new GenericCommandMessage<StubCommand>(
                new StubCommand(aggregateIdentifier),
                Collections.singletonMap(TARGET_AGGREGATE_PROPERTY,
                                         (Object) aggregateIdentifier));
        CommandCallback mockCallback = mock(CommandCallback.class);
        testSubject.dispatch(command, mockCallback);

        testSubject.stop();
        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        InOrder inOrder = inOrder(mockInterceptor, mockUnitOfWorkListener, mockCallback);
        inOrder.verify(mockInterceptor).handle(any(CommandMessage.class),
                                               any(UnitOfWork.class),
                                               any(InterceptorChain.class));
        inOrder.verify(mockUnitOfWorkListener).onPrepareCommit(any(Set.class), any(List.class));
        inOrder.verify(mockUnitOfWorkListener).afterCommit();
        inOrder.verify(mockUnitOfWorkListener).onCleanup();

        verify(mockCallback).onSuccess(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAggregatesBlacklistedAndRecoveredOnError_WithAutoReschedule() throws Throwable {
        CommandHandlerInterceptor mockInterceptor = mock(CommandHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        testSubject = new DisruptorCommandBus<StubAggregate>(
                new GenericAggregateFactory<StubAggregate>(StubAggregate.class), inMemoryEventStore, eventBus,
                new MetaDataCommandTargetResolver(TARGET_AGGREGATE_PROPERTY),
                new DisruptorConfiguration().setInvokerInterceptors(Arrays.asList(mockInterceptor))
                                            .setClaimStrategy(new MultiThreadedClaimStrategy(8))
                                            .setWaitStrategy(new SleepingWaitStrategy())
                                            .setExecutor(customExecutor)
                                            .setRollbackConfiguration(new RollbackOnAllExceptionsConfiguration()));
        testSubject.subscribe(StubCommand.class, stubHandler);
        testSubject.subscribe(CreateCommand.class, stubHandler);
        testSubject.subscribe(ErrorCommand.class, stubHandler);
        stubHandler.setRepository(testSubject);
        final UnitOfWorkListener mockUnitOfWorkListener = mock(UnitOfWorkListener.class);
        when(mockUnitOfWorkListener.onEventRegistered(any(EventMessage.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArguments()[0];
            }
        });
        when(mockInterceptor.handle(any(CommandMessage.class), any(UnitOfWork.class), any(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        ((UnitOfWork) invocation.getArguments()[1]).registerListener(mockUnitOfWorkListener);
                        return ((InterceptorChain) invocation.getArguments()[2]).proceed();
                    }
                });
        testSubject.dispatch(new GenericCommandMessage(new CreateCommand(aggregateIdentifier), Collections.singletonMap(
                TARGET_AGGREGATE_PROPERTY, (Object) aggregateIdentifier)));
        CommandCallback mockCallback = mock(CommandCallback.class);
        for (int t = 0; t < 1000; t++) {
            CommandMessage command;
            if (t % 100 == 10) {
                command = new GenericCommandMessage<ErrorCommand>(
                        new ErrorCommand(aggregateIdentifier),
                        Collections.singletonMap(TARGET_AGGREGATE_PROPERTY,
                                                 (Object) aggregateIdentifier));
            } else {
                command = new GenericCommandMessage<StubCommand>(
                        new StubCommand(aggregateIdentifier),
                        Collections.singletonMap(TARGET_AGGREGATE_PROPERTY,
                                                 (Object) aggregateIdentifier));
            }
            testSubject.dispatch(command, mockCallback);
        }

        testSubject.stop();
        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        verify(mockCallback, times(990)).onSuccess(any());
        verify(mockCallback, times(10)).onFailure(isA(AggregateBlacklistedException.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAggregatesBlacklistedAndRecoveredOnError_WithoutReschedule() throws Throwable {
        CommandHandlerInterceptor mockInterceptor = mock(CommandHandlerInterceptor.class);
        ExecutorService customExecutor = Executors.newCachedThreadPool();
        testSubject = new DisruptorCommandBus<StubAggregate>(
                new GenericAggregateFactory<StubAggregate>(StubAggregate.class), inMemoryEventStore, eventBus,
                new MetaDataCommandTargetResolver(TARGET_AGGREGATE_PROPERTY),
                new DisruptorConfiguration().setInvokerInterceptors(Arrays.asList(mockInterceptor))
                                            .setClaimStrategy(new MultiThreadedClaimStrategy(8))
                                            .setWaitStrategy(new SleepingWaitStrategy())
                                            .setExecutor(customExecutor)
                                            .setRollbackConfiguration(new RollbackOnAllExceptionsConfiguration())
                                            .setRescheduleCommandsOnCorruptState(false));
        testSubject.subscribe(StubCommand.class, stubHandler);
        testSubject.subscribe(CreateCommand.class, stubHandler);
        testSubject.subscribe(ErrorCommand.class, stubHandler);
        stubHandler.setRepository(testSubject);
        final UnitOfWorkListener mockUnitOfWorkListener = mock(UnitOfWorkListener.class);
        when(mockUnitOfWorkListener.onEventRegistered(any(EventMessage.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArguments()[0];
            }
        });
        when(mockInterceptor.handle(any(CommandMessage.class), any(UnitOfWork.class), any(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        ((UnitOfWork) invocation.getArguments()[1]).registerListener(mockUnitOfWorkListener);
                        return ((InterceptorChain) invocation.getArguments()[2]).proceed();
                    }
                });
        testSubject.dispatch(new GenericCommandMessage(new CreateCommand(aggregateIdentifier), Collections.singletonMap(
                TARGET_AGGREGATE_PROPERTY, (Object) aggregateIdentifier)));
        CommandCallback mockCallback = mock(CommandCallback.class);
        for (int t = 0; t < 1000; t++) {
            CommandMessage command;
            if (t % 100 == 10) {
                command = new GenericCommandMessage<ErrorCommand>(
                        new ErrorCommand(aggregateIdentifier),
                        Collections.singletonMap(TARGET_AGGREGATE_PROPERTY,
                                                 (Object) aggregateIdentifier));
            } else {
                command = new GenericCommandMessage<StubCommand>(
                        new StubCommand(aggregateIdentifier),
                        Collections.singletonMap(TARGET_AGGREGATE_PROPERTY,
                                                 (Object) aggregateIdentifier));
            }
            testSubject.dispatch(command, mockCallback);
        }

        testSubject.stop();
        assertFalse(customExecutor.awaitTermination(250, TimeUnit.MILLISECONDS));
        customExecutor.shutdown();
        assertTrue(customExecutor.awaitTermination(5, TimeUnit.SECONDS));
        verify(mockCallback, atLeast(100)).onSuccess(any());
        verify(mockCallback, atMost(990)).onSuccess(any());
        verify(mockCallback, times(10)).onFailure(isA(AggregateBlacklistedException.class));
    }

    @Test
    public void testCreateAggregate() {
        inMemoryEventStore.storedEvents.clear();

        testSubject = new DisruptorCommandBus<StubAggregate>(
                new GenericAggregateFactory<StubAggregate>(StubAggregate.class), inMemoryEventStore, eventBus,
                new MetaDataCommandTargetResolver(TARGET_AGGREGATE_PROPERTY),
                new DisruptorConfiguration().setClaimStrategy(new SingleThreadedClaimStrategy(8))
                                            .setWaitStrategy(new SleepingWaitStrategy()));
        testSubject.subscribe(StubCommand.class, stubHandler);
        testSubject.subscribe(CreateCommand.class, stubHandler);
        testSubject.subscribe(ErrorCommand.class, stubHandler);
        stubHandler.setRepository(testSubject);

        testSubject.dispatch(new GenericCommandMessage<Object>(
                new CreateCommand(aggregateIdentifier),
                Collections.singletonMap(TARGET_AGGREGATE_PROPERTY, (Object) aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage lastEvent = inMemoryEventStore.storedEvents.get(aggregateIdentifier);
        assertEquals(0, lastEvent.getSequenceNumber());
    }

    @Test(expected = IllegalStateException.class)
    public void testCommandRejectedAfterShutdown() throws InterruptedException {
        testSubject = new DisruptorCommandBus<StubAggregate>(new GenericAggregateFactory<StubAggregate>(StubAggregate.class),
                                                             inMemoryEventStore,
                                                             eventBus,
                                                             new MetaDataCommandTargetResolver(TARGET_AGGREGATE_PROPERTY));
        testSubject.subscribe(StubCommand.class, stubHandler);
        stubHandler.setRepository(testSubject);

        testSubject.stop();
        testSubject.dispatch(new GenericCommandMessage<Object>(new Object()));
    }

    @Test
    public void testCommandProcessedAndEventsStored() throws InterruptedException {
        testSubject = new DisruptorCommandBus<StubAggregate>(new GenericAggregateFactory<StubAggregate>(StubAggregate.class),
                                                             inMemoryEventStore,
                                                             eventBus,
                                                             new MetaDataCommandTargetResolver(TARGET_AGGREGATE_PROPERTY));
        testSubject.subscribe(StubCommand.class, stubHandler);
        stubHandler.setRepository(testSubject);

        for (int i = 0; i < COMMAND_COUNT; i++) {
            CommandMessage<StubCommand> command = new GenericCommandMessage<StubCommand>(
                    new StubCommand(aggregateIdentifier),
                    Collections.singletonMap(TARGET_AGGREGATE_PROPERTY,
                                             (Object) aggregateIdentifier));
            testSubject.dispatch(command);
        }

        inMemoryEventStore.countDownLatch.await(5, TimeUnit.SECONDS);
        eventBus.publisherCountDown.await(1, TimeUnit.SECONDS);
        assertEquals("Seems that some events are not published", 0, eventBus.publisherCountDown.getCount());
        assertEquals("Seems that some events are not stored", 0, inMemoryEventStore.countDownLatch.getCount());
    }

    private static class StubAggregate extends AbstractEventSourcedAggregateRoot {

        private static final long serialVersionUID = 8192033940704210095L;

        private int timesDone = 0;
        private final String identifier;

        @AggregateInitializer
        private StubAggregate(String identifier) {
            this.identifier = identifier;
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
            if (StubDomainEvent.class.isAssignableFrom(event.getPayloadType())) {
                timesDone++;
            }
        }

        @Override
        protected Collection<AbstractEventSourcedEntity> getChildEntities() {
            return Collections.emptyList();
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private final Map<String, DomainEventMessage> storedEvents = new HashMap<String, DomainEventMessage>();
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
                throw new AggregateNotFoundException(identifier, "Aggregate not found");
            }
            return new SimpleDomainEventStream(Collections.singletonList(message));
        }
    }

    private static class StubCommand {

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
            if (CreateCommand.class.isAssignableFrom(command.getPayloadType())) {
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
        public void publish(EventMessage event) {
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
}
