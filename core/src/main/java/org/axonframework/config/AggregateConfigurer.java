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

package org.axonframework.config;

import org.axonframework.commandhandling.AggregateAnnotationCommandHandler;
import org.axonframework.commandhandling.AnnotationCommandTargetResolver;
import org.axonframework.commandhandling.disruptor.DisruptorCommandBus;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.Assert;
import org.axonframework.eventsourcing.*;
import org.axonframework.eventsourcing.eventstore.EventStore;

import java.util.function.Function;

public class AggregateConfigurer<A> implements AggregateConfiguration<A> {
    private final Class<A> aggregate;

    private final Component<AggregateAnnotationCommandHandler> commandHandler;
    private final Component<Repository<A>> repository;
    private final Component<AggregateFactory<A>> aggregateFactory;
    private final Component<SnapshotTriggerDefinition> snapshotTriggerDefinition;
    private Configuration parent;

    public static <A> AggregateConfigurer<A> defaultConfiguration(Class<A> aggregate) {
        return new AggregateConfigurer<>(aggregate);
    }

    protected AggregateConfigurer(Class<A> aggregate) {
        this.aggregate = aggregate;

        snapshotTriggerDefinition = new Component<>(() -> parent, name("snapshotTriggerDefinition"),
                                                    c -> NoSnapshotTriggerDefinition.INSTANCE);
        aggregateFactory = new Component<>(() -> parent, name("aggregateFactory"),
                                           c -> new GenericAggregateFactory<>(aggregate));
        repository = new Component<>(() -> parent, "Repository<" + aggregate.getSimpleName() + ">",
                                     c -> {
                                         if (c.commandBus() instanceof DisruptorCommandBus) {
                                             return ((DisruptorCommandBus) c.commandBus())
                                                     .createRepository(aggregateFactory.get(),
                                                                       c.parameterResolverFactory());
                                         }
                                         Assert.state(c.eventBus() instanceof EventStore, "Default configuration requires the use of event sourcing. Either configure an Event Store to use, or configure a specific repository implementation for " + aggregate.toString());
                                         return new EventSourcingRepository<>(aggregateFactory.get(),
                                                                              (EventStore) c.eventBus(),
                                                                              c.parameterResolverFactory(),
                                                                              snapshotTriggerDefinition.get());
                                     });
        commandHandler = new Component<>(() -> parent, "aggregateCommandHandler<" + aggregate.getSimpleName() + ">",
                                         c -> new AggregateAnnotationCommandHandler<>(aggregate, repository.get(),
                                                                                      new AnnotationCommandTargetResolver(),
                                                                                      c.parameterResolverFactory()));
    }

    private String name(String prefix) {
        return prefix + "<" + aggregate.getSimpleName() + ">";
    }

    public AggregateConfigurer<A> useRepository(Function<Configuration, Repository<A>> repositoryBuilder) {
        repository.update(repositoryBuilder);
        return this;
    }

    @Override
    public void initialize(Configuration parent) {
        this.parent = parent;
    }

    @Override
    public void start() {
        commandHandler.get().subscribe(parent.commandBus());
    }

    @Override
    public void shutdown() {

    }

    @Override
    public Repository<A> repository() {
        return repository.get();
    }

    @Override
    public Class<A> aggregateType() {
        return aggregate;
    }
}
