/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.quickstart;

import org.axonframework.commandhandling.AggregateAnnotationCommandHandler;
import org.axonframework.commandhandling.disruptor.DisruptorCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.quickstart.annotated.ToDoItem;

/**
 * Setting up the basic ToDoItem sample with a disruptor command and event bus and a file based event store. The
 * configuration takes place using spring. We use annotations to find the command and event handlers.
 *
 * @author Allard Buijze
 */
public class RunDisruptorCommandBus {

    public static void main(String[] args) throws InterruptedException {
        // we'll store Events in memory
        EventStore eventStore = new EmbeddedEventStore(new InMemoryEventStorageEngine());

        // TODO: Listeners should subscribe to Event Store instead of Event Bus
        // we register the event handler
        eventStore.subscribe(new SimpleEventProcessor("processor", (EventListener) event -> {
            System.out.println(event.getPayload());
        }));

        // we use default settings for the disruptor command bus
        DisruptorCommandBus commandBus = new DisruptorCommandBus(eventStore);

        // now, we obtain a repository from the command bus
        Repository<ToDoItem> repository = commandBus.createRepository(new GenericAggregateFactory<>(ToDoItem.class));

        // we use the repository to register the command handler
        new AggregateAnnotationCommandHandler<>(ToDoItem.class, repository).subscribe(commandBus);

        // the CommandGateway provides a friendlier API to send commands
        CommandGateway commandGateway = new DefaultCommandGateway(commandBus);

        // and let's send some Commands on the CommandBus.
        CommandGenerator.sendCommands(commandGateway);

        // we need to stop the disruptor command bus, to make sure we release all resources
        commandBus.stop();
    }
}
