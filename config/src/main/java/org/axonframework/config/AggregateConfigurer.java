/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.disruptor.commandhandling.DisruptorCommandBus;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.NoSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.AnnotationCommandTargetResolver;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.GenericJpaRepository;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.inspection.AggregateMetaModelFactory;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.lang.String.format;
import static org.axonframework.common.Assert.state;

/**
 * Axon Configuration API extension that allows the definition of an Aggregate. This component will automatically
 * setup all components required for the Aggregate to operate.
 *
 * @param <A> The type of Aggregate configured
 */
public class AggregateConfigurer<A> implements AggregateConfiguration<A> {

    private final Class<A> aggregate;

    private final Component<AggregateAnnotationCommandHandler> commandHandler;
    private final Component<Repository<A>> repository;
    private final Component<AggregateFactory<A>> aggregateFactory;
    private final Component<SnapshotTriggerDefinition> snapshotTriggerDefinition;
    private final Component<CommandTargetResolver> commandTargetResolver;
    private final Component<AggregateModel<A>> metaModel;
    private final List<Registration> registrations = new ArrayList<>();
    private Configuration parent;

    /**
     * Creates a default configuration as described in {@link #defaultConfiguration(Class)}. This constructor is
     * available for subclasses that provide additional configuration possibilities.
     *
     * @param aggregate The type of aggregate to configure
     */
    protected AggregateConfigurer(Class<A> aggregate) {
        this.aggregate = aggregate;

        metaModel = new Component<>(() -> parent,
                                    "aggregateMetaModel<" + aggregate.getSimpleName() + ">",
                                    c -> c.getComponent(
                                            AggregateMetaModelFactory.class,
                                            () -> new AnnotatedAggregateMetaModelFactory(c.parameterResolverFactory(),
                                                                                         c.handlerDefinition(aggregate))
                                    ).createModel(aggregate));
        commandTargetResolver = new Component<>(() -> parent, name("commandTargetResolver"),
                                                c -> c.getComponent(CommandTargetResolver.class,
                                                                    AnnotationCommandTargetResolver::new));
        snapshotTriggerDefinition = new Component<>(() -> parent, name("snapshotTriggerDefinition"),
                                                    c -> NoSnapshotTriggerDefinition.INSTANCE);
        aggregateFactory =
                new Component<>(() -> parent, name("aggregateFactory"), c -> new GenericAggregateFactory<>(aggregate));
        repository = new Component<>(
                () -> parent,
                "Repository<" + aggregate.getSimpleName() + ">",
                c -> {
                    state(c.eventBus() instanceof EventStore,
                          () -> "Default configuration requires the use of event sourcing. Either configure an Event " +
                                  "Store to use, or configure a specific repository implementation for " +
                                  aggregate.toString());

                    if (c.commandBus() instanceof DisruptorCommandBus) {
                        return ((DisruptorCommandBus) c.commandBus()).createRepository(c.eventStore(),
                                                                                       aggregateFactory.get(),
                                                                                       snapshotTriggerDefinition.get(),
                                                                                       c.parameterResolverFactory(),
                                                                                       c.handlerDefinition(aggregate),
                                                                                       c::repository);
                    }
                    return EventSourcingRepository.builder(aggregate)
                            .aggregateModel(metaModel.get())
                            .aggregateFactory(aggregateFactory.get())
                            .eventStore(c.eventStore())
                            .snapshotTriggerDefinition(snapshotTriggerDefinition.get())
                            .repositoryProvider(c::repository)
                            .build();
                });
        commandHandler = new Component<>(() -> parent, "aggregateCommandHandler<" + aggregate.getSimpleName() + ">",
                                         c -> AggregateAnnotationCommandHandler.<A>builder()
                                                 .repository(repository.get())
                                                 .commandTargetResolver(commandTargetResolver.get())
                                                 .aggregateModel(metaModel.get())
                                                 .build());
    }

    /**
     * Creates a default Configuration for an aggregate of the given {@code aggregateType}. This required either a
     * Repository to be configured using {@link #configureRepository(Function)}, or that the Global Configuration
     * contains an Event Store and the Aggregate support Event Sourcing.
     *
     * @param aggregateType The type of Aggregate to configure
     * @param <A>           The type of Aggregate to configure
     * @return An AggregateConfigurer instance for further configuration of the Aggregate
     */
    public static <A> AggregateConfigurer<A> defaultConfiguration(Class<A> aggregateType) {
        return new AggregateConfigurer<>(aggregateType);
    }

