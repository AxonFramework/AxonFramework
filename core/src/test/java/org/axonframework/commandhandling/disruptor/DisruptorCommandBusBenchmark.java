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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.model.AggregateLifecycle;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.*;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asStream;
import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class DisruptorCommandBusBenchmark {

    private static final int COMMAND_COUNT = 50 * 1000 * 1000;

    public static void main(String[] args) throws InterruptedException {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        StubHandler stubHandler = new StubHandler();
        DisruptorCommandBus commandBus = new DisruptorCommandBus(eventStore);
        commandBus.subscribe(StubCommand.class.getName(), stubHandler);
        stubHandler.setRepository(commandBus.createRepository(new GenericAggregateFactory<>(StubAggregate.class)));
        final String aggregateIdentifier = "MyID";
        eventStore.publish(new GenericDomainEventMessage<>("type", aggregateIdentifier, 0, new StubDomainEvent()));

        long start = System.currentTimeMillis();
        for (int i = 0; i < COMMAND_COUNT; i++) {
            CommandMessage<StubCommand> command = asCommandMessage(new StubCommand(aggregateIdentifier));
            commandBus.dispatch(command);
        }
        System.out.println("Finished dispatching!");

        eventStore.countDownLatch.await(5, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        try {
            assertEquals("Seems that some events are not stored", COMMAND_COUNT, asStream(eventStore.readEvents(aggregateIdentifier)).count());
            System.out.println("Did " + ((COMMAND_COUNT * 1000L) / (end - start)) + " commands per second");
        } finally {
            commandBus.stop();
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private final Map<String, DomainEventMessage> storedEvents = new HashMap<>();
        private final CountDownLatch countDownLatch = new CountDownLatch((int) (COMMAND_COUNT + 1L));

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
            }
            storedEvents.put(key, lastEvent);
        }

        @Override
        public DomainEventStream readEvents(String identifier) {
            return DomainEventStream.of(storedEvents.get(identifier));
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

    private static class StubAggregate {

        @AggregateIdentifier
        private String identifier;

        public String getIdentifier() {
            return identifier;
        }

        public void doSomething() {
            AggregateLifecycle.apply(new SomethingDoneEvent());
        }

        @EventSourcingHandler
        protected void handle(EventMessage event) {
            identifier = ((DomainEventMessage<?>) event).getAggregateIdentifier();
        }
    }

    private static class StubCommand {

        @TargetAggregateIdentifier
        private String aggregateIdentifier;

        public StubCommand(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    private static class StubHandler implements MessageHandler<CommandMessage<?>> {

        private Repository<StubAggregate> repository;

        private StubHandler() {
        }

        @Override
        public Object handle(CommandMessage<?> message,
                             UnitOfWork<? extends CommandMessage<?>> unitOfWork) throws Exception {
            StubCommand payload = (StubCommand) message.getPayload();
            repository.load(payload.getAggregateIdentifier()).execute(StubAggregate::doSomething);
            return null;
        }

        public void setRepository(Repository<StubAggregate> repository) {
            this.repository = repository;
        }
    }

    private static class StubDomainEvent {

    }
}
