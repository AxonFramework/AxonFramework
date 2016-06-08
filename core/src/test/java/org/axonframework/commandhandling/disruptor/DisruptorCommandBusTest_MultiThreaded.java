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

import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.AggregateLifecycle;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.MockException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.AggregateIdentifier;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.assertEquals;
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
    @Test(timeout = 10000)
    public void testDispatchLargeNumberCommandForDifferentAggregates() throws Exception {
        testSubject = new DisruptorCommandBus(
                inMemoryEventStore, new DisruptorConfiguration().setBufferSize(4)
                        .setProducerType(ProducerType.MULTI)
                        .setWaitStrategy(new SleepingWaitStrategy())
                        .setRollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE)
                        .setInvokerThreadCount(2)
                        .setPublisherThreadCount(3));
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(ErrorCommand.class.getName(), stubHandler);
        Repository<StubAggregate> spiedRepository = spy(testSubject
                                                                .createRepository(new GenericAggregateFactory<>(
                                                                        StubAggregate.class)));
        stubHandler.setRepository(spiedRepository);
        final Map<Object, Object> garbageCollectionPrevention = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            Aggregate<StubAggregate> realAggregate = (Aggregate<StubAggregate>) invocation.callRealMethod();
            garbageCollectionPrevention.put(realAggregate, new Object());
            return realAggregate;
        }).when(spiedRepository).newInstance(any());
        doAnswer(invocation -> {
            Object aggregate = invocation.callRealMethod();
            garbageCollectionPrevention.put(aggregate, new Object());
            return aggregate;
        }).when(spiedRepository).load(isA(String.class));

        for (int a = 0; a < AGGREGATE_COUNT; a++) {
            testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier[a])));
        }
        CommandCallback mockCallback = mock(CommandCallback.class);
        for (int t = 0; t < COMMAND_COUNT; t++) {
            for (int a = 0; a < AGGREGATE_COUNT; a++) {
                CommandMessage command;
                if (t == 10) {
                    command = asCommandMessage(new ErrorCommand(aggregateIdentifier[a]));
                } else {
                    command = asCommandMessage(new StubCommand(aggregateIdentifier[a]));
                }
                testSubject.dispatch(command, mockCallback);
            }
        }

        testSubject.stop();
        // only the commands executed after the failed ones will cause a readEvents() to occur
        assertEquals(10, inMemoryEventStore.loadCounter.get());
        assertEquals(20, garbageCollectionPrevention.size());
        assertEquals((COMMAND_COUNT * AGGREGATE_COUNT) + (2 * AGGREGATE_COUNT),
                     inMemoryEventStore.storedEventCounter.get());
        verify(mockCallback, times(990)).onSuccess(any(), any());
        verify(mockCallback, times(10)).onFailure(any(), isA(RuntimeException.class));
    }

    private static class StubAggregate {

        @AggregateIdentifier
        private String identifier;

        private StubAggregate(String identifier) {
            this.identifier = identifier;
            AggregateLifecycle.apply(new SomethingDoneEvent());
        }

        @SuppressWarnings("UnusedDeclaration")
        public StubAggregate() {
        }

        public String getIdentifier() {
            return identifier;
        }

        public void doSomething() {
            AggregateLifecycle.apply(new SomethingDoneEvent());
        }

        public void createFailingEvent() {
            AggregateLifecycle.apply(new FailingEvent());
        }

        @EventSourcingHandler
        protected void handle(EventMessage event) {
            identifier = ((DomainEventMessage) event).getAggregateIdentifier();
        }
    }

    private static class InMemoryEventStore extends AbstractEventBus implements EventStore {

        private final Map<String, DomainEventMessage> storedEvents = new ConcurrentHashMap<>();
        private final AtomicInteger storedEventCounter = new AtomicInteger();
        private final AtomicInteger loadCounter = new AtomicInteger();

        @Override
        protected void commit(List<? extends EventMessage<?>> events) {
            if (events == null || events.isEmpty()) {
                return;
            }
            String key = ((DomainEventMessage) events.get(0)).getAggregateIdentifier();
            DomainEventMessage<?> lastEvent = null;
            for (EventMessage<?> event : events) {
                storedEventCounter.incrementAndGet();
                lastEvent = (DomainEventMessage<?>) event;
                if (FailingEvent.class.isAssignableFrom(lastEvent.getPayloadType())) {
                    throw new MockException("This is a failing event. EventStore refuses to store that");
                }
            }
            storedEvents.put(key, lastEvent);
        }

        @Override
        public DomainEventStream readEvents(String identifier) {
            loadCounter.incrementAndGet();
            DomainEventMessage<?> message = storedEvents.get(identifier);
            return message == null ? DomainEventStream.of() : DomainEventStream.of(message);
        }

        @Override
        public TrackingEventStream streamEvents(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }
    }

    private static class StubCommand {

        @TargetAggregateIdentifier
        private Object aggregateIdentifier;

        private StubCommand(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        private Object getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    private static class ErrorCommand extends StubCommand {

        private ErrorCommand(Object aggregateIdentifier) {
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

        private CreateCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class StubHandler implements MessageHandler<CommandMessage<?>> {

        private Repository<StubAggregate> repository;

        private StubHandler() {
        }

        @Override
        public Object handle(CommandMessage<?> command, UnitOfWork<? extends CommandMessage<?>> unitOfWork) throws Exception {
            StubCommand payload = (StubCommand) command.getPayload();
            if (ExceptionCommand.class.isAssignableFrom(command.getPayloadType())) {
                throw ((ExceptionCommand) command.getPayload()).getException();
            } else if (CreateCommand.class.isAssignableFrom(command.getPayloadType())) {
                Aggregate<StubAggregate> aggregate = repository.newInstance(() -> new StubAggregate(payload.getAggregateIdentifier().toString()));
                aggregate.execute(StubAggregate::doSomething);
            } else {
                Aggregate<StubAggregate> aggregate = repository.load(payload.getAggregateIdentifier().toString());
                if (ErrorCommand.class.isAssignableFrom(command.getPayloadType())) {
                    aggregate.execute(StubAggregate::createFailingEvent);
                } else {
                    aggregate.execute(StubAggregate::doSomething);
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
        public void publish(List<? extends EventMessage<?>> events) {
            publisherCountDown.countDown();
        }

        @Override
        public TrackingEventStream streamEvents(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration registerDispatchInterceptor(MessageDispatchInterceptor<EventMessage<?>> dispatchInterceptor) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FailingEvent {

    }
}
