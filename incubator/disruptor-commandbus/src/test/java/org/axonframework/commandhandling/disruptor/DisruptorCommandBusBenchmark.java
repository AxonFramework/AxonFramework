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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.annotation.CommandMessage;
import org.axonframework.commandhandling.annotation.GenericCommandMessage;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.AbstractEventSourcedEntity;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.Repository;
import org.axonframework.serializer.XStreamSerializer;
import org.axonframework.unitofwork.UnitOfWork;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class DisruptorCommandBusBenchmark {


    private static final long COMMAND_COUNT = 1000L * 1000L;

    public static void main(String[] args) throws InterruptedException {
        EventBus eventBus = new SimpleEventBus();
        StubHandler stubHandler = new StubHandler();
        InMemoryEventStore inMemoryEventStore = new InMemoryEventStore();
        PlatformTransactionManager transactionManager = null;
        DisruptorCommandBus commandBus =
                new DisruptorCommandBus(512, new GenericAggregateFactory<StubAggregate>(StubAggregate.class),
                                        inMemoryEventStore,
                                        eventBus, new XStreamSerializer());
        commandBus.subscribe(StubCommand.class, stubHandler);
        stubHandler.setRepository(commandBus);
        final String aggregateIdentifier = "MyID";
        inMemoryEventStore.appendEvents(StubAggregate.class.getSimpleName(), new SimpleDomainEventStream(
                new GenericDomainEventMessage<StubDomainEvent>(aggregateIdentifier, 0, new StubDomainEvent())));

        System.out.println("Press enter to start");
        new Scanner(System.in).nextLine();
        long start = System.currentTimeMillis();
        for (int i = 0; i < COMMAND_COUNT; i++) {
            CommandMessage<StubCommand> command = new GenericCommandMessage<StubCommand>(new StubCommand(
                    aggregateIdentifier));
            commandBus.dispatch(command);
        }
        System.out.println("Finished dispatching!");

        inMemoryEventStore.countDownLatch.await(30, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        assertEquals("Seems that some events are missing", 0, inMemoryEventStore.countDownLatch.getCount());

        System.out.println("Did " + ((COMMAND_COUNT * 1000L) / (end - start)) + " commands per second");
        commandBus.stop();
    }

    private static class StubAggregate extends AbstractEventSourcedAggregateRoot {

        private int timesDone = 0;
        private final UUID identifier;

        private StubAggregate() {
            this(UUID.randomUUID());
        }

        private StubAggregate(UUID identifier) {
            this.identifier = identifier;
        }

        @Override
        public Object getIdentifier() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        public void doSomething() {
            apply(new SomethingDoneEvent());
        }

        @Override
        protected void handle(DomainEventMessage event) {
            if (event instanceof StubDomainEvent) {
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
            DomainEventMessage<? extends Object> lastEvent = null;
            while (events.hasNext()) {
                countDownLatch.countDown();
                lastEvent = events.next();
            }
            storedEvents.put(key, lastEvent);
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier) {
            return new SimpleDomainEventStream(Collections.singletonList(storedEvents.get(identifier.toString())));
        }
    }

    private static class StubCommand implements IdentifiedCommand {

        private Object agregateIdentifier;

        public StubCommand(Object agregateIdentifier) {
            this.agregateIdentifier = agregateIdentifier;
        }

        @Override
        public Object getAggregateIdentifier() {
            return agregateIdentifier;
        }
    }

    private static class StubHandler implements CommandHandler<StubCommand> {

        private Repository<StubAggregate> repository;

        private StubHandler() {
        }

        @Override
        public Object handle(CommandMessage<StubCommand> command, UnitOfWork unitOfWork) throws Throwable {
            repository.load(command.getPayload().getAggregateIdentifier()).doSomething();
            return Void.TYPE;
        }

        public void setRepository(Repository<StubAggregate> repository) {
            this.repository = repository;
        }
    }

    private static class StubDomainEvent {
    }
}
