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
import org.axonframework.commandhandling.AnnotationCommandTargetResolver;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.NoSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.quickstart.annotated.ToDoEventHandler;
import org.axonframework.quickstart.annotated.ToDoItem;
import org.axonframework.spring.config.AnnotationDriven;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AnnotationDriven
@Configuration
public class AxonSpringConfiguration {

    @Bean
    public CommandBus commandBus() {
        return new SimpleCommandBus();
    }

    @Bean
    public EventStore eventStore() {
        return new EmbeddedEventStore(new InMemoryEventStorageEngine());
    }

    @Bean
    public CommandGateway commandGateway() {
        return new DefaultCommandGateway(commandBus());
    }

    @Bean
    public Repository<ToDoItem> repository(ParameterResolverFactory parameterResolverFactory) {
        return new EventSourcingRepository<>(new GenericAggregateFactory<>(ToDoItem.class), eventStore(), parameterResolverFactory,
                                             NoSnapshotTriggerDefinition.INSTANCE);
    }

    @Bean
    public AggregateAnnotationCommandHandler aggregateAnnotationCommandHandler(ParameterResolverFactory parameterResolverFactory, Repository<ToDoItem> repository) {
        AggregateAnnotationCommandHandler<ToDoItem> ch = new AggregateAnnotationCommandHandler<>(ToDoItem.class, repository, new AnnotationCommandTargetResolver(), parameterResolverFactory);
        ch.subscribe(commandBus());
        return ch;
    }

    @Bean
    public ToDoEventHandler eventHandler() {
        return new ToDoEventHandler();
    }

    @Bean
    public SubscribingEventProcessor eventProcessor() {
        SubscribingEventProcessor eventProcessor = new SubscribingEventProcessor("eventProcessor", new SimpleEventHandlerInvoker(eventHandler()), eventStore());
        eventProcessor.start();
        return eventProcessor;
    }
}
