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

import org.axonframework.commandhandling.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.Registration;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.saga.ResourceInjector;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
 */
public class DefaultConfigurer implements Configurer {

    private final Configuration config = new ConfigurationImpl();

    private final Component<BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactory =
            new Component<>(config, "monitorFactory", (c) -> (type, name) -> NoOpMessageMonitor.instance());
    private final Component<List<CorrelationDataProvider>> correlationProviders =
            new Component<>(config, "correlationProviders",
                            c -> Collections.singletonList(new MessageOriginProvider()));

    private final Map<Class<?>, Component<?>> components = new HashMap<>();
    private final Map<Class<?>, AggregateConfiguration> aggregateConfigurations = new HashMap<>();

    private final List<Consumer<Configuration>> initHandlers = new ArrayList<>();
    private final List<Runnable> startHandlers = new ArrayList<>();
    private final List<Runnable> shutdownHandlers = new ArrayList<>();

    private boolean initialized = false;

    /**
     * Returns a Configurer instance with default components configured, such as a {@link SimpleCommandBus} and
     * {@link SimpleEventBus}.
     *
     * @return Configurer instance for further configuration
     */
    public static Configurer defaultConfiguration() {
        return new DefaultConfigurer();
    }

    /**
     * Returns a Configurer instance which has JPA versions of building blocks configured, such as a JPA based Event
     * Store (see {@link JpaEventStorageEngine}), a {@link JpaTokenStore} and {@link JpaSagaStore}.
     *
     * @param entityManagerProvider The instance that provides access to the JPA EntityManager
     * @return a Configurer instance for further configuration
     */
    public static Configurer jpaConfiguration(EntityManagerProvider entityManagerProvider) {
        return new DefaultConfigurer().registerComponent(EntityManagerProvider.class, c -> entityManagerProvider)
                .configureEmbeddedEventStore(c -> new JpaEventStorageEngine(
                        c.getComponent(EntityManagerProvider.class, () -> entityManagerProvider),
                        c.getComponent(TransactionManager.class, () -> NoTransactionManager.INSTANCE)))
                .registerComponent(TokenStore.class, c -> new JpaTokenStore(
                        c.getComponent(EntityManagerProvider.class, () -> entityManagerProvider), c.serializer()))
                .registerComponent(SagaStore.class, c -> new JpaSagaStore(
                        c.getComponent(EntityManagerProvider.class, () -> entityManagerProvider)));
    }

    /**
     * Initialize the Configurer
     */
    protected DefaultConfigurer() {
        components.put(ParameterResolverFactory.class,
                       new Component<>(config, "parameterResolverFactory", this::defaultParameterResolverFactory));
        components.put(Serializer.class, new Component<>(config, "serializer", this::defaultSerializer));
        components.put(CommandBus.class, new Component<>(config, "commandBus", this::defaultCommandBus));
        components.put(EventBus.class, new Component<>(config, "eventBus", this::defaultEventBus));
        components.put(CommandGateway.class, new Component<>(config, "resourceInjector", this::defaultCommandGateway));
        components.put(ResourceInjector.class,
                       new Component<>(config, "resourceInjector", this::defaultResourceInjector));
    }

    /**
     * Returns a {@link DefaultCommandGateway} that will use the configuration's {@link CommandBus} to dispatch
     * commands.
     *
     * @param config the configuration that supplies the command bus
     * @return the default command gateway
     */
    protected CommandGateway defaultCommandGateway(Configuration config) {
        return new DefaultCommandGateway(config.commandBus());
    }

    /**
     * Provides the default ParameterResolverFactory. Subclasses may override this method to provide their own default
     *
     * @param config The configuration based on which the component is initialized
     * @return the default ParameterResolverFactory to use
     */
    protected ParameterResolverFactory defaultParameterResolverFactory(Configuration config) {
        return MultiParameterResolverFactory.ordered(ClasspathParameterResolverFactory.forClass(getClass()),
                                                     new ConfigurationParameterResolverFactory(config));
    }

    /**
     * Provides the default CommandBus implementation. Subclasses may override this method to provide their own default
     *
     * @param config The configuration based on which the component is initialized
     * @return the default CommandBus to use
     */
    protected CommandBus defaultCommandBus(Configuration config) {
        SimpleCommandBus cb =
                new SimpleCommandBus(config.getComponent(TransactionManager.class, () -> NoTransactionManager.INSTANCE),
                                     config.messageMonitor(SimpleCommandBus.class, "commandBus"));
        cb.registerHandlerInterceptor(new CorrelationDataInterceptor<>(config.correlationDataProviders()));
        return cb;
    }

    /**
     * Returns a {@link ConfigurationResourceInjector} that injects resources defined in the given {@code config
     * Configuration}.
     *
     * @param config the configuration that supplies registered components
     * @return a resource injector that supplies components registered with the configuration
     */
    protected ResourceInjector defaultResourceInjector(Configuration config) {
        return new ConfigurationResourceInjector(config);
    }

    /**
     * Provides the default EventBus implementation. Subclasses may override this method to provide their own default
     *
     * @param config The configuration based on which the component is initialized
     * @return the default EventBus to use
     */
    protected EventBus defaultEventBus(Configuration config) {
        return new SimpleEventBus(Integer.MAX_VALUE, config.messageMonitor(EventBus.class, "eventBus"));
    }

