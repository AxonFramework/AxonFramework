/*
 * Copyright (c) 2010-2020. Axon Framework
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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.WeakReferenceCache;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.disruptor.commandhandling.DisruptorCommandBus;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.NoSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Distributed;
import org.axonframework.modelling.command.AggregateAnnotationCommandHandler;
import org.axonframework.modelling.command.AnnotationCommandTargetResolver;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.GenericJpaRepository;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.inspection.AggregateMetaModelFactory;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.String.format;
import static org.axonframework.common.Assert.state;

/**
 * Axon Configuration API extension that allows the definition of an Aggregate. This component will automatically setup
 * all components required for the Aggregate to operate.
 *
 * @param <A> The type of Aggregate configured
 * @author Allard Buijze
 * @since 3.0
 */
public class AggregateConfigurer<A> implements AggregateConfiguration<A> {

    private final Class<A> aggregate;

    private final Component<AggregateAnnotationCommandHandler<A>> commandHandler;
    private final Component<Repository<A>> repository;
    private final Component<Cache> cache;
    private final Component<AggregateFactory<A>> aggregateFactory;
    private final Component<SnapshotTriggerDefinition> snapshotTriggerDefinition;
    private final Component<SnapshotFilter> snapshotFilter;
    private final Component<CommandTargetResolver> commandTargetResolver;
    private final Component<AggregateModel<A>> metaModel;
    private final Component<Predicate<? super DomainEventMessage<?>>> eventStreamFilter;
    private final Component<Boolean> filterEventsByType;
    private final Set<Class<? extends A>> subtypes = new HashSet<>();
    private final List<Registration> registrations = new ArrayList<>();

    private Configuration parent;

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
     * Creates a Configuration for an aggregate of given {@code aggregateType}, which is mapped to a relational database
     * using an EntityManager obtained from the main configuration. The given {@code aggregateType} is expected to be a
     * proper JPA Entity.
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
     * Creates a Configuration for an aggregate of given {@code aggregateType}, which is mapped to a relational database
     * using an EntityManager provided by given {@code entityManagerProvider}. The given {@code aggregateType} is
     * expected to be a proper JPA Entity.
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

    /**
     * Creates a default configuration as described in {@link #defaultConfiguration(Class)}. This constructor is
     * available for subclasses that provide additional configuration possibilities.
     *
     * @param aggregate The type of aggregate to configure
     */
    protected AggregateConfigurer(Class<A> aggregate) {
        this.aggregate = aggregate;

        metaModel = new Component<>(() -> parent, name("aggregateMetaModel"),
                                    c -> c.getComponent(
                                            AggregateMetaModelFactory.class,
                                            () -> new AnnotatedAggregateMetaModelFactory(c.parameterResolverFactory(),
                                                                                         c.handlerDefinition(aggregate))
                                    ).createModel(aggregate, subtypes));
        commandTargetResolver = new Component<>(
                () -> parent, name("commandTargetResolver"),
                c -> c.getComponent(
                        CommandTargetResolver.class,
                        () -> AnnotationCommandTargetResolver.builder().build()
                )
        );
        snapshotTriggerDefinition = new Component<>(() -> parent, name("snapshotTriggerDefinition"),
                                                    c -> NoSnapshotTriggerDefinition.INSTANCE);
        snapshotFilter = new Component<>(() -> parent, name("snapshotFilter"), c -> SnapshotFilter.allowAll());
        aggregateFactory = new Component<>(() -> parent, name("aggregateFactory"),
                                           c -> new GenericAggregateFactory<>(metaModel.get()));
        cache = new Component<>(() -> parent, name("aggregateCache"), c -> null);
        eventStreamFilter = new Component<>(() -> parent, name("eventStreamFilter"), c -> null);
        filterEventsByType = new Component<>(() -> parent, name("filterByAggregateType"), c -> false);
        repository = new Component<>(
                () -> parent, name("Repository"),
                c -> {
                    state(c.eventBus() instanceof EventStore,
                          () -> "Default configuration requires the use of event sourcing. Either configure an Event " +
                                  "Store to use, or configure a specific repository implementation for " +
                                  aggregate.toString());

                    CommandBus commandBus = c.commandBus();
                    if (commandBus instanceof DisruptorCommandBus) {
                        return buildDisruptorRepository((DisruptorCommandBus) commandBus, c, aggregate);
                    } else if (commandBusHasDisruptorLocalSegment(commandBus)) {
                        //noinspection unchecked
                        Distributed<CommandBus> distributedCommandBus = (Distributed<CommandBus>) commandBus;
                        return buildDisruptorRepository(
                                (DisruptorCommandBus) distributedCommandBus.localSegment(), c, aggregate
                        );
                    }

                    EventSourcingRepository.Builder<A> builder =
                            EventSourcingRepository.builder(aggregate)
                                                   .aggregateModel(metaModel.get())
                                                   .aggregateFactory(aggregateFactory.get())
                                                   .eventStore(c.eventStore())
                                                   .snapshotTriggerDefinition(snapshotTriggerDefinition.get())
                                                   .cache(cache.get())
                                                   .repositoryProvider(c::repository);
                    if (eventStreamFilter.get() != null) {
                        builder = builder.eventStreamFilter(eventStreamFilter.get());
                    } else if (filterEventsByType.get()) {
                        builder = builder.filterByAggregateType();
                    }
                    return builder.build();
                });
        commandHandler = new Component<>(() -> parent, name("aggregateCommandHandler"),
                                         c -> AggregateAnnotationCommandHandler.<A>builder()
                                                 .repository(repository.get())
                                                 .commandTargetResolver(commandTargetResolver.get())
                                                 .aggregateModel(metaModel.get())
                                                 .build());
    }

