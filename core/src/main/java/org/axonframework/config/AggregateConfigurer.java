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
import org.axonframework.commandhandling.CommandTargetResolver;
import org.axonframework.commandhandling.disruptor.DisruptorCommandBus;
import org.axonframework.commandhandling.model.GenericJpaRepository;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventsourcing.*;
import org.axonframework.eventsourcing.eventstore.EventStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class AggregateConfigurer<A> implements AggregateConfiguration<A> {
    private final Class<A> aggregate;

    private final Component<AggregateAnnotationCommandHandler> commandHandler;
    private final Component<Repository<A>> repository;
    private final Component<AggregateFactory<A>> aggregateFactory;
    private final Component<SnapshotTriggerDefinition> snapshotTriggerDefinition;
    private final Component<CommandTargetResolver> commandTargetResolver;
    private Configuration parent;

    private List<Registration> registrations = new ArrayList<>();

    public static <A> AggregateConfigurer<A> defaultConfiguration(Class<A> aggregate) {
        return new AggregateConfigurer<>(aggregate);
    }

    public static <A> AggregateConfigurer<A> jpaMappedConfiguration(Class<A> aggregate,
                                                                    EntityManagerProvider entityManagerProvider) {
        return new AggregateConfigurer<>(aggregate)
                .configureRepository(c -> new GenericJpaRepository<>(entityManagerProvider, aggregate, c.eventBus()));
    }

    protected AggregateConfigurer(Class<A> aggregate) {
        this.aggregate = aggregate;

        commandTargetResolver = new Component<>(() -> parent, name("commandTargetResolver"),
                                                c -> new AnnotationCommandTargetResolver());
        snapshotTriggerDefinition = new Component<>(() -> parent, name("snapshotTriggerDefinition"),
                                                    c -> NoSnapshotTriggerDefinition.INSTANCE);
        aggregateFactory = new Component<>(() -> parent, name("aggregateFactory"),
                                           c -> new GenericAggregateFactory<>(aggregate));
        repository = new Component<>(() -> parent, "Repository<" + aggregate.getSimpleName() + ">",
                                     c -> {
                                         Assert.state(c.eventBus() instanceof EventStore, "Default configuration requires the use of event sourcing. Either configure an Event Store to use, or configure a specific repository implementation for " + aggregate.toString());
                                         if (c.commandBus() instanceof DisruptorCommandBus) {
                                             return ((DisruptorCommandBus) c.commandBus())
                                                     .createRepository(aggregateFactory.get(),
                                                                       c.parameterResolverFactory());
                                         }
                                         return new EventSourcingRepository<>(aggregateFactory.get(),
                                                                              c.eventStore(),
                                                                              c.parameterResolverFactory(),
                                                                              snapshotTriggerDefinition.get());
                                     });
        commandHandler = new Component<>(() -> parent, "aggregateCommandHandler<" + aggregate.getSimpleName() + ">",
                                         c -> new AggregateAnnotationCommandHandler<>(aggregate, repository.get(),
                                                                                      commandTargetResolver.get(),
                                                                                      c.parameterResolverFactory()));
    }

    private String name(String prefix) {
        return prefix + "<" + aggregate.getSimpleName() + ">";
    }

    public AggregateConfigurer<A> configureRepository(Function<Configuration, Repository<A>> repositoryBuilder) {
        repository.update(repositoryBuilder);
        return this;
    }

    public AggregateConfigurer<A> configureAggregateFactory(Function<Configuration, AggregateFactory<A>> aggregateFactoryBuilder) {
        aggregateFactory.update(aggregateFactoryBuilder);
        return this;
    }

    public AggregateConfigurer<A> configureCommandHandler(Function<Configuration, AggregateAnnotationCommandHandler> aggregateCommandHandlerBuilder) {
        commandHandler.update(aggregateCommandHandlerBuilder);
        return this;
    }

    public AggregateConfigurer<A> configureCommandTargetResolver(Function<Configuration, CommandTargetResolver> commandTargetResolverBuilder) {
        commandTargetResolver.update(commandTargetResolverBuilder);
        return this;
    }

    @Override
    public void initialize(Configuration parent) {
        this.parent = parent;
    }

    @Override
    public void start() {
        registrations.add(commandHandler.get().subscribe(parent.commandBus()));
    }

    @Override
    public void shutdown() {
        registrations.forEach(Registration::cancel);
        registrations.clear();
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
