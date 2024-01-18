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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.AbstractRepository;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;

import java.util.UUID;
import java.util.concurrent.Callable;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Benchmark test created to compare
 *
 * @author Allard Buijze
 */
public class CommandHandlingBenchmark {

    private static final UUID aggregateIdentifier = UUID.randomUUID();

    public static void main(String[] args) {
        EventStore eventStore = EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build();
        CommandBus cb = SimpleCommandBus.builder().build();
        eventStore.publish(new GenericDomainEventMessage<>("type", aggregateIdentifier.toString(), 0, new SomeEvent()));

        final MyAggregate myAggregate = new MyAggregate(aggregateIdentifier);
        Repository<MyAggregate> repository = new AbstractRepository<MyAggregate, AnnotatedAggregate<MyAggregate>>(
                new AbstractRepository.Builder<MyAggregate>(MyAggregate.class) {
                }
        ) {

            @Override
            protected AnnotatedAggregate<MyAggregate> doCreateNew(Callable<MyAggregate> factoryMethod) {
                throw new UnsupportedOperationException("Not implemented yet");
            }

            @Override
            protected void doSave(AnnotatedAggregate<MyAggregate> aggregate) {
            }

            @Override
            protected AnnotatedAggregate<MyAggregate> doLoad(String aggregateIdentifier, Long expectedVersion) {
                return AnnotatedAggregate.initialize(myAggregate, aggregateModel(), eventStore);
            }

            @Override
            protected void doDelete(AnnotatedAggregate<MyAggregate> aggregate) {

            }
        };
        cb.subscribe(String.class.getName(), new MyCommandHandler(repository));

        long COMMAND_COUNT = 5 * 1000 * 1000;
        cb.dispatch(GenericCommandMessage.asCommandMessage("ready,"), ProcessingContext.NONE);
        long t1 = System.currentTimeMillis();
        for (int t = 0; t < COMMAND_COUNT; t++) {
            cb.dispatch(GenericCommandMessage.asCommandMessage("go!"), ProcessingContext.NONE);
        }
        long t2 = System.currentTimeMillis();
        System.out.printf("Just did %d commands per second%n", ((COMMAND_COUNT * 1000) / (t2 - t1)));
    }

    private static class MyAggregate {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        @AggregateIdentifier
        private final UUID identifier;

        @SuppressWarnings("unused")
        protected MyAggregate() {
            this(UUID.randomUUID());
        }

        private MyAggregate(UUID identifier) {
            this.identifier = identifier;
        }

        public void doSomething() {
            apply(new SomeEvent());
        }
    }

    private static class SomeEvent {

    }

    private static class MyCommandHandler implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        private final Repository<MyAggregate> repository;

        private MyCommandHandler(Repository<MyAggregate> repository) {
            this.repository = repository;
        }

        @Override
        public Object handleSync(CommandMessage<?> command) {
            repository.load(aggregateIdentifier.toString()).execute(MyAggregate::doSomething);
            return null;
        }
    }
}
