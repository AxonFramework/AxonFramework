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
import com.lmax.disruptor.SleepingWaitStrategy;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.RollbackOnAllExceptionsConfiguration;
import org.axonframework.commandhandling.annotation.TargetAggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.IdentifierFactory;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcedEntity;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DisruptorCommandBusTest_MultiThreaded {

    private static final int COMMAND_COUNT = 100;
    private static final int AGGREGATE_COUNT = 10;
    private CountingEventBus eventBus;
    private StubHandler stubHandler;
    private InMemoryEventStore inMemoryEventStore;
    private DisruptorCommandBus testSubject;
    private String[] aggregateIdentifier;

    @Before
    public void setUp() throws Exception {
        aggregateIdentifier = new String[AGGREGATE_COUNT];
        for (int i = 0; i < AGGREGATE_COUNT; i++) {
            aggregateIdentifier[i] = IdentifierFactory.getInstance().generateIdentifier();
        }
        eventBus = new CountingEventBus();
        stubHandler = new StubHandler();
        inMemoryEventStore = new InMemoryEventStore();
    }

    @After
    public void tearDown() {
        testSubject.stop();
    }

    @SuppressWarnings("unchecked")
    @Test//(timeout = 10000)
    public void testDispatchLargeNumberCommandForDifferentAggregates() throws Throwable {
        testSubject = new DisruptorCommandBus(
                inMemoryEventStore, eventBus,
                new DisruptorConfiguration().setClaimStrategy(new MultiThreadedClaimStrategy(4))
                                            .setWaitStrategy(new SleepingWaitStrategy())
                                            .setRollbackConfiguration(new RollbackOnAllExceptionsConfiguration())
                                            .setInvokerThreadCount(2)
                                            .setPublisherThreadCount(3));
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(ErrorCommand.class.getName(), stubHandler);
        Repository<StubAggregate> spiedRepository = spy(testSubject
                                                                .createRepository(new GenericAggregateFactory<StubAggregate>(
                                                                        StubAggregate.class)));
        stubHandler.setRepository(spiedRepository);
        final Map<Object, Object> garbageCollectionPrevention = new ConcurrentHashMap<Object, Object>();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                garbageCollectionPrevention.put(invocation.getArguments()[0], new Object());
                return invocation.callRealMethod();
            }
        }).when(spiedRepository).add(isA(StubAggregate.class));
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object aggregate = invocation.callRealMethod();
                garbageCollectionPrevention.put(aggregate, new Object());
                return aggregate;
            }
        }).when(spiedRepository).load(isA(Object.class));
        final UnitOfWorkListener mockUnitOfWorkListener = mock(UnitOfWorkListener.class);
        when(mockUnitOfWorkListener.onEventRegistered(isA(UnitOfWork.class), any(EventMessage.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return invocation.getArguments()[0];
                    }
                });

        for (int a = 0; a < AGGREGATE_COUNT; a++) {
            testSubject.dispatch(new GenericCommandMessage<CreateCommand>(new CreateCommand(aggregateIdentifier[a])));
        }
        CommandCallback mockCallback = mock(CommandCallback.class);
        for (int t = 0; t < COMMAND_COUNT; t++) {
            for (int a = 0; a < AGGREGATE_COUNT; a++) {
                CommandMessage command;
                if (t == 10) {
                    command = new GenericCommandMessage<ErrorCommand>(new ErrorCommand(aggregateIdentifier[a]));
                } else {
                    command = new GenericCommandMessage<StubCommand>(new StubCommand(aggregateIdentifier[a]));
                }
                testSubject.dispatch(command, mockCallback);
            }
        }

        testSubject.stop();
        assertEquals(20, garbageCollectionPrevention.size());
        // only the commands executed after the failed ones will cause a readEvents() to occur
        assertEquals(10, inMemoryEventStore.loadCounter.get());
        assertEquals((COMMAND_COUNT * AGGREGATE_COUNT) + (2 * AGGREGATE_COUNT),
                     inMemoryEventStore.storedEventCounter.get());
        verify(mockCallback, times(990)).onSuccess(any());
        verify(mockCallback, times(10)).onFailure(isA(RuntimeException.class));
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
        protected Collection<EventSourcedEntity> getChildEntities() {
            return Collections.emptyList();
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private final Map<String, DomainEventMessage> storedEvents = new ConcurrentHashMap<String, DomainEventMessage>();
        private final AtomicInteger storedEventCounter = new AtomicInteger();
        private final AtomicInteger loadCounter = new AtomicInteger();

        @Override
        public void appendEvents(String type, DomainEventStream events) {
            if (!events.hasNext()) {
                return;
            }
            String key = events.peek().getAggregateIdentifier().toString();
            DomainEventMessage<?> lastEvent = null;
            while (events.hasNext()) {
                storedEventCounter.incrementAndGet();
                lastEvent = events.next();
                if (FailingEvent.class.isAssignableFrom(lastEvent.getPayloadType())) {
                    throw new RuntimeException("This is a failing event. EventStore refuses to store that");
                }
            }
            storedEvents.put(key, lastEvent);
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier) {
            loadCounter.incrementAndGet();
            DomainEventMessage message = storedEvents.get(identifier.toString());
            if (message == null) {
                throw new EventStreamNotFoundException(type, identifier);
            }
            return new SimpleDomainEventStream(Collections.singletonList(message));
        }
    }

    private static class StubCommand {

        @TargetAggregateIdentifier
        private Object aggregateIdentifier;

        public StubCommand(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public Object getAggregateIdentifier() {
            return aggregateIdentifier;
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
}
