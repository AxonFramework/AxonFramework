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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.DomainEventStream;
import org.axonframework.eventsourcing.EventSourcedEntity;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.SimpleDomainEventStream;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.repository.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Benchmark test created to compare
 *
 * @author Allard Buijze
 */
public class CommandHandlingBenchmark {

    private static final UUID aggregateIdentifier = UUID.randomUUID();

    public static void main(String[] args) {
        CommandBus cb = new SimpleCommandBus();

        InMemoryEventStore eventStore = new InMemoryEventStore();
        eventStore.appendInitialEvent(new SimpleDomainEventStream(new GenericDomainEventMessage<>(
                aggregateIdentifier.toString(), 0, new SomeEvent())));

        final MyAggregate myAggregate = new MyAggregate(aggregateIdentifier);
        Repository<MyAggregate> repository = new Repository<MyAggregate>() {
            @Override
            public MyAggregate load(String aggregateIdentifier, Long expectedVersion) {
                throw new UnsupportedOperationException("Not implemented yet");
            }

            @Override
            public MyAggregate load(String aggregateIdentifier) {
                return myAggregate;
            }

            @Override
            public void add(MyAggregate aggregate) {
                throw new UnsupportedOperationException("Not implemented yet");
            }
        };
        cb.subscribe(String.class.getName(), new MyCommandHandler(repository));
//        new AnnotationCommandHandlerAdapter(new MyCommandHandler(repository), cb).subscribe();


        long COMMAND_COUNT = 5 * 1000 * 1000;
        cb.dispatch(GenericCommandMessage.asCommandMessage("ready,"));
        long t1 = System.currentTimeMillis();
        for (int t = 0; t < COMMAND_COUNT; t++) {
            cb.dispatch(GenericCommandMessage.asCommandMessage("go!"));
        }
        long t2 = System.currentTimeMillis();
        System.out.println(String.format("Just did %d commands per second", ((COMMAND_COUNT * 1000) / (t2 - t1))));
    }

    private static class MyAggregate extends AbstractAnnotatedAggregateRoot {

        private final UUID identifier;

        protected MyAggregate() {
            this(UUID.randomUUID());
        }

        protected MyAggregate(UUID identifier) {
            this.identifier = identifier;
        }

        @Override
        public String getIdentifier() {
            return identifier.toString();
        }

        public void doSomething() {
            apply(new SomeEvent());
        }

        @Override
        protected Collection<EventSourcedEntity> getChildEntities() {
            return Collections.emptyList();
        }
    }

    private static class SomeEvent {

    }

    private static class MyCommandHandler implements CommandHandler<String> {

        private final Repository<MyAggregate> repository;

        public MyCommandHandler(Repository<MyAggregate> repository) {
            this.repository = repository;
        }

        @Override
        public Object handle(CommandMessage<String> command, UnitOfWork unitOfWork) throws Throwable {
            repository.load(aggregateIdentifier.toString()).doSomething();
            return null;
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private DomainEventStream storedEvents;

        public void appendInitialEvent(DomainEventStream events) {
            storedEvents = events;
        }

        @Override
        public void appendEvents(List<DomainEventMessage<?>> events) {
//            while (events.hasNext()) {
//                storedEvents.add(events.next());
//            }
        }

        @Override
        public DomainEventStream readEvents(String identifier) {
            System.out.println(".");
            return storedEvents;
        }

        @Override
        public DomainEventStream readEvents(String identifier, long firstSequenceNumber,
                                            long lastSequenceNumber) {
            throw new UnsupportedOperationException("Not implemented");
        }

    }
}
