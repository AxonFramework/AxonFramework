/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.config;


import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

class StatefulCommandHandlingComponentTest {

    private final Logger logger = LoggerFactory.getLogger(StatefulCommandHandlingComponentTest.class);

    @Test
    void name() throws ExecutionException, InterruptedException {
        SimpleEventStore eventStore = new SimpleEventStore(
                new AsyncInMemoryEventStorageEngine(),
                "default",
                event -> {
                    if (event.getPayload() instanceof NameChangedEvent) {
                        return Collections.singleton(new Tag("MyModel", ((NameChangedEvent) event.getPayload()).id()));
                    }
                    return new HashSet<>();
                }
        );
        AsyncEventSourcingRepository<String, MyModel> repository = new AsyncEventSourcingRepository<>(
                eventStore,
                myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("MyModel", myModelId)),
                (model, em) -> {
                    if (em.getPayload() instanceof NameChangedEvent) {
                        logger.info("Handling ES event: {}", em.getPayload());
                        model.setName(((NameChangedEvent) em.getPayload()).name());
                    }
                    return model;
                },
                id -> {
                    logger.info("Creating model with id: {}", id);
                    return new MyModel(id);
                },
                "default"
        );
        Function<CommandMessage<?>, String> CommandIdResolver = command -> {
            if (command.getPayload() instanceof MyChangeNameCommand(String id, String name)) {
                return id;
            }
            throw new IllegalArgumentException("Unsupported command");
        };

        StatefulCommandHandlingComponent<String, MyModel> component = new StatefulCommandHandlingComponent<>(
                MyModel.class,
                repository,
                CommandIdResolver
        ).subscribe(new QualifiedName(MyChangeNameCommand.class), (command, model, context) -> {
             logger.info("Handling command: {}. Model resolved before: {}", command, model);
             MyChangeNameCommand payload = (MyChangeNameCommand) command.getPayload();
             eventStore.transaction(context, "default")
                       .appendEvent(new GenericEventMessage<>(new MessageType(NameChangedEvent.class),
                                                              new NameChangedEvent(model.id, payload.name())));

             logger.info("Model after: {}", model);
             return MessageStream.empty();
         });


        // TODO: Repository doesn't work with empty store?
        AsyncUnitOfWork uow0 = new AsyncUnitOfWork();
        uow0.executeWithResult((context) -> {
            eventStore.publish(context,
                               "default",
                               new GenericEventMessage<>(new MessageType(NameChangedEvent.class),
                                                         new NameChangedEvent("my-id", "name-0"))
            );
            return CompletableFuture.completedFuture(null);
        }).get();

        updateName("name-1", component);
        updateName("name-2", component);
        updateName("name-3", component);
        updateName("name-4", component);
        updateName("name-5", component);
    }

    private static void updateName(String name2, StatefulCommandHandlingComponent<String, MyModel> component)
            throws InterruptedException, ExecutionException {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult((context) -> {
            GenericCommandMessage<MyChangeNameCommand> command = new GenericCommandMessage<>(
                    new MessageType(MyChangeNameCommand.class),
                    new MyChangeNameCommand("my-id", name2));
            return component.handle(command, context).firstAsCompletableFuture();
        }).get();
    }


    record MyChangeNameCommand(
            String id,
            String name
    ) {

    }

    static class MyModel {

        private String id;
        private String name;

        public MyModel(String id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return id + " " + name;
        }
    }

    record NameChangedEvent(
            String id,
            String name
    ) {

    }
}