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
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.Registration;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

public class DefaultConfigurer implements Configurer {

    private final Configuration config = new ConfigurationImpl();

    private Component<BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactory = new Component<>(config, "monitorFactory", (c) -> (type, name) -> NoOpMessageMonitor.instance());
    private Component<MessageHandlerInterceptor<Message<?>>> interceptor = new Component<>(config, "correlationInterceptor",
                                                                                           c -> new CorrelationDataInterceptor<>());
    private Component<List<CorrelationDataProvider>> correlationProviders = new Component<>(config, "correlationProviders",
                                                                                            c -> asList(msg -> singletonMap("correlationId", msg.getIdentifier()),
                                                                                                        msg -> singletonMap("traceId", msg.getMetaData().getOrDefault("traceId", msg.getIdentifier()))
                                                                                            ));

    private Map<Class<?>, Component<?>> components = new HashMap<>();
    private Map<Class<?>, AggregateConfiguration> aggregateConfigurations = new HashMap<>();
    private Map<Object, MessageMonitor<?>> monitors = new ConcurrentHashMap<>();

    private List<Consumer<Configuration>> initHandlers = new ArrayList<>();
    private List<Runnable> startHandlers = new ArrayList<>();
    private List<Runnable> shutdownHandlers = new ArrayList<>();

    private boolean initialized = false;

    public static Configurer defaultConfiguration() {
        return new DefaultConfigurer();
    }

    public static Configurer jpaConfiguration(EntityManagerProvider entityManagerProvider) {
        return new DefaultConfigurer()
                .registerComponent(EntityManagerProvider.class, c -> entityManagerProvider)
                .configureEmbeddedEventStore(c -> new JpaEventStorageEngine(c.getComponent(EntityManagerProvider.class,
                                                                                           () -> entityManagerProvider),
                                                                            c.getComponent(TransactionManager.class,
                                                                                      () -> NoTransactionManager.INSTANCE)))
                .registerComponent(TokenStore.class, c -> new JpaTokenStore(c.getComponent(EntityManagerProvider.class,
                                                                                           () -> entityManagerProvider),
                                                                            c.serializer()))
                .registerComponent(SagaStore.class, c -> new JpaSagaStore(c.getComponent(EntityManagerProvider.class,
                                                                                         () -> entityManagerProvider)));
    }

    protected DefaultConfigurer() {
        components.put(ParameterResolverFactory.class,
                       new Component<>(config, "parameterResolverFactory", this::defaultParameterResolverFactory));
        components.put(Serializer.class, new Component<>(config, "serializer", this::defaultSerializer));
        components.put(CommandBus.class, new Component<>(config, "commandBus", this::defaultCommandBus));
        components.put(EventBus.class, new Component<>(config, "eventBus", this::defaultEventBus));
    }

    protected ParameterResolverFactory defaultParameterResolverFactory(Configuration configuration) {
        return ClasspathParameterResolverFactory.forClass(getClass());
    }

    protected CommandBus defaultCommandBus(Configuration configuration) {
        SimpleCommandBus cb = new SimpleCommandBus(messageMonitorFactory.get().apply(SimpleCommandBus.class, "commandBus"));
        cb.setHandlerInterceptors(singletonList(interceptor.get()));
        DefaultUnitOfWorkFactory unitOfWorkFactory = new DefaultUnitOfWorkFactory(configuration.getComponent(TransactionManager.class));
        configuration.correlationDataProviders().forEach(unitOfWorkFactory::registerCorrelationDataProvider);
        cb.setUnitOfWorkFactory(unitOfWorkFactory);
        return cb;
    }

    protected EventBus defaultEventBus(Configuration configuration) {
        return new SimpleEventBus(Integer.MAX_VALUE, configuration.messageMonitor(EventBus.class, "eventBus"));
    }

    protected Serializer defaultSerializer(Configuration configuration) {
        return new XStreamSerializer(configuration.getComponent(RevisionResolver.class, AnnotationRevisionResolver::new));
    }

    @Override
    public Configurer configureMessageMonitor(Function<Configuration, BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactoryBuilder) {
        messageMonitorFactory.update(messageMonitorFactoryBuilder);
        return this;
    }

    @Override
    public Configurer configureCorrelationDataProviders(Function<Configuration, List<CorrelationDataProvider>> correlationDataProviderBuilder) {
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
            Registration registration = new AnnotationCommandHandlerAdapter(annotatedCommandHandlerBuilder.apply(config), config.parameterResolverFactory()).subscribe(config.commandBus());
            shutdownHandlers.add(registration::cancel);
        });
        return this;
    }

    @Override
    public <C> Configurer registerComponent(Class<C> componentType, Function<Configuration, ? extends C> componentBuilder) {
        components.put(componentType, new Component<>(config, componentType.getSimpleName(), componentBuilder));
        return this;
    }

    @Override
    public Configurer configureEmbeddedEventStore(Function<Configuration, EventStorageEngine> storageEngineBuilder) {
        return configureEventStore(c -> {
            MessageMonitor<Message<?>> monitor = messageMonitorFactory.get().apply(EmbeddedEventStore.class, "eventStore");
            EmbeddedEventStore eventStore = new EmbeddedEventStore(storageEngineBuilder.apply(c), monitor);
            monitors.put(eventStore, monitor);
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
    public <A> Configurer configureAggregate(Class<A> aggregate) {
        return configureAggregate(AggregateConfigurer.defaultConfiguration(aggregate));
    }

    @Override
    public Configuration buildConfiguration() {
        if (!initialized) {
            invokeInitHandlers();
        }
        return config;
    }

    protected void invokeStartHandlers() {
        startHandlers.forEach(Runnable::run);
    }

    protected void invokeInitHandlers() {
        initialized = true;
        initHandlers.forEach(h -> h.accept(config));
    }

    protected void invokeShutdownHandlers() {
        shutdownHandlers.forEach(Runnable::run);
    }

    protected Configuration getConfig() {
        return config;
    }

    private class ConfigurationImpl implements Configuration {

        @Override
        @SuppressWarnings("unchecked")
        public <T> Repository<T> repository(Class<T> aggregate) {
            AggregateConfiguration<T> aggregateConfigurer = DefaultConfigurer.this.aggregateConfigurations.get(aggregate);
            if (aggregateConfigurer == null) {
                throw new IllegalArgumentException("Aggregate " + aggregate.getSimpleName() + " has not been configured");
            }
            return aggregateConfigurer.repository();
        }

        @Override
        public <T> T getComponent(Class<T> componentType, Supplier<T> defaultImpl) {
            return componentType.cast(
                    components.computeIfAbsent(componentType,
                                                    k -> new Component<>(config,
                                                                         componentType.getSimpleName(),
                                                                         c -> defaultImpl.get())).get());
        }

        @Override
        public <M extends Message<?>> MessageMonitor<? super M> messageMonitor(Class<?> componentType, String componentName) {
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

    }

    public Map<Class<?>, Component<?>> getComponents() {
        return components;
    }
}
