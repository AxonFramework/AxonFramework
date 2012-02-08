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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventsourcing.AbstractEventSourcedEntity;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.UnitOfWork;

import java.util.Collection;
import java.util.Collections;
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
        eventStore.appendInitialEvent("test", new SimpleDomainEventStream(new GenericDomainEventMessage<SomeEvent>(
                aggregateIdentifier, 0, new SomeEvent())));

        final MyAggregate myAggregate = new MyAggregate(aggregateIdentifier);
        Repository<MyAggregate> repository = new Repository<MyAggregate>() {
            @Override
            public MyAggregate load(Object aggregateIdentifier, Long expectedVersion) {
                throw new UnsupportedOperationException("Not implemented yet");
            }

            @Override
            public MyAggregate load(Object aggregateIdentifier) {
                return myAggregate;
            }

            @Override
            public void add(MyAggregate aggregate) {
                throw new UnsupportedOperationException("Not implemented yet");
            }
        };
        cb.subscribe(String.class, new MyCommandHandler(repository));
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
        public UUID getIdentifier() {
            return identifier;
        }

        public void doSomething() {
            apply(new SomeEvent());
        }

        @Override
        protected Collection<AbstractEventSourcedEntity> getChildEntities() {
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
            repository.load(aggregateIdentifier).doSomething();
            return null;
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private DomainEventStream storedEvents;

        public void appendInitialEvent(String type, DomainEventStream events) {
            storedEvents = events;
        }

        @Override
        public void appendEvents(String type, DomainEventStream events) {
//            while (events.hasNext()) {
//                storedEvents.add(events.next());
//            }
        }

        @Override
        public DomainEventStream readEvents(String type, Object identifier) {
            System.out.println(".");
            return storedEvents;
        }
    }
}
