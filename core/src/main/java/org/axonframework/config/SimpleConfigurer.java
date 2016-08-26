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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventhandling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
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
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

public class SimpleConfigurer implements Configurer {

    private final Configuration config = new ConfigurationImpl();

    private Component<BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactory = new Component<>(config, "monitorFactory", (c) -> (type, name) -> NoOpMessageMonitor.instance());
    private Component<MessageHandlerInterceptor<Message<?>>> interceptor = new Component<>(config, "correlationInterceptor",
                                                                                           c -> new CorrelationDataInterceptor<>());
    private Component<EventBus> eventBus = new Component<>(config, "eventBus", c -> new SimpleEventBus());
    private Component<Serializer> serializer = new Component<>(config, "serializer", c -> new XStreamSerializer());
    private Component<TransactionManager> transactionManager = new Component<>(config, "transactionManager", c -> NoTransactionManager.INSTANCE);
    private Component<List<CorrelationDataProvider>> correlationProviders = new Component<>(config, "correlationProviders",
                                                                                            c -> asList(msg -> singletonMap("correlationId", msg.getIdentifier()),
                                                                                                        msg -> singletonMap("traceId", msg.getMetaData().getOrDefault("traceId", msg.getIdentifier()))
                                                                                            ));

    private Component<CommandBus> commandBus = new Component<>(config, "commandBus", c -> {
        SimpleCommandBus cb = new SimpleCommandBus(messageMonitorFactory.get().apply(SimpleCommandBus.class, "commandBus"));
        cb.setHandlerInterceptors(singletonList(interceptor.get()));
        DefaultUnitOfWorkFactory unitOfWorkFactory = new DefaultUnitOfWorkFactory(transactionManager.get());
        c.correlationDataProviders().forEach(unitOfWorkFactory::registerCorrelationDataProvider);
        cb.setUnitOfWorkFactory(unitOfWorkFactory);
        return cb;
    });

    private Component<ParameterResolverFactory> parameterResolverFactory = new Component<>(config, "parameterResolverFactory",
                                                                                           c -> ClasspathParameterResolverFactory.forClass(getClass()));
    private Component<SagaStore<?>> sagaStore = new Component<>(config, "sagaStore", c -> new InMemorySagaStore());

    private Map<Class<?>, AggregateConfiguration> aggregate = new HashMap<>();

    private Map<Object, MessageMonitor<?>> monitorsPerComponent = new ConcurrentHashMap<>();
    private List<Runnable> startHandlers = new ArrayList<>();

    public static Configurer defaultConfiguration() {
        return new SimpleConfigurer();
    }

    public static Configurer jpaConfiguration(EntityManagerProvider entityManagerProvider) {
        return new SimpleConfigurer()
                .withEmbeddedEventStore(c -> new JpaEventStorageEngine(entityManagerProvider, c.transactionManager()))
                .withSagaStore(c -> new JpaSagaStore(entityManagerProvider));
    }

    protected SimpleConfigurer() {
    }

    private Configurer withSagaStore(Function<Configuration, SagaStore<?>> sagaStoreBuilder) {
        sagaStore.update(sagaStoreBuilder);
        return this;
    }

    @Override
    public Configurer withMessageMonitor(Function<Configuration, BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactoryBuilder) {
        messageMonitorFactory.update(messageMonitorFactoryBuilder);
        return this;
    }

    @Override
    public Configurer withCorrelationDataProviders(Function<Configuration, List<CorrelationDataProvider>> correlationDataProviderBuilder) {
        correlationProviders.update(correlationDataProviderBuilder);
        return this;
    }

    @Override
    public Configurer registerModule(ModuleConfiguration module) {
        startHandlers.add(() -> module.initialize(config));
        startHandlers.add(module::start);
        return this;
    }

    @Override
    public SimpleConfigurer withEmbeddedEventStore(Function<Configuration, EventStorageEngine> storageEngineBuilder) {
        return withEventStore(c -> {
            MessageMonitor<Message<?>> monitor = messageMonitorFactory.get().apply(EmbeddedEventStore.class, "eventStore");
            EmbeddedEventStore eventStore = new EmbeddedEventStore(storageEngineBuilder.apply(c), monitor);
            monitorsPerComponent.put(eventStore, monitor);
            return eventStore;
        });
    }

    @Override
    public SimpleConfigurer withEventStore(Function<Configuration, EventStore> eventStoreBuilder) {
        eventBus.update(eventStoreBuilder);
        return this;
    }

    @Override
    public Configurer withEventBus(Function<Configuration, EventBus> eventBusBuilder) {
        eventBus.update(eventBusBuilder);
        return this;
    }

    @Override
    public Configurer withCommandBus(Function<Configuration, CommandBus> commandBusBuilder) {
        commandBus.update(commandBusBuilder);
        return this;
    }

    @Override
    public Configurer withSerializer(Function<Configuration, Serializer> serializerBuilder) {
        serializer.update(serializerBuilder);
        return this;
    }

    @Override
    public Configurer withTransactionManager(Function<Configuration, TransactionManager> transactionManagerBuilder) {
        transactionManager.update(transactionManagerBuilder);
        return this;
    }

    @Override
    public <A> Configurer registerAggregate(AggregateConfiguration<A> aggregateConfiguration) {
        this.aggregate.put(aggregateConfiguration.aggregateType(), aggregateConfiguration);
        this.startHandlers.add(() -> aggregateConfiguration.initialize(config));
        this.startHandlers.add(aggregateConfiguration::start);
        return this;
    }

    @Override
    public <A> Configurer registerAggregate(Class<A> aggregate) {
        return registerAggregate(AggregateConfigurer.defaultConfiguration(aggregate));
    }

    @Override
    public Configuration initialize() {
        startHandlers.forEach(Runnable::run);
        return config;
    }

    private class ConfigurationImpl implements Configuration {

        @Override
        public EventBus eventBus() {
            return eventBus.get();
        }

        @Override
        public CommandBus commandBus() {
            return commandBus.get();
        }

        @Override
        public <T> Repository<T> repository(Class<T> aggregate) {
            AggregateConfiguration<T> aggregateConfigurer = SimpleConfigurer.this.aggregate.get(aggregate);
            if (aggregateConfigurer == null) {
                throw new IllegalArgumentException("Aggregate " + aggregate.getSimpleName() + " has not been configured");
            }
            return aggregateConfigurer.repository();
        }

        @Override
        public TransactionManager transactionManager() {
            return transactionManager.get();
        }

        @Override
        public <T> SagaRepository<T> sagaRepository(Class<T> sagaType) {
            return null;
        }

        @Override
        public <T> SagaStore<? super T> sagaStore(Class<T> sagaType) {
            return null;
        }

        @Override
        public <M extends Message<?>> MessageMonitor<? super M> messageMonitor(Class<?> componentType, String componentName) {
            return messageMonitorFactory.get().apply(componentType, componentName);
        }

        public Serializer serializer() {
            return serializer.get();
        }

        @Override
        public void shutdown() {

        }

        @Override
        public List<CorrelationDataProvider> correlationDataProviders() {
            return correlationProviders.get();
        }

        @Override
        public ParameterResolverFactory parameterResolverFactory() {
            return parameterResolverFactory.get();
        }
    }
}
