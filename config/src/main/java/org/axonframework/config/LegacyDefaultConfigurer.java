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

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.tracing.CommandBusSpanFactory;
import org.axonframework.commandhandling.tracing.DefaultCommandBusSpanFactory;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineManagerSpanFactory;
import org.axonframework.deadline.DefaultDeadlineManagerSpanFactory;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.axonframework.eventhandling.DefaultEventBusSpanFactory;
import org.axonframework.eventhandling.DefaultEventProcessorSpanFactory;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventBusSpanFactory;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.gateway.DefaultEventGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.DefaultSnapshotterSpanFactory;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.SnapshotterSpanFactory;
import org.axonframework.eventsourcing.eventstore.LegacyEmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.LegacyEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.LegacyEventStore;
import org.axonframework.eventsourcing.eventstore.jpa.LegacyJpaEventStorageEngine;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.modelling.command.DefaultRepositorySpanFactory;
import org.axonframework.modelling.command.RepositorySpanFactory;
import org.axonframework.modelling.saga.DefaultSagaManagerSpanFactory;
import org.axonframework.modelling.saga.ResourceInjector;
import org.axonframework.modelling.saga.SagaManagerSpanFactory;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.DefaultQueryBusSpanFactory;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.DefaultQueryUpdateEmitterSpanFactory;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusSpanFactory;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.QueryUpdateEmitterSpanFactory;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertStrictPositive;
import static org.axonframework.util.HandlerTypeResolver.*;

/**
 * Entry point of the Axon Configuration API. It implements the Configurer interface, providing access to the methods to
 * configure the default Axon components.
 * <p>
 * Using {@link #defaultConfiguration()}, you will get a Configurer instance with default components configured. You
 * will need to register your Aggregates (using {@link #configureAggregate(AggregateConfiguration)} and provide a
 * repository implementation for each of them, or if you wish to use event sourcing, register your aggregates through
 * {@link #configureAggregate(Class)} and configure an Event Store ({@link #configureEventStore(Function)} or
 * {@link #configureEmbeddedEventStore(Function)}).
 * <p>
 * Use {@link #buildConfiguration()} to build the configuration, which provides access to the configured building
 * blocks, such as the {@link CommandBus} and {@link EventBus}.
 * <p>
 * Note that this Configurer implementation is not thread-safe.
 *
 * @author Allard Buijze
 * @since 3.0
 * @deprecated In favor of using the {@link org.axonframework.configuration.ApplicationConfigurer} with additional
 * modules.
 */
