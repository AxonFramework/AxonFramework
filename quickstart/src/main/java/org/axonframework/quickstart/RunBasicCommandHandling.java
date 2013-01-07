/*
 * Copyright (c) 2010-2013. Axon Framework
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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.fs.FileSystemEventStore;
import org.axonframework.eventstore.fs.SimpleEventFileResolver;
import org.axonframework.quickstart.api.CreateToDoItemCommand;
import org.axonframework.quickstart.api.MarkCompletedCommand;
import org.axonframework.quickstart.handler.CreateToDoCommandHandler;
import org.axonframework.quickstart.handler.MarkCompletedCommandHandler;
import org.axonframework.quickstart.handler.ToDoEventListener;
import org.axonframework.quickstart.handler.ToDoItem;

import java.io.File;

/**
 * Setting up the basic ToDoItem sample with as little as possible help from axon utilities. The configuration takes
 * place in java code. In this class we setup the application infrastructure.
 *
 * @author Jettro Coenradie
 */
public class RunBasicCommandHandling {

    public static void main(String[] args) {
        // let's start with the Command Bus
        CommandBus commandBus = new SimpleCommandBus();

        // the CommandGateway provides a friendlier API to send commands
        CommandGateway commandGateway = new DefaultCommandGateway(commandBus);

        // we'll store Events on the FileSystem, in the "events" folder
        EventStore eventStore = new FileSystemEventStore(new SimpleEventFileResolver(new File("./events")));

        // a Simple Event Bus will do
        EventBus eventBus = new SimpleEventBus();

        // we need to configure the repository
        EventSourcingRepository<ToDoItem> repository = new EventSourcingRepository<ToDoItem>(ToDoItem.class);
        repository.setEventStore(eventStore);
        repository.setEventBus(eventBus);

        // Register the Command Handlers with the command bus by subscribing to the name of the command
        commandBus.subscribe(CreateToDoItemCommand.class.getName(),
                new CreateToDoCommandHandler(repository));
        commandBus.subscribe(MarkCompletedCommand.class.getName(),
                new MarkCompletedCommandHandler(repository));

        // We register an event listener to see which events are created
        eventBus.subscribe(new ToDoEventListener());

        // and let's send some Commands on the CommandBus using the special runner configured with our CommandGateway.
        CommandGenerator.sendCommands(commandGateway);
    }
}