    private boolean commandBusHasDisruptorLocalSegment(CommandBus commandBus) {
        //noinspection unchecked
        return commandBus instanceof Distributed
                && ((Distributed<CommandBus>) commandBus).localSegment() instanceof DisruptorCommandBus;
    }

    private Repository<A> buildDisruptorRepository(DisruptorCommandBus commandBus,
                                                   Configuration parentConfiguration,
                                                   Class<A> aggregate) {
        return commandBus.createRepository(parentConfiguration.eventStore(),
                                           aggregateFactory.get(),
                                           snapshotTriggerDefinition.get(),
                                           parentConfiguration.parameterResolverFactory(),
                                           parentConfiguration.handlerDefinition(aggregate),
                                           parentConfiguration::repository);
    }

    private String name(String prefix) {
        return prefix + "<" + aggregate.getSimpleName() + ">";
    }

    /**
     * Defines the repository to use to load and store Aggregates of this type. The builder function receives the global
     * configuration object from which it can retrieve components the repository depends on.
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
            Function<Configuration, AggregateAnnotationCommandHandler<A>> aggregateCommandHandlerBuilder) {
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

    /**
     * Configure a {@link SnapshotFilter} for the Aggregate type under configuration. Note that {@code SnapshotFilter}
     * instances will be combined and should return {@code true} if they handle a snapshot they wish to ignore.
     *
     * @param snapshotFilter the function creating the {@link SnapshotFilter}
     * @return this configurer instance for chaining
     */
    public AggregateConfigurer<A> configureSnapshotFilter(Function<Configuration, SnapshotFilter> snapshotFilter) {
        this.snapshotFilter.update(snapshotFilter);
        return this;
    }

    /**
     * Configures an event stream filter for the EventSourcingRepository for the Aggregate type under configuration. By
     * default, no filter is applied to the event stream.
     * <p>
     * Note that this configuration is ignored if a custom repository instance is configured.
     *
     * @param filterBuilder The function creating the filter.
     * @return this configurer instance for chaining
     * @see EventSourcingRepository.Builder#eventStreamFilter(Predicate)
     */
    public AggregateConfigurer<A> configureEventStreamFilter(
            Function<Configuration, Predicate<? super DomainEventMessage<?>>> filterBuilder) {
        this.eventStreamFilter.update(filterBuilder);
        return this;
    }

    /**
     * Configures the Cache to use for the repository created for this Aggregate type. Note that this setting is ignored
     * when explicitly configuring a Repository using {@link #configureRepository(Function)}.
     *
     * @param cache the function that defines the Cache to use
     * @return this configurer instance for chaining
     */
    public AggregateConfigurer<A> configureCache(Function<Configuration, Cache> cache) {
        this.cache.update(cache);
        return this;
    }

    /**
     * Configures a WeakReferenceCache to be used for the repository created for this Aggregate type. Note that this
     * setting is ignored when explicitly configuring a Repository using {@link #configureRepository(Function)}.
     *
     * @return this configurer instance for chaining
     */
    public AggregateConfigurer<A> configureWeakReferenceCache() {
        return configureCache(c -> new WeakReferenceCache());
    }

    /**
     * Configures a function that determines whether or not the EventSourcingRepository for the Aggregate type under
     * configuration should filter out events with non-matching types. This may be used to support installations where
     * multiple Aggregate types share overlapping Aggregate IDs.
     * <p>
     * Note that this configuration is ignored if a custom repository instance is configured, and that {@link
     * #configureEventStreamFilter(Function)} overrides this.
     *
     * @param filterConfigurationPredicate The function determining whether or not to filter events by Aggregate type.
     * @return this configurer instance for chaining
     * @see EventSourcingRepository.Builder#filterByAggregateType()
     */
    public AggregateConfigurer<A> configureFilterEventsByType(
            Function<Configuration, Boolean> filterConfigurationPredicate) {
        this.filterEventsByType.update(filterConfigurationPredicate);
        return this;
    }

    @Override
    public void initialize(Configuration config) {
        parent = config;
        parent.onStart(
                Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS,
                () -> registrations.add(commandHandler.get().subscribe(parent.commandBus()))
        );
        parent.onShutdown(
                Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS,
                () -> {
                    registrations.forEach(Registration::cancel);
                    registrations.clear();
                }
        );
    }

    @Override
    public Repository<A> repository() {
        return repository.get();
    }

    @Override
    public Class<A> aggregateType() {
        return aggregate;
    }

    @Override
    public AggregateFactory<A> aggregateFactory() {
        return aggregateFactory.get();
    }

    @Override
    public SnapshotFilter snapshotFilter() {
        return snapshotFilter.get();
    }

    /**
     * Registers subtypes of this aggregate to support aggregate polymorphism. Command Handlers defined on this subtypes
     * will be considered part of this aggregate's handlers.
     *
     * @param subtypes subtypes in this polymorphic hierarchy
     * @return this configurer for fluent interfacing
     */
    @SafeVarargs
    public final AggregateConfigurer<A> withSubtypes(Class<? extends A>... subtypes) {
        return withSubtypes(Arrays.asList(subtypes));
    }

    /**
     * Registers subtypes of this aggregate to support aggregate polymorphism. Command Handlers defined on this subtypes
     * will be considered part of this aggregate's handlers.
     *
     * @param subtypes subtypes in this polymorphic hierarchy
     * @return this configurer for fluent interfacing
     */
    public AggregateConfigurer<A> withSubtypes(Collection<Class<? extends A>> subtypes) {
        this.subtypes.addAll(subtypes);
        return this;
    }
}
