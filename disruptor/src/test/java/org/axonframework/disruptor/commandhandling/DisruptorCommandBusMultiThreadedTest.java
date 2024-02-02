/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.disruptor.commandhandling.utils.MockException;
import org.axonframework.disruptor.commandhandling.utils.SomethingDoneEvent;
import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.stubbing.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for the {@link DisruptorCommandBus} dedicated for a multi-threading solution.
 *
 * @author Allard Buijze
 */
class DisruptorCommandBusMultiThreadedTest {

    private static final int COMMAND_COUNT = 100;
    private static final int AGGREGATE_COUNT = 10;

    private InMemoryEventStore inMemoryEventStore;
    private DisruptorCommandBus testSubject;
    private Repository<StubAggregate> spiedRepository;

    @BeforeEach
    void setUp() {
        StubHandler stubHandler = new StubHandler();
        inMemoryEventStore = InMemoryEventStore.builder().build();
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
        spiedRepository = spy(testSubject.createRepository(
                inMemoryEventStore, new GenericAggregateFactory<>(StubAggregate.class)
        ));
        stubHandler.setRepository(spiedRepository);
    }

    @AfterEach
    void tearDown() {
        testSubject.stop();
    }

    @Test
    void dispatchLargeNumberCommandForDifferentAggregates() throws Exception {
        final Map<Object, Object> garbageCollectionPrevention = new ConcurrentHashMap<>();
        doAnswer(trackCreateAndLoad(garbageCollectionPrevention)).when(spiedRepository).newInstance(any());
        doAnswer(trackCreateAndLoad(garbageCollectionPrevention)).when(spiedRepository).load(isA(String.class));

        List<String> aggregateIdentifiers = IntStream.range(0, AGGREGATE_COUNT)
                                                     .mapToObj(i -> IdentifierFactory.getInstance()
                                                                                     .generateIdentifier())
                                                     .collect(toList());

        //noinspection unchecked
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);

        Stream<CommandMessage<Object>> commands = generateCommands(aggregateIdentifiers);
        commands.forEach(c -> testSubject.dispatch(c, mockCallback));

        testSubject.stop();
        // only the commands executed after the failed ones will cause a readEvents() to occur
        assertEquals(10, inMemoryEventStore.loadCounter.get());
        assertEquals(20, garbageCollectionPrevention.size());
        assertEquals((COMMAND_COUNT * AGGREGATE_COUNT) + (2 * AGGREGATE_COUNT),
                     inMemoryEventStore.storedEventCounter.get());
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback, times(1010)).onResult(any(), commandResultMessageCaptor.capture());
        assertEquals(10, commandResultMessageCaptor.getAllValues()
                                                   .stream()
                                                   .filter(ResultMessage::isExceptional)
                                                   .map(ResultMessage::exceptionResult)
                                                   .filter(e -> e instanceof MockException)
                                                   .count());
    }

    private Answer<Object> trackCreateAndLoad(Map<Object, Object> garbageCollectionPrevention) {
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
        protected void handle(EventMessage<?> event) {
            identifier = ((DomainEventMessage<?>) event).getAggregateIdentifier();
        }
    }

    private static class InMemoryEventStore extends AbstractEventBus implements EventStore {

        private final Map<String, DomainEventMessage<?>> storedEvents = new ConcurrentHashMap<>();
        private final AtomicInteger storedEventCounter = new AtomicInteger();
        private final AtomicInteger loadCounter = new AtomicInteger();

        private InMemoryEventStore(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        protected void commit(List<? extends EventMessage<?>> events) {
            if (events == null || events.isEmpty()) {
                return;
            }
            String key = ((DomainEventMessage<?>) events.get(0)).getAggregateIdentifier();
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
        public DomainEventStream readEvents(@Nonnull String identifier) {
            loadCounter.incrementAndGet();
            DomainEventMessage<?> message = storedEvents.get(identifier);
            return message == null ? DomainEventStream.of() : DomainEventStream.of(message);
        }

        @Override
        public void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot) {
        }

        @Override
        public TrackingEventStream openStream(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }

        private static class Builder extends AbstractEventBus.Builder {

            private InMemoryEventStore build() {
                return new InMemoryEventStore(this);
            }
        }
    }

    private static class StubCommand {

        @TargetAggregateIdentifier
        private final Object aggregateIdentifier;

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

    private static class StubHandler implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        private Repository<StubAggregate> repository;

        private StubHandler() {
        }

        @Override
        public Object handleSync(CommandMessage<?> command) throws Exception {
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
