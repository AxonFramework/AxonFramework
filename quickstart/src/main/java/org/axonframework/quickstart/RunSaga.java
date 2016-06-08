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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.SimpleResourceInjector;
import org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.java.SimpleEventScheduler;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.quickstart.api.MarkToDoItemOverdueCommand;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;
import org.axonframework.quickstart.saga.ToDoSaga;

import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;

/**
 * Simple Example that shows how to Create Saga instances, schedule deadlines and inject resources
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class RunSaga {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws InterruptedException {

        // first of all, we need an Event Bus
        EventBus eventBus = new SimpleEventBus();

        // Sagas often need to send commands, so let's create a Command Bus
        CommandBus commandBus = new SimpleCommandBus();

        // a CommandGateway has a much nicer API
        CommandGateway commandGateway = new DefaultCommandGateway(commandBus);

        // let's register a Command Handler that writes to System Out so we can see what happens
        commandBus.subscribe(MarkToDoItemOverdueCommand.class.getName(),
                             (CommandMessage<?> commandMessage, UnitOfWork<? extends CommandMessage<?>> unitOfWork) -> {
                                 System.out.println(String.format("Got command to mark [%s] overdue!",
                                         ((MarkToDoItemOverdueCommand) commandMessage.getPayload()).getTodoId()));
                                 return null;
                             });

        // The Saga will schedule some deadlines in our sample
        final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor();
        EventScheduler eventScheduler = new SimpleEventScheduler(executorService, eventBus);

        // this will allow the eventScheduler and commandGateway to be injected in our Saga
        SimpleResourceInjector resourceInjector = new SimpleResourceInjector(eventScheduler, commandGateway);

        // we need to store a Saga somewhere. Let's do that in memory for now
        SagaRepository<ToDoSaga> repository = new AnnotatedSagaRepository<>(ToDoSaga.class, new InMemorySagaStore(),
                                                                            resourceInjector);

        // Sagas instances are managed and tracked by a SagaManager.
        AnnotatedSagaManager sagaManager = new AnnotatedSagaManager(ToDoSaga.class, repository, ToDoSaga::new, RollbackConfigurationType.UNCHECKED_EXCEPTIONS);

        // and we need to subscribe the Saga Manager to the Event Bus
        eventBus.subscribe(sagaManager);

        // That's the infrastructure we need...
        // Let's pretend a few things are happening

        // We create 2 items
        eventBus.publish(asEventMessage(new ToDoItemCreatedEvent("todo1", "Got something to do")));
        eventBus.publish(asEventMessage(new ToDoItemCreatedEvent("todo2", "Got something else to do")));
        // We mark the first completed, before the deadline expires. The Saga has a hard-coded deadline of 2 seconds
        eventBus.publish(asEventMessage(new ToDoItemCompletedEvent("todo1")));
        // we wait 3 seconds. Enough time for the deadline to expire
        Thread.sleep(3000);
        // Just a System out to remind us that we should see something
        System.out.println("Should have seen an item marked overdue, now");

        // to make sure the JVM ends, we shut down any threads created by the ExecutorService.
        executorService.shutdown();
    }
}