    /**
     * Creates a Configuration for an aggregate of given {@code aggregateType}, which is mapped to a relational
     * database using an EntityManager obtained from the main configuration. The given {@code aggregateType}
     * is expected to be a proper JPA Entity.
     * <p>
     * The EntityManagerProvider is expected to have been registered with the Configurer (which would be the case when
     * using {@link DefaultConfigurer#jpaConfiguration(EntityManagerProvider)}. If that is not the case, consider using
     * {@link #jpaMappedConfiguration(Class, EntityManagerProvider)} instead.
     *
     * @param aggregateType The type of Aggregate to configure
     * @param <A>           The type of Aggregate to configure
     * @return An AggregateConfigurer instance for further configuration of the Aggregate
     */
    public static <A> AggregateConfigurer<A> jpaMappedConfiguration(Class<A> aggregateType) {
        AggregateConfigurer<A> configurer = new AggregateConfigurer<>(aggregateType);
        return configurer.configureRepository(
                c -> {
                    EntityManagerProvider entityManagerProvider = c.getComponent(
                            EntityManagerProvider.class,
                            () -> {
                                throw new AxonConfigurationException(format(
                                        "JPA has not been correctly configured for aggregate [%s]. Either provide "
                                                + "an EntityManagerProvider, or use "
                                                + "DefaultConfigurer.jpaConfiguration(...) to define one for the "
                                                + "entire configuration.",
                                        aggregateType.getSimpleName()
                                ));
                            });
                    return GenericJpaRepository.builder(aggregateType)
                            .aggregateModel(configurer.metaModel.get())
                            .entityManagerProvider(entityManagerProvider)
                            .eventBus(c.eventBus())
                            .repositoryProvider(c::repository)
                            .build();
                });
    }

    /**
     * Creates a Configuration for an aggregate of given {@code aggregateType}, which is mapped to a relational
     * database using an EntityManager provided by given {@code entityManagerProvider}. The given {@code aggregateType}
     * is expected to be a proper JPA Entity.
     *
     * @param aggregateType         The type of Aggregate to configure
     * @param entityManagerProvider The provider for Axon to retrieve the EntityManager from
     * @param <A>                   The type of Aggregate to configure
     * @return An AggregateConfigurer instance for further configuration of the Aggregate
     */
    public static <A> AggregateConfigurer<A> jpaMappedConfiguration(Class<A> aggregateType,
                                                                    EntityManagerProvider entityManagerProvider) {
        AggregateConfigurer<A> configurer = new AggregateConfigurer<>(aggregateType);
        return configurer.configureRepository(
                c -> GenericJpaRepository.builder(aggregateType)
                        .aggregateModel(configurer.metaModel.get())
                        .entityManagerProvider(entityManagerProvider)
                        .eventBus(c.eventBus())
                        .repositoryProvider(c::repository)
                        .build()
        );
    }

    private String name(String prefix) {
        return prefix + "<" + aggregate.getSimpleName() + ">";
    }

    /**
     * Defines the repository to use to load and store Aggregates of this type. The builder function receives the
     * global configuration object from which it can retrieve components the repository depends on.
     *
     * @param repositoryBuilder The builder function for the repository
     * @return this configurer instance for chaining
     */
    public AggregateConfigurer<A> configureRepository(Function<Configuration, Repository<A>> repositoryBuilder) {
        repository.update(repositoryBuilder);
        return this;
    }

    /**
     * Defines the factory to use to to create new Aggregates instances of the type under configuration.
     *
     * @param aggregateFactoryBuilder The builder function for the AggregateFactory
     * @return this configurer instance for chaining
     */
    public AggregateConfigurer<A> configureAggregateFactory(
            Function<Configuration, AggregateFactory<A>> aggregateFactoryBuilder) {
        aggregateFactory.update(aggregateFactoryBuilder);
        return this;
    }

    /**
     * Defines the AggregateAnnotationCommandHandler instance to use.
     *
     * @param aggregateCommandHandlerBuilder The builder function for the AggregateCommandHandler
     * @return this configurer instance for chaining
     */
    public AggregateConfigurer<A> configureCommandHandler(
            Function<Configuration, AggregateAnnotationCommandHandler> aggregateCommandHandlerBuilder) {
        commandHandler.update(aggregateCommandHandlerBuilder);
        return this;
    }

    /**
     * Defines the CommandTargetResolver to use for the Aggregate type under configuration. The CommandTargetResolver
     * defines which Aggregate instance must be loaded to handle a specific Command.
     *
     * @param commandTargetResolverBuilder the builder function for the CommandTargetResolver.
     * @return this configurer instance for chaining
     */
    public AggregateConfigurer<A> configureCommandTargetResolver(
            Function<Configuration, CommandTargetResolver> commandTargetResolverBuilder) {
        commandTargetResolver.update(commandTargetResolverBuilder);
        return this;
    }

    /**
     * Configures snapshotting for the Aggregate type under configuration.
     * <p>
     * Note that this configuration is ignored if a custom repository instance is configured.
     *
     * @param snapshotTriggerDefinition The function creating the SnapshotTriggerDefinition
     * @return this configurer instance for chaining
     */
    public AggregateConfigurer<A> configureSnapshotTrigger(
            Function<Configuration, SnapshotTriggerDefinition> snapshotTriggerDefinition) {
        this.snapshotTriggerDefinition.update(snapshotTriggerDefinition);
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
