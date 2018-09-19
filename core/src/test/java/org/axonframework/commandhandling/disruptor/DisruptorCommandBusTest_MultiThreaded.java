/*
 * Copyright (c) 2010-2017. Axon Framework
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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.commandhandling.model.AggregateLifecycle;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.MockException;
import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.junit.*;
import org.mockito.stubbing.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DisruptorCommandBusTest_MultiThreaded {

    private static final int COMMAND_COUNT = 100;
    private static final int AGGREGATE_COUNT = 10;
    private InMemoryEventStore inMemoryEventStore;
    private DisruptorCommandBus testSubject;
    private Repository<StubAggregate> spiedRepository;

    @Before
    public void setUp() {
        StubHandler stubHandler = new StubHandler();
        inMemoryEventStore = new InMemoryEventStore();
        testSubject = DisruptorCommandBus.builder()
                                         .bufferSize(4)
                                         .producerType(ProducerType.MULTI)
                                         .waitStrategy(new SleepingWaitStrategy())
                                         .rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE)
                                         .invokerThreadCount(2)
                                         .publisherThreadCount(3)
                                         .build();
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(ErrorCommand.class.getName(), stubHandler);
        spiedRepository = spy(testSubject.createRepository(inMemoryEventStore,
                                                           new GenericAggregateFactory<>(StubAggregate.class)));
        stubHandler.setRepository(spiedRepository);
    }

    @After
    public void tearDown() {
        testSubject.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDispatchLargeNumberCommandForDifferentAggregates() throws Exception {
        final Map<Object, Object> garbageCollectionPrevention = new ConcurrentHashMap<>();
        doAnswer(trackCreateAndLoad(garbageCollectionPrevention)).when(spiedRepository).newInstance(any());
        doAnswer(trackCreateAndLoad(garbageCollectionPrevention)).when(spiedRepository).load(isA(String.class));

        List<String> aggregateIdentifiers = IntStream.range(0, AGGREGATE_COUNT)
                                                     .mapToObj(i -> IdentifierFactory.getInstance()
                                                                                     .generateIdentifier())
                                                     .collect(toList());

        CommandCallback mockCallback = mock(CommandCallback.class);

        Stream<CommandMessage<Object>> commands = generateCommands(aggregateIdentifiers);
        commands.forEach(c -> testSubject.dispatch(c, mockCallback));

        testSubject.stop();
        // only the commands executed after the failed ones will cause a readEvents() to occur
        assertEquals(10, inMemoryEventStore.loadCounter.get());
        assertEquals(20, garbageCollectionPrevention.size());
        assertEquals((COMMAND_COUNT * AGGREGATE_COUNT) + (2 * AGGREGATE_COUNT),
                     inMemoryEventStore.storedEventCounter.get());
        verify(mockCallback, times(1000)).onSuccess(any(), any());
        verify(mockCallback, times(10)).onFailure(any(), isA(MockException.class));
    }

    private Answer trackCreateAndLoad(Map<Object, Object> garbageCollectionPrevention) {
        return invocation -> {
            Object realAggregate = invocation.callRealMethod();
            garbageCollectionPrevention.put(realAggregate, new Object());
            return realAggregate;
        };
    }

    private Stream<CommandMessage<Object>> generateCommands(List<String> aggregateIdentifiers) {
        Stream<CommandMessage<Object>> create = aggregateIdentifiers.stream()
                                                                    .map(CreateCommand::new)
                                                                    .map(GenericCommandMessage::asCommandMessage);
        Stream<CommandMessage<Object>> head = IntStream.range(0, 10)
                                                       .mapToObj(k -> aggregateIdentifiers.stream()
                                                                                          .map(StubCommand::new)
                                                                                          .map(GenericCommandMessage::asCommandMessage))
                                                       .reduce(Stream.of(), Stream::concat);
        Stream<CommandMessage<Object>> errors = aggregateIdentifiers.stream()
                                                                    .map(ErrorCommand::new)
                                                                    .map(GenericCommandMessage::asCommandMessage);
        Stream<CommandMessage<Object>> tail = IntStream.range(11, 100)
                                                       .mapToObj(k -> aggregateIdentifiers.stream()
                                                                                          .map(StubCommand::new)
                                                                                          .map(GenericCommandMessage::asCommandMessage))
                                                       .reduce(Stream.of(), Stream::concat);

        return Stream.of(create, head, errors, tail).flatMap(identity());
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
        public void storeSnapshot(DomainEventMessage<?> snapshot) {
        }

        @Override
        public TrackingEventStream openStream(TrackingToken trackingToken) {
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
        public Object handle(CommandMessage<?> command) throws Exception {
            StubCommand payload = (StubCommand) command.getPayload();
            if (ExceptionCommand.class.isAssignableFrom(command.getPayloadType())) {
                throw ((ExceptionCommand) command.getPayload()).getException();
            } else if (CreateCommand.class.isAssignableFrom(command.getPayloadType())) {
                Aggregate<StubAggregate> aggregate = repository
                        .newInstance(() -> new StubAggregate(payload.getAggregateIdentifier().toString()));
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

    private static class FailingEvent {

    }
}