@Deprecated(since = "5.0.0", forRemoval = true)
public class LegacyDefaultConfigurer implements LegacyConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final Runnable NOTHING = () -> {
    };

    private final LegacyConfiguration config = new ConfigurationImpl();

    private final MessageMonitorFactoryBuilder messageMonitorFactoryBuilder = new MessageMonitorFactoryBuilder();
    private final Component<BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactoryComponent =
            new Component<>(config, "monitorFactory", messageMonitorFactoryBuilder::build);
    private final Component<List<CorrelationDataProvider>> correlationProviders = new Component<>(
            config, "correlationProviders",
            c -> Collections.singletonList(new MessageOriginProvider())
    );
    private final Map<Class<?>, Component<?>> components = new ConcurrentHashMap<>();
    private final List<Component<MessageHandlerRegistrar>> messageHandlerRegistrars = new ArrayList<>();
    private final Component<Serializer> eventSerializer =
            new Component<>(config, "eventSerializer", LegacyConfiguration::messageSerializer);
    private final Component<Serializer> messageSerializer =
            new Component<>(config, "messageSerializer", LegacyConfiguration::serializer);
    private final List<Component<EventUpcaster>> upcasters = new ArrayList<>();
    private final Component<EventUpcasterChain> upcasterChain = new Component<>(
            config, "eventUpcasterChain", this::defaultUpcasterChain
    );
    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    private final Component<Function<Class<?>, HandlerDefinition>> handlerDefinition = new Component<>(
            config, "handlerDefinition",
            c -> this::defaultHandlerDefinition
    );
    private final List<Component<HandlerEnhancerDefinition>> handlerEnhancerDefinitions = new ArrayList<>();

    private final List<Consumer<LegacyConfiguration>> initHandlers = new ArrayList<>();
    private final TreeMap<Integer, List<LifecycleHandler>> startHandlers = new TreeMap<>();
    private final TreeMap<Integer, List<LifecycleHandler>> shutdownHandlers = new TreeMap<>(Comparator.reverseOrder());
    private final List<ModuleConfiguration> modules = new ArrayList<>();
    private long lifecyclePhaseTimeout = 5;
    private TimeUnit lifecyclePhaseTimeunit = TimeUnit.SECONDS;

    private boolean initialized = false;
    private Integer currentLifecyclePhase = null;
    private LifecycleState lifecycleState = LifecycleState.DOWN;

    /**
     * Initialize the Configurer.
     */
    protected LegacyDefaultConfigurer() {
        components.put(ParameterResolverFactory.class,
                       new Component<>(config, "parameterResolverFactory", this::defaultParameterResolverFactory));
        components.put(Serializer.class, new Component<>(config, "serializer", this::defaultSerializer));
        components.put(CommandBus.class, new Component<>(config, "commandBus", this::defaultCommandBus));
        components.put(EventBus.class, new Component<>(config, "eventBus", this::defaultEventBus));
        components.put(LegacyEventStore.class, new Component<>(config, "eventStore", LegacyConfiguration::eventStore));
        components.put(CommandGateway.class, new Component<>(config, "commandGateway", this::defaultCommandGateway));
        components.put(QueryBus.class, new Component<>(config, "queryBus", this::defaultQueryBus));
        components.put(
                QueryUpdateEmitter.class, new Component<>(config, "queryUpdateEmitter", this::defaultQueryUpdateEmitter)
        );
        components.put(QueryGateway.class, new Component<>(config, "queryGateway", this::defaultQueryGateway));
        components.put(ResourceInjector.class,
                       new Component<>(config, "resourceInjector", this::defaultResourceInjector));
        components.put(ScopeAwareProvider.class,
                       new Component<>(config, "scopeAwareProvider", this::defaultScopeAwareProvider));
        components.put(DeadlineManager.class, new Component<>(config, "deadlineManager", this::defaultDeadlineManager));
        components.put(EventUpcaster.class, upcasterChain);
        components.put(EventGateway.class, new Component<>(config, "eventGateway", this::defaultEventGateway));
        components.put(Snapshotter.class, new Component<>(config, "snapshotter", this::defaultSnapshotter));
        components.put(SpanFactory.class, new Component<>(config, "spanFactory", this::defaultSpanFactory));
        components.put(SnapshotterSpanFactory.class,
                       new Component<>(config, "snapshotterSpanFactory", this::defaultSnapshotterSpanFactory));
        components.put(CommandBusSpanFactory.class,
                       new Component<>(config, "commandBusSpanFactory", this::defaultCommandBusSpanFactory));
        components.put(QueryBusSpanFactory.class,
                       new Component<>(config, "queryBusSpanFactory", this::defaultQueryBusSpanFactory));
        components.put(QueryUpdateEmitterSpanFactory.class,
                       new Component<>(config,
                                       "queryUpdateEmitterSpanFactory",
                                       this::defaultQueryUpdateEmitterSpanFactory));
        components.put(EventBusSpanFactory.class,
                       new Component<>(config, "eventBusSpanFactory", this::defaultEventBusSpanFactory));
        components.put(DeadlineManagerSpanFactory.class,
                       new Component<>(config, "deadlineManagerSpanFactory", this::defaultDeadlineManagerSpanFactory));
        components.put(SagaManagerSpanFactory.class,
                       new Component<>(config, "sagaManagerSpanFactory", this::defaultSagaManagerSpanFactory));
        components.put(RepositorySpanFactory.class,
                       new Component<>(config, "repositorySpanFactory", this::defaultRepositorySpanFactory));
        components.put(EventProcessorSpanFactory.class,
                       new Component<>(config, "eventProcessorSpanFactory", this::defaultEventProcessorSpanFactory));
        registerModule(new AxonIQConsoleModule());
    }

    /**
     * Returns a Configurer instance with default components configured, such as a {@link SimpleCommandBus} and
     * {@link SimpleEventBus}.
     *
     * @return Configurer instance for further configuration.
     */
    public static LegacyConfigurer defaultConfiguration() {
        return defaultConfiguration(true);
    }

    /**
     * Returns a Configurer instance with default components configured, such as a {@link SimpleCommandBus} and
     * {@link SimpleEventBus}, indicating whether to {@code autoLocateConfigurerModules}.
     * <p>
     * When {@code autoLocateConfigurerModules} is {@code true}, a ServiceLoader will be used to locate all declared
     * instances of type {@link ConfigurerModule}. Each of the discovered instances will be invoked, allowing it to set
     * default values for the configuration.
     *
     * @param autoLocateConfigurerModules flag indicating whether ConfigurerModules on the classpath should be
     *                                    automatically retrieved. Should be set to {@code false} when using an
     *                                    application container, such as Spring or CDI.
     * @return Configurer instance for further configuration.
     */
    public static LegacyConfigurer defaultConfiguration(boolean autoLocateConfigurerModules) {
        LegacyDefaultConfigurer configurer = new LegacyDefaultConfigurer();
        if (autoLocateConfigurerModules) {
            ServiceLoader<ConfigurerModule> configurerModuleLoader =
                    ServiceLoader.load(ConfigurerModule.class, configurer.getClass().getClassLoader());
            List<ConfigurerModule> configurerModules = new ArrayList<>();
            configurerModuleLoader.forEach(configurerModules::add);
            configurerModules.sort(Comparator.comparingInt(ConfigurerModule::order));
            configurerModules.forEach(cm -> cm.configureModule(configurer));
        }
        return configurer;
    }

    /**
     * Returns a Configurer instance which has JPA versions of building blocks configured, such as a JPA based Event
     * Store (see {@link LegacyJpaEventStorageEngine}), a {@link JpaTokenStore} and {@link JpaSagaStore}.
     * <br>
     * This method allows to provide a transaction manager for usage in JTA-managed entity manager.
     *
     * @param entityManagerProvider The instance that provides access to the JPA EntityManager.
     * @param transactionManager    TransactionManager to be used for accessing the entity manager.
     * @return A Configurer instance for further configuration.
     */
    public static LegacyConfigurer jpaConfiguration(EntityManagerProvider entityManagerProvider,
                                                    TransactionManager transactionManager) {
        return new LegacyDefaultConfigurer()
                .registerComponent(EntityManagerProvider.class, c -> entityManagerProvider)
                .registerComponent(TransactionManager.class, c -> transactionManager)
                .configureEmbeddedEventStore(
                        c -> LegacyJpaEventStorageEngine.builder()
                                                        .snapshotSerializer(c.serializer())
                                                        .upcasterChain(c.upcasterChain())
                                                        .persistenceExceptionResolver(
                                                                c.getComponent(PersistenceExceptionResolver.class)
                                                        )
                                                        .eventSerializer(c.eventSerializer())
                                                        .snapshotFilter(c.snapshotFilter())
                                                        .entityManagerProvider(c.getComponent(EntityManagerProvider.class))
                                                        .transactionManager(c.getComponent(TransactionManager.class))
                                                        .build()
                )
                .registerComponent(TokenStore.class,
                                   c -> JpaTokenStore.builder()
                                                     .entityManagerProvider(c.getComponent(EntityManagerProvider.class))
                                                     .serializer(c.serializer())
                                                     .build())
                .registerComponent(SagaStore.class,
                                   c -> JpaSagaStore.builder()
                                                    .entityManagerProvider(c.getComponent(EntityManagerProvider.class))
                                                    .serializer(c.serializer())
                                                    .build());
    }

    /**
     * Returns a Configurer instance which has JPA versions of building blocks configured, such as a JPA based Event
     * Store (see {@link LegacyJpaEventStorageEngine}), a {@link JpaTokenStore} and {@link JpaSagaStore}.
     * <br>
     * This configuration should be used with an entity manager running without JTA transaction. If you are using a
     * entity manager in JTA mode, please provide the corresponding {@link TransactionManager} in the
     * {@link LegacyDefaultConfigurer#jpaConfiguration(EntityManagerProvider, TransactionManager)} method.
     *
     * @param entityManagerProvider The instance that provides access to the JPA EntityManager.
     * @return A Configurer instance for further configuration.
     */
    public static LegacyConfigurer jpaConfiguration(EntityManagerProvider entityManagerProvider) {
        return jpaConfiguration(entityManagerProvider, NoTransactionManager.INSTANCE);
    }

    /**
     * Method returning a default component to use for given {@code type} for given {@code configuration}, or an empty
     * Optional if no default can be provided.
     *
     * @param type          The type of component to find a default for.
     * @param configuration The configuration the component is configured in.
     * @param <T>           The type of component.
     * @return An Optional containing a default component, or empty if none can be provided.
     */
    protected <T> Optional<T> defaultComponent(Class<T> type, LegacyConfiguration configuration) {
        return Optional.empty();
    }

    /**
     * Returns a {@link DefaultCommandGateway} that will use the configuration's {@link CommandBus} to dispatch
     * commands.
     *
     * @param config The configuration that supplies the command bus.
     * @return The default command gateway.
     */
    protected CommandGateway defaultCommandGateway(LegacyConfiguration config) {
        return defaultComponent(CommandGateway.class, config)
                .orElseGet(() -> new DefaultCommandGateway(config.commandBus(), messageTypeResolver));
    }

    /**
     * Returns a {@link DefaultQueryGateway} that will use the configuration's {@link QueryBus} to dispatch queries.
     *
     * @param config The configuration that supplies the query bus.
     * @return The default query gateway.
     */
    protected QueryGateway defaultQueryGateway(LegacyConfiguration config) {
        return defaultComponent(QueryGateway.class, config)
                .orElseGet(() -> DefaultQueryGateway.builder().queryBus(config.queryBus()).build());
    }

    /**
     * Provides the default QueryBus implementations. Subclasses may override this method to provide their own default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default QueryBus to use.
     */
    protected QueryBus defaultQueryBus(LegacyConfiguration config) {
        return defaultComponent(QueryBus.class, config)
                .orElseGet(() -> {
                    QueryBus queryBus = SimpleQueryBus.builder()
                                                      .messageMonitor(config.messageMonitor(SimpleQueryBus.class,
                                                                                            "queryBus"))
                                                      .transactionManager(config.getComponent(
                                                              TransactionManager.class, NoTransactionManager::instance
                                                      ))
                                                      .errorHandler(config.getComponent(
                                                              QueryInvocationErrorHandler.class,
                                                              () -> LoggingQueryInvocationErrorHandler.builder().build()
                                                      ))
                                                      .queryUpdateEmitter(config.getComponent(QueryUpdateEmitter.class))
                                                      .spanFactory(config.getComponent(QueryBusSpanFactory.class))
                                                      .build();
                    queryBus.registerHandlerInterceptor(new CorrelationDataInterceptor<>(config.correlationDataProviders()));
                    return queryBus;
                });
    }

    /**
     * Provides the default QueryUpdateEmitter implementation. Subclasses may override this method to provide their own
     * default.
     *
     * @param config The configuration based on which the component is initialized
     * @return The default QueryUpdateEmitter to use
     */
    protected QueryUpdateEmitter defaultQueryUpdateEmitter(LegacyConfiguration config) {
        return defaultComponent(QueryUpdateEmitter.class, config)
                .orElseGet(() -> {
                    MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor =
                            config.messageMonitor(QueryUpdateEmitter.class, "queryUpdateEmitter");
                    return SimpleQueryUpdateEmitter.builder()
                                                   .updateMessageMonitor(updateMessageMonitor)
                                                   .spanFactory(config.getComponent(QueryUpdateEmitterSpanFactory.class))
                                                   .build();
                });
    }

    /**
     * Provides the default ParameterResolverFactory. Subclasses may override this method to provide their own default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default ParameterResolverFactory to use.
     */
    protected ParameterResolverFactory defaultParameterResolverFactory(LegacyConfiguration config) {
        return defaultComponent(ParameterResolverFactory.class, config)
                .orElseGet(() -> ClasspathParameterResolverFactory.forClass(getClass()));
    }

    /**
     * Provides the default HandlerDefinition. Subclasses may override this method to provide their own default.
     *
     * @param inspectedClass The class being inspected for handlers
     * @return The default HandlerDefinition to use
     */
    protected HandlerDefinition defaultHandlerDefinition(Class<?> inspectedClass) {
        HandlerDefinition definition = defaultComponent(HandlerDefinition.class, config)
                .orElseGet(() -> ClasspathHandlerDefinition.forClass(inspectedClass));

        List<HandlerEnhancerDefinition> registeredEnhancerDefinitions =
                handlerEnhancerDefinitions.stream()
                                          .map(Component::get)
                                          .collect(toList());
        if (definition instanceof MultiHandlerDefinition) {
            registeredEnhancerDefinitions.add(((MultiHandlerDefinition) definition).getHandlerEnhancerDefinition());
        }

        return MultiHandlerDefinition.ordered(
                Collections.singletonList(definition),
                MultiHandlerEnhancerDefinition.ordered(registeredEnhancerDefinitions)
        );
    }

    /**
     * Provides the default CommandBus implementation. Subclasses may override this method to provide their own
     * default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default CommandBus to use.
     */
    protected CommandBus defaultCommandBus(LegacyConfiguration config) {
        return defaultComponent(CommandBus.class, config)
                .orElseGet(() -> {
                    TransactionManager txManager = config.getComponent(TransactionManager.class);
                    SimpleCommandBus commandBus = txManager != null
                            ? new SimpleCommandBus(txManager)
                            : new SimpleCommandBus();
                    if (!config.correlationDataProviders().isEmpty()) {
                        CorrelationDataInterceptor<Message<?>> interceptor =
                                new CorrelationDataInterceptor<>(config.correlationDataProviders());
                        return new InterceptingCommandBus(commandBus, List.of(interceptor), List.of());
                    }
                    return commandBus;
                });
    }

    /**
     * Returns a {@link ConfigurationResourceInjector} that injects resources defined in the given
     * {@code config Configuration}.
     *
     * @param config The configuration that supplies registered components.
     * @return A resource injector that supplies components registered with the configuration.
     */
    protected ResourceInjector defaultResourceInjector(LegacyConfiguration config) {
        return defaultComponent(ResourceInjector.class, config)
                .orElseGet(() -> new ConfigurationResourceInjector(config));
    }

    /**
     * Returns a {@link ScopeAwareProvider} that provides {@link org.axonframework.messaging.ScopeAware} instances to be
     * used by a {@link DeadlineManager}. Uses the given {@code config} to construct the default
     * {@link ConfigurationScopeAwareProvider}.
     *
     * @param config the configuration used to construct the default {@link ConfigurationScopeAwareProvider}
     * @return a {@link ScopeAwareProvider} that provides {@link org.axonframework.messaging.ScopeAware} instances to be
     * used by a {@link DeadlineManager}
     */
    protected ScopeAwareProvider defaultScopeAwareProvider(LegacyConfiguration config) {
        return defaultComponent(ScopeAwareProvider.class, config)
                .orElseGet(() -> new ConfigurationScopeAwareProvider(config));
    }

    /**
     * Provides the default {@link DeadlineManager} implementation. Subclasses may override this method to provide their
     * own default.
     *
     * @param config The configuration that supplies registered components.
     * @return The default DeadlineManager to use
     */
    protected DeadlineManager defaultDeadlineManager(LegacyConfiguration config) {
        return defaultComponent(DeadlineManager.class, config)
                .orElseGet(() -> SimpleDeadlineManager.builder()
                                                      .scopeAwareProvider(config.scopeAwareProvider())
                                                      .spanFactory(config.getComponent(DeadlineManagerSpanFactory.class))
                                                      .build());
    }

    /**
     * Provides the default EventBus implementation. Subclasses may override this method to provide their own default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default EventBus to use.
     */
    protected EventBus defaultEventBus(LegacyConfiguration config) {
        return defaultComponent(EventBus.class, config)
                .orElseGet(() -> SimpleEventBus.builder()
                                               .messageMonitor(config.messageMonitor(EventBus.class, "eventBus"))
                                               .spanFactory(config.getComponent(EventBusSpanFactory.class))
                                               .build());
    }

    /**
     * Returns a {@link DefaultEventGateway} that will use the configuration's {@link EventBus} to publish events.
     *
     * @param config The configuration that supplies the event bus.
     * @return The default event gateway.
     */
    protected EventGateway defaultEventGateway(LegacyConfiguration config) {
        return defaultComponent(EventGateway.class, config)
                .orElseGet(() -> new DefaultEventGateway(
                        config.getComponent(EventSink.class),
                        config.getComponent(MessageTypeResolver.class)
                ));
    }

    /**
     * Returns the default {@link SpanFactory}, or a {@link NoOpSpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link SpanFactory}.
     */
    protected SpanFactory defaultSpanFactory(LegacyConfiguration config) {
        return defaultComponent(SpanFactory.class, config)
                .orElseGet(NoOpSpanFactory::new);
    }

    /**
     * Returns the default {@link SnapshotterSpanFactory}, or a {@link DefaultSnapshotterSpanFactory} backed by the
     * configured {@link SpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link SnapshotterSpanFactory}.
     */
    protected SnapshotterSpanFactory defaultSnapshotterSpanFactory(LegacyConfiguration config) {
        return defaultComponent(SnapshotterSpanFactory.class, this.config)
                .orElseGet(() -> DefaultSnapshotterSpanFactory
                        .builder()
                        .spanFactory(config.spanFactory())
                        .build());
    }

    /**
     * Returns the default {@link CommandBusSpanFactory}, or a {@link DefaultCommandBusSpanFactory} backed by the
     * configured {@link SpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link CommandBusSpanFactory}.
     */
    protected CommandBusSpanFactory defaultCommandBusSpanFactory(LegacyConfiguration config) {
        return defaultComponent(CommandBusSpanFactory.class, this.config)
                .orElseGet(() -> DefaultCommandBusSpanFactory
                        .builder()
                        .spanFactory(config.spanFactory())
                        .build());
    }

    /**
     * Returns the default {@link QueryBusSpanFactory}, or a {@link DefaultQueryBusSpanFactory} backed by the configured
     * {@link SpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link QueryBusSpanFactory}.
     */
    protected QueryBusSpanFactory defaultQueryBusSpanFactory(LegacyConfiguration config) {
        return defaultComponent(QueryBusSpanFactory.class, this.config)
                .orElseGet(() -> DefaultQueryBusSpanFactory
                        .builder()
                        .spanFactory(config.spanFactory())
                        .build());
    }

    /**
     * Returns the default {@link QueryUpdateEmitterSpanFactory}, or a {@link DefaultQueryUpdateEmitterSpanFactory}
     * backed by the configured {@link SpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link QueryUpdateEmitterSpanFactory}.
     */
    protected QueryUpdateEmitterSpanFactory defaultQueryUpdateEmitterSpanFactory(LegacyConfiguration config) {
        return defaultComponent(QueryUpdateEmitterSpanFactory.class, this.config)
                .orElseGet(() -> DefaultQueryUpdateEmitterSpanFactory
                        .builder()
                        .spanFactory(config.spanFactory())
                        .build());
    }

    /**
     * Returns the default {@link EventBusSpanFactory}, or a {@link DefaultEventBusSpanFactory} backed by the configured
     * {@link SpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link EventBusSpanFactory}.
     */
    protected EventBusSpanFactory defaultEventBusSpanFactory(LegacyConfiguration config) {
        return defaultComponent(EventBusSpanFactory.class, this.config)
                .orElseGet(() -> DefaultEventBusSpanFactory
                        .builder()
                        .spanFactory(config.spanFactory())
                        .build());
    }

    /**
     * Returns the default {@link DeadlineManagerSpanFactory}, or a {@link DefaultDeadlineManagerSpanFactory} backed by
     * the configured {@link SpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link DeadlineManagerSpanFactory}.
     */
    protected DeadlineManagerSpanFactory defaultDeadlineManagerSpanFactory(LegacyConfiguration config) {
        return defaultComponent(DeadlineManagerSpanFactory.class, this.config)
                .orElseGet(() -> DefaultDeadlineManagerSpanFactory
                        .builder()
                        .spanFactory(config.spanFactory())
                        .build());
    }

    /**
     * Returns the default {@link RepositorySpanFactory}, or a {@link DefaultRepositorySpanFactory} backed by the
     * configured {@link SpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link RepositorySpanFactory}.
     */
    protected RepositorySpanFactory defaultRepositorySpanFactory(LegacyConfiguration config) {
        return defaultComponent(RepositorySpanFactory.class, this.config)
                .orElseGet(() -> DefaultRepositorySpanFactory
                        .builder()
                        .spanFactory(config.spanFactory())
                        .build());
    }

    /**
     * Returns the default {@link EventProcessorSpanFactory}, or a {@link DefaultEventProcessorSpanFactory} backed by
     * the configured {@link SpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link EventProcessorSpanFactory}.
     */
    protected EventProcessorSpanFactory defaultEventProcessorSpanFactory(LegacyConfiguration config) {
        return defaultComponent(EventProcessorSpanFactory.class, this.config)
                .orElseGet(() -> DefaultEventProcessorSpanFactory
                        .builder()
                        .spanFactory(config.spanFactory())
                        .build());
    }

    /**
     * Returns the default {@link SagaManagerSpanFactory}, or a {@link DefaultSagaManagerSpanFactory} backed by the
     * configured {@link SpanFactory} if none it set.
     *
     * @param config The configuration that supplies the span factory.
     * @return The default {@link SagaManagerSpanFactory}.
     */
    protected SagaManagerSpanFactory defaultSagaManagerSpanFactory(LegacyConfiguration config) {
        return defaultComponent(SagaManagerSpanFactory.class, this.config)
                .orElseGet(() -> DefaultSagaManagerSpanFactory
                        .builder()
                        .spanFactory(config.spanFactory())
                        .build());
    }

    /**
     * Provides the default Serializer implementation. Subclasses may override this method to provide their own
     * default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default Serializer to use.
     */
    protected Serializer defaultSerializer(LegacyConfiguration config) {
        return defaultComponent(Serializer.class, config)
                .orElseGet(() -> XStreamSerializer.builder()
                                                  .revisionResolver(config.getComponent(RevisionResolver.class,
                                                                                        AnnotationRevisionResolver::new))
                                                  .build());
    }

    /**
     * Provides the default {@link EventUpcasterChain} implementation, looping through all
     * {@link #registerEventUpcaster(Function) registered} {@link EventUpcaster EventUpcasters} to collect them for a
     * fresh {@code EventUpcasterChain}. Subclasses may override this method to provide their own default.
     *
     * @param config The configuration based on which the component is initialized.
     * @return The default EventUpcasterChain to use.
     */
    protected EventUpcasterChain defaultUpcasterChain(LegacyConfiguration config) {
        return new EventUpcasterChain(upcasters.stream().map(Component::get).collect(toList()));
    }

    /**
     * Provides the default {@link Snapshotter} implementation, defaulting to a {@link AggregateSnapshotter}. Subclasses
     * may override this method to provide their own default.
     *
     * @param config the configuration based on which the {@link Snapshotter} will be initialized
     * @return the default {@link Snapshotter}
     */
    protected Snapshotter defaultSnapshotter(LegacyConfiguration config) {
        return defaultComponent(Snapshotter.class, config)
                .orElseGet(() -> {
                    List<AggregateConfiguration<?>> aggregateConfigurations =
                            config.findModules(AggregateConfiguration.class)
                                  .stream()
                                  .map(aggregateConfiguration -> (AggregateConfiguration<?>) aggregateConfiguration)
                                  .collect(Collectors.toList());
                    if (aggregateConfigurations.isEmpty()) {
                        // No configurations, so we return a no-op snapshotter, or retrieveHandlerDefinition will throw
                        // an exception.
                        return (aggregateType, aggregateIdentifier) -> {
                            // No-op Snapshotter.
                        };
                    }
                    List<AggregateFactory<?>> aggregateFactories = new ArrayList<>();
                    for (AggregateConfiguration<?> aggregateConfiguration : aggregateConfigurations) {
                        aggregateFactories.add(aggregateConfiguration.aggregateFactory());
                    }
                    return AggregateSnapshotter.builder()
                                               .eventStore(config.eventStore())
                                               .transactionManager(config.getComponent(TransactionManager.class))
                                               .aggregateFactories(aggregateFactories)
                                               .repositoryProvider(config::repository)
                                               .parameterResolverFactory(config.parameterResolverFactory())
                                               .spanFactory(config.getComponent(SnapshotterSpanFactory.class))
                                               .handlerDefinition(retrieveHandlerDefinition(config,
                                                                                            aggregateConfigurations))
                                               .build();
                });
    }

    /**
     * The class is required to be provided in case the
     * {@code ClasspathHandlerDefinition is used to retrieve the {@link HandlerDefinition}. Ideally, a {@code
     * HandlerDefinition} would be retrieved per aggregate class, as potentially users would be able to define different
     * {@link ClassLoader} instances per aggregate. For now we have deduced the latter to be to much of an edge case.
     * Hence we assume users will use the same ClassLoader for differing aggregates within a single configuration.
     */
    private HandlerDefinition retrieveHandlerDefinition(LegacyConfiguration configuration,
                                                        List<AggregateConfiguration<?>> aggregateConfigurations) {
        return configuration.handlerDefinition(aggregateConfigurations.get(0).aggregateType());
    }

    @Override
    public EventProcessingConfigurer eventProcessing() {
        List<EventProcessingConfigurer> eventProcessingConfigurers =
                modules.stream()
                       .filter(module -> module.isType(EventProcessingConfigurer.class))
                       .map(module -> (EventProcessingConfigurer) module.unwrap()) // It's safe to unwrap it since it isn't dependent on anything else.
                       .collect(toList());
        switch (eventProcessingConfigurers.size()) {
            case 0:
                EventProcessingModule eventProcessingModule = new EventProcessingModule();
                registerModule(eventProcessingModule);
                return eventProcessingModule;
            case 1:
                return eventProcessingConfigurers.get(0);
            default:
                throw new AxonConfigurationException(
                        "There are several EventProcessingConfigurers defined. "
                                + "The `eventProcessing()` method is used to retrieve a 'singleton' EventProcessingConfigurer."
                );
        }
    }

    @Override
    public LegacyConfigurer registerEventUpcaster(
            @Nonnull Function<LegacyConfiguration, EventUpcaster> upcasterBuilder
    ) {
        upcasters.add(new Component<>(config, "upcaster", upcasterBuilder));
        return this;
    }

    @Override
    public LegacyConfigurer configureMessageMonitor(
            @Nonnull Function<LegacyConfiguration, BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> builder
    ) {
        messageMonitorFactoryBuilder.add((conf, type, name) -> builder.apply(conf).apply(type, name));
        return this;
    }

    @Override
    public LegacyConfigurer configureMessageMonitor(@Nonnull Class<?> componentType,
                                                    @Nonnull MessageMonitorFactory messageMonitorFactory) {
        messageMonitorFactoryBuilder.add(componentType, messageMonitorFactory);
        return this;
    }

    @Override
    public LegacyConfigurer configureMessageMonitor(@Nonnull Class<?> componentType,
                                                    @Nonnull String componentName,
                                                    @Nonnull MessageMonitorFactory messageMonitorFactory) {
        messageMonitorFactoryBuilder.add(componentType, componentName, messageMonitorFactory);
        return this;
    }

    @Override
    public LegacyConfigurer configureCorrelationDataProviders(
            @Nonnull Function<LegacyConfiguration, List<CorrelationDataProvider>> correlationDataProviderBuilder
    ) {
        correlationProviders.update(correlationDataProviderBuilder);
        return this;
    }

    @Override
    public LegacyConfigurer registerModule(@Nonnull ModuleConfiguration module) {
        logger.debug("Registering module [{}]", module.getClass().getSimpleName());
        if (initialized) {
            module.initialize(config);
        }
        this.modules.add(module);
        return this;
    }

    @Override
    public <C> LegacyConfigurer registerComponent(@Nonnull Class<C> componentType,
                                                  @Nonnull Function<LegacyConfiguration, ? extends C> componentBuilder) {
        logger.debug("Registering component [{}]", componentType.getSimpleName());
        components.put(componentType, new Component<>(config, componentType.getSimpleName(), componentBuilder));
        return this;
    }

    @Override
    public LegacyConfigurer registerCommandHandler(
            @Nonnull Function<LegacyConfiguration, Object> commandHandlerBuilder
    ) {
        messageHandlerRegistrars.add(new Component<>(
                () -> config,
                "CommandHandlerRegistrar",
                configuration -> new MessageHandlerRegistrar(
                        () -> configuration,
                        commandHandlerBuilder,
                        (config, commandHandler) -> {
                            config.commandBus()
                                  .subscribe(new AnnotatedCommandHandlingComponent<>(
                                          commandHandler,
                                          config.parameterResolverFactory(),
                                          config.handlerDefinition(commandHandler.getClass()),
                                          messageTypeResolver
                                  ));
                            // TODO AnnotationCommandHandlerAdapter#subscribe does not use a Registration anymore
                            // If we support automated unsubscribe, we need to figure out another way.
                            // Enforced to a no-op Registration object for now.
                            return () -> true;
                        }
                )
        ));
        return this;
    }

    @Override
    public LegacyConfigurer registerQueryHandler(@Nonnull Function<LegacyConfiguration, Object> queryHandlerBuilder) {
        messageHandlerRegistrars.add(new Component<>(
                () -> config,
                "QueryHandlerRegistrar",
                configuration -> new MessageHandlerRegistrar(
                        () -> configuration,
                        queryHandlerBuilder,
                        (config, queryHandler) -> new AnnotationQueryHandlerAdapter<>(
                                queryHandler,
                                config.parameterResolverFactory(),
                                config.handlerDefinition(queryHandler.getClass())
                        ).subscribe(config.queryBus())
                )
        ));
        return this;
    }

    @Override
    public LegacyConfigurer registerMessageHandler(
            @Nonnull Function<LegacyConfiguration, Object> messageHandlerBuilder
    ) {
        Component<Object> messageHandler = new Component<>(() -> config, "", messageHandlerBuilder);
        Class<?> handlerClass = messageHandler.get().getClass();
        if (isCommandHandler(handlerClass)) {
            registerCommandHandler(c -> messageHandler.get());
        }
        if (isEventHandler(handlerClass)) {
            eventProcessing().registerEventHandler(c -> messageHandler.get());
        }
        if (isQueryHandler(handlerClass)) {
            registerQueryHandler(c -> messageHandler.get());
        }
        return this;
    }

    @Override
    public LegacyConfigurer configureEmbeddedEventStore(
            @Nonnull Function<LegacyConfiguration, LegacyEventStorageEngine> storageEngineBuilder
    ) {
        return configureEventStore(c -> {
            MessageMonitor<Message<?>> monitor =
                    messageMonitorFactoryComponent.get()
                                                  .apply(LegacyEmbeddedEventStore.class, "eventStore");
            LegacyEmbeddedEventStore eventStore = LegacyEmbeddedEventStore.builder()
                                                                          .storageEngine(storageEngineBuilder.apply(c))
                                                                          .messageMonitor(monitor)
                                                                          .build();
            c.onShutdown(eventStore::shutDown);
            return eventStore;
        });
    }

    @Override
    public LegacyConfigurer configureEventSerializer(
            @Nonnull Function<LegacyConfiguration, Serializer> eventSerializerBuilder
    ) {
        eventSerializer.update(eventSerializerBuilder);
        return this;
    }

    @Override
    public LegacyConfigurer configureMessageSerializer(
            @Nonnull Function<LegacyConfiguration, Serializer> messageSerializerBuilder
    ) {
        messageSerializer.update(messageSerializerBuilder);
        return this;
    }

    @Override
    public <A> LegacyConfigurer configureAggregate(@Nonnull AggregateConfiguration<A> aggregateConfiguration) {
        return registerModule(aggregateConfiguration);
    }

    @Override
    public LegacyConfigurer registerHandlerDefinition(
            @Nonnull BiFunction<LegacyConfiguration, Class, HandlerDefinition> handlerDefinitionClass
    ) {
        this.handlerDefinition.update(c -> clazz -> handlerDefinitionClass.apply(c, clazz));
        return this;
    }

    @Override
    public LegacyConfigurer registerHandlerEnhancerDefinition(
            Function<LegacyConfiguration, HandlerEnhancerDefinition> handlerEnhancerBuilder
    ) {
        this.handlerEnhancerDefinitions.add(
                new Component<>(config, "HandlerEnhancerDefinition", handlerEnhancerBuilder)
        );
        return this;
    }

    @Override
    public LegacyConfigurer configureLifecyclePhaseTimeout(long timeout, TimeUnit timeUnit) {
        assertStrictPositive(timeout, "The lifecycle phase timeout should be strictly positive");
        assertNonNull(timeUnit, "The lifecycle phase time unit should not be null");
        this.lifecyclePhaseTimeout = timeout;
        this.lifecyclePhaseTimeunit = timeUnit;
        return this;
    }

    @Override
    public LegacyConfiguration buildConfiguration() {
        if (!initialized) {
            verifyIdentifierFactory();
            prepareModules();
            prepareMessageHandlerRegistrars();
            invokeInitHandlers();
        }
        return config;
    }

    /**
     * Prepare the registered modules for initialization. This ensures all lifecycle handlers are registered.
     */
    protected void prepareModules() {
        modules.forEach(module -> initHandlers.add(module::initialize));
    }

    /**
     * Verifies that a valid {@link IdentifierFactory} class has been configured.
     *
     * @throws IllegalArgumentException if the configured factory is not valid
     */
    private void verifyIdentifierFactory() {
        try {
            IdentifierFactory.getInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("The configured IdentifierFactory could not be instantiated.", e);
        }
    }

    /**
     * Prepare the registered message handlers {@link MessageHandlerRegistrar} for initialization. This ensures their
     * lifecycle handlers are registered.
     */
    protected void prepareMessageHandlerRegistrars() {
        messageHandlerRegistrars.forEach(registrar -> initHandlers.add(c -> registrar.get()));
    }

    /**
     * Calls all registered init handlers. Registration of init handlers after this invocation will result in an
     * immediate invocation of that handler.
     */
    protected void invokeInitHandlers() {
        initialized = true;
        initHandlers.forEach(h -> h.accept(config));
    }

    /**
     * Invokes all registered start handlers.
     */
    protected void invokeStartHandlers() {
        logger.debug("Initiating start up");
        lifecycleState = LifecycleState.STARTING_UP;

        invokeLifecycleHandlers(
                startHandlers,
                e -> {
                    logger.debug("Start up is being ended prematurely due to an exception");
                    String startFailure = String.format(
                            "One of the start handlers in phase [%d] failed with the following exception: ",
                            currentLifecyclePhase
                    );
                    logger.warn(startFailure, e);

                    invokeShutdownHandlers();
                    throw new LifecycleHandlerInvocationException(startFailure, e);
                }
        );

        lifecycleState = LifecycleState.UP;
        logger.debug("Finalized start sequence");
    }

    /**
     * Invokes all registered shutdown handlers.
     */
    protected void invokeShutdownHandlers() {
        logger.debug("Initiating shutdown");
        lifecycleState = LifecycleState.SHUTTING_DOWN;

        invokeLifecycleHandlers(
                shutdownHandlers,
                e -> logger.warn(
                        "One of the shutdown handlers in phase [{}] failed with the following exception: ",
                        currentLifecyclePhase, e
                )
        );

        lifecycleState = LifecycleState.DOWN;
        logger.debug("Finalized shutdown sequence");
    }

    private void invokeLifecycleHandlers(TreeMap<Integer, List<LifecycleHandler>> lifecycleHandlerMap,
                                         Consumer<Exception> exceptionHandler) {
        Map.Entry<Integer, List<LifecycleHandler>> phasedHandlers = lifecycleHandlerMap.firstEntry();
        if (phasedHandlers == null) {
            return;
        }

        do {
            currentLifecyclePhase = phasedHandlers.getKey();
            logger.debug("Entered {} handler lifecycle phase [{}]", lifecycleState.description, currentLifecyclePhase);

            List<LifecycleHandler> handlers = phasedHandlers.getValue();
            try {
                handlers.stream()
                        .map(LifecycleHandler::run)
                        .map(c -> c.thenRun(NOTHING))
                        .reduce(CompletableFuture::allOf)
                        .orElse(FutureUtils.emptyCompletedFuture())
                        .get(lifecyclePhaseTimeout, lifecyclePhaseTimeunit);
            } catch (CompletionException | ExecutionException e) {
                exceptionHandler.accept(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn(
                        "Completion interrupted during {} phase [{}]. Proceeding to following phase",
                        lifecycleState.description, currentLifecyclePhase);
            } catch (TimeoutException e) {
                final long lifecyclePhaseTimeoutInSeconds = TimeUnit.SECONDS.convert(lifecyclePhaseTimeout,
                                                                                     lifecyclePhaseTimeunit);
                logger.warn(
                        "Timed out during {} phase [{}] after {} second(s). Proceeding to following phase",
                        lifecycleState.description, currentLifecyclePhase, lifecyclePhaseTimeoutInSeconds);
            }
        } while ((phasedHandlers = lifecycleHandlerMap.higherEntry(currentLifecyclePhase)) != null);
        currentLifecyclePhase = null;
    }

    /**
     * Returns the current Configuration object being built by this Configurer, without initializing it. Note that
     * retrieving objects from this configuration may lead to premature initialization of certain components.
     *
     * @return The current Configuration object being built by this Configurer.
     */
    protected LegacyConfiguration getConfig() {
        return config;
    }

    /**
     * Returns a map of all registered components in this configuration. The key of the map is the registered component
     * type (typically an interface), the value is a Component instance that wraps the actual implementation. Note that
     * calling {@link Component#get()} may prematurely initialize a component.
     *
     * @return A map of all registered components in this configuration.
     */
    public Map<Class<?>, Component<?>> getComponents() {
        return components;
    }

    @Override
    public void onStart(int phase, LifecycleHandler startHandler) {
        onInitialize(cfg -> cfg.onStart(phase, startHandler));
    }

    @Override
    public void onShutdown(int phase, LifecycleHandler shutdownHandler) {
        onInitialize(cfg -> cfg.onShutdown(phase, shutdownHandler));
    }

    private enum LifecycleState {
        DOWN("down"),
        STARTING_UP("start"),
        UP("up"),
        SHUTTING_DOWN("shutdown");

        private final String description;

        LifecycleState(String description) {
            this.description = description;
        }
    }

    private class ConfigurationImpl implements LegacyConfiguration {

        @Override
        public <T> T getComponent(@Nonnull Class<T> componentType, @Nonnull Supplier<T> defaultImpl) {
            Object component = components.computeIfAbsent(
                    componentType,
                    type -> new Component<>(config,
                                            componentType.getSimpleName(),
                                            c -> defaultComponent(componentType, c).orElseGet(defaultImpl))
            ).get();
            return componentType.cast(component);
        }

        @Override
        public <M extends Message<?>> MessageMonitor<? super M> messageMonitor(@Nonnull Class<?> componentType,
                                                                               @Nonnull String componentName) {
            return messageMonitorFactoryComponent.get().apply(componentType, componentName);
        }

        @Override
        public Serializer eventSerializer() {
            return eventSerializer.get();
        }

        @Override
        public Serializer messageSerializer() {
            return messageSerializer.get();
        }

        @Override
        public void start() {
            invokeStartHandlers();
        }

        @Override
        public void shutdown() {
            invokeShutdownHandlers();
        }

        @Override
        public List<CorrelationDataProvider> correlationDataProviders() {
            return correlationProviders.get();
        }

        @Override
        public List<ModuleConfiguration> getModules() {
            return modules;
        }

        @Override
        public void onStart(int phase, LifecycleHandler startHandler) {
            if (isEarlierPhaseDuringStartUp(phase)) {
                logger.info(
                        "A start handler is being registered for phase [{}] whilst phase [{}] is in progress. "
                                + "Will run provided handler immediately instead.",
                        phase, currentLifecyclePhase
                );
                startHandler.run().join();
            }
            registerLifecycleHandler(startHandlers, phase, startHandler);
        }

        private boolean isEarlierPhaseDuringStartUp(int phase) {
            return lifecycleState == LifecycleState.STARTING_UP
                    && currentLifecyclePhase != null && phase <= currentLifecyclePhase;
        }

        @Override
        public void onShutdown(int phase, LifecycleHandler shutdownHandler) {
            if (isEarlierPhaseDuringShutdown(phase)) {
                logger.info(
                        "A shutdown handler is being registered for phase [{}] whilst phase [{}] is in progress. "
                                + "Will run provided handler immediately instead.",
                        phase, currentLifecyclePhase
                );
                shutdownHandler.run().join();
            }
            registerLifecycleHandler(shutdownHandlers, phase, shutdownHandler);
        }

        private boolean isEarlierPhaseDuringShutdown(int phase) {
            return lifecycleState == LifecycleState.SHUTTING_DOWN
                    && currentLifecyclePhase != null && phase >= currentLifecyclePhase;
        }

        private void registerLifecycleHandler(Map<Integer, List<LifecycleHandler>> lifecycleHandlers,
                                              int phase,
                                              LifecycleHandler lifecycleHandler) {
            lifecycleHandlers.compute(phase, (p, handlers) -> {
                if (handlers == null) {
                    handlers = new CopyOnWriteArrayList<>();
                }
                handlers.add(lifecycleHandler);
                return handlers;
            });
        }

        @Override
        public EventUpcasterChain upcasterChain() {
            return upcasterChain.get();
        }

        @Override
        public HandlerDefinition handlerDefinition(Class<?> inspectedType) {
            return handlerDefinition.get().apply(inspectedType);
        }
    }
}