    /**
     * Provides the default Serializer implementation. Subclasses may override this method to provide their own default
     *
     * @param config The configuration based on which the component is initialized
     * @return the default Serializer to use
     */
    protected Serializer defaultSerializer(Configuration config) {
        return new XStreamSerializer(config.getComponent(RevisionResolver.class, AnnotationRevisionResolver::new));
    }

    @Override
    public Configurer configureMessageMonitor(
            Function<Configuration, BiFunction<Class<?>, String, MessageMonitor<Message<?>>>>
                    messageMonitorFactoryBuilder) {
        messageMonitorFactory.update(messageMonitorFactoryBuilder);
        return this;
    }

    @Override
    public Configurer configureCorrelationDataProviders(
            Function<Configuration, List<CorrelationDataProvider>> correlationDataProviderBuilder) {
        correlationProviders.update(correlationDataProviderBuilder);
        return this;
    }

    @Override
    public Configurer registerModule(ModuleConfiguration module) {
        if (initialized) {
            module.initialize(config);
        } else {
            initHandlers.add(module::initialize);
        }
        startHandlers.add(module::start);
        shutdownHandlers.add(module::shutdown);
        return this;
    }

    @Override
    public Configurer registerCommandHandler(Function<Configuration, Object> annotatedCommandHandlerBuilder) {
        startHandlers.add(() -> {
            Registration registration =
                    new AnnotationCommandHandlerAdapter(annotatedCommandHandlerBuilder.apply(config),
                                                        config.parameterResolverFactory())
                            .subscribe(config.commandBus());
            shutdownHandlers.add(registration::cancel);
        });
        return this;
    }

    @Override
    public <C> Configurer registerComponent(Class<C> componentType,
                                            Function<Configuration, ? extends C> componentBuilder) {
        components.put(componentType, new Component<>(config, componentType.getSimpleName(), componentBuilder));
        return this;
    }

    @Override
    public Configurer configureEmbeddedEventStore(Function<Configuration, EventStorageEngine> storageEngineBuilder) {
        return configureEventStore(c -> {
            MessageMonitor<Message<?>> monitor =
                    messageMonitorFactory.get().apply(EmbeddedEventStore.class, "eventStore");
            EmbeddedEventStore eventStore = new EmbeddedEventStore(storageEngineBuilder.apply(c), monitor
            );
            c.onShutdown(eventStore::shutDown);
            return eventStore;
        });
    }

    @Override
    public <A> Configurer configureAggregate(AggregateConfiguration<A> aggregateConfiguration) {
        this.aggregateConfigurations.put(aggregateConfiguration.aggregateType(), aggregateConfiguration);
        this.initHandlers.add(aggregateConfiguration::initialize);
        this.startHandlers.add(aggregateConfiguration::start);
        this.shutdownHandlers.add(aggregateConfiguration::shutdown);
        return this;
    }

    @Override
    public Configuration buildConfiguration() {
        if (!initialized) {
            invokeInitHandlers();
        }
        return config;
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
     * Invokes all registered start handlers
     */
    protected void invokeStartHandlers() {
        startHandlers.forEach(Runnable::run);
    }

    /**
     * Invokes all registered shutdown handlers
     */
    protected void invokeShutdownHandlers() {
        shutdownHandlers.forEach(Runnable::run);
    }

    /**
     * Returns the current Configuration object being built by this Configurer, without initializing it. Note that
     * retrieving objects from this configuration may lead to premature initialization of certain components.
     *
     * @return the current Configuration object being built by this Configurer
     */
    protected Configuration getConfig() {
        return config;
    }

    /**
     * Returns a map of all registered components in this configuration. The key of the map is the registered component
     * type (typically an interface), the value is a Component instance that wraps the actual implementation. Note that
     * calling {@link Component#get()} may prematurely initialize a component.
     *
     * @return a map of all registered components in this configuration
     */
    public Map<Class<?>, Component<?>> getComponents() {
        return components;
    }

    private class ConfigurationImpl implements Configuration {

        @Override
        @SuppressWarnings("unchecked")
        public <T> Repository<T> repository(Class<T> aggregateType) {
            AggregateConfiguration<T> aggregateConfigurer =
                    DefaultConfigurer.this.aggregateConfigurations.get(aggregateType);
            if (aggregateConfigurer == null) {
                throw new IllegalArgumentException(
                        "Aggregate " + aggregateType.getSimpleName() + " has not been configured");
            }
            return aggregateConfigurer.repository();
        }

        @Override
        public <T> T getComponent(Class<T> componentType, Supplier<T> defaultImpl) {
            return componentType.cast(components.computeIfAbsent(componentType, k -> new Component<>(config,
                                                                                                     componentType
                                                                                                             .getSimpleName(),
                                                                                                     c -> defaultImpl
                                                                                                             .get()))
                                              .get());
        }

        @Override
        public <M extends Message<?>> MessageMonitor<? super M> messageMonitor(Class<?> componentType,
                                                                               String componentName) {
            return messageMonitorFactory.get().apply(componentType, componentName);
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
        public void onShutdown(Runnable shutdownHandler) {
            shutdownHandlers.add(shutdownHandler);
        }

        @Override
        public void onStart(Runnable startHandler) {
            startHandlers.add(startHandler);
        }
    }
}
