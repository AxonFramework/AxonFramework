/*
 * Copyright (c) 2010-2017. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Module Configuration implementation that defines a Saga. This component allows the configuration of the type of
 * Event Processor used, as well as where to store Saga instances.
 */
public class SagaConfiguration<S> implements ModuleConfiguration {

    private final Component<EventProcessor> processor;
    private final Component<AnnotatedSagaManager<S>> sagaManager;
    private final Component<SagaRepository<S>> sagaRepository;
    private final Component<SagaStore<? super S>> sagaStore;
    private final List<Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>>> handlerInterceptors = new ArrayList<>();
    private Configuration config;

    /**
     * Initialize a configuration for a Saga of given {@code sagaType}, using a Subscribing Event Processor to process
     * incoming Events.
     *
     * @param sagaType The type of Saga to handle events with
     * @param <S>      The type of Saga configured in this configuration
     * @return a SagaConfiguration instance, ready for further configuration
     */
    public static <S> SagaConfiguration<S> subscribingSagaManager(Class<S> sagaType) {
        return subscribingSagaManager(sagaType, Configuration::eventBus);
    }

    /**
     * Initialize a configuration for a Saga of given {@code sagaType}, using a Subscribing Event Processor to process
     * incoming Events from the message source provided by given {@code messageSourceBuilder}
     *
     * @param sagaType             The type of Saga to handle events with
     * @param messageSourceBuilder The function providing the message source based on the configuration
     * @param <S>                  The type of Saga configured in this configuration
     * @return a SagaConfiguration instance, ready for further configuration
     */
    public static <S> SagaConfiguration<S> subscribingSagaManager(
            Class<S> sagaType,
            Function<Configuration, SubscribableMessageSource<EventMessage<?>>> messageSourceBuilder) {
        return new SagaConfiguration<>(sagaType, messageSourceBuilder);
    }

    /**
     * Initialize a configuration for a Saga of given {@code sagaType}, using a Tracking Event Processor to process
     * incoming Events. Note that a Token Store should be configured in the global configuration, or the Saga Manager
     * will default to an in-memory token store, which is not recommended for production environments.
     *
     * @param sagaType The type of Saga to handle events with
     * @param <S>      The type of Saga configured in this configuration
     * @return a SagaConfiguration instance, ready for further configuration
     */
    public static <S> SagaConfiguration<S> trackingSagaManager(Class<S> sagaType) {
        return trackingSagaManager(sagaType, Configuration::eventBus);
    }

    /**
     * Initialize a configuration for a Saga of given {@code sagaType}, using a Tracking Event Processor to process
     * incoming Events from a Message Source provided by given {@code messageSourceBuilder}. Note that a Token Store
     * should be configured in the global configuration, or the Saga Manager will default to an in-memory token store,
     * which is not recommended for production environments.
     *
     * @param sagaType             The type of Saga to handle events with
     * @param messageSourceBuilder The function providing the message source based on the configuration
     * @param <S>                  The type of Saga configured in this configuration
     * @return a SagaConfiguration instance, ready for further configuration
     */
    public static <S> SagaConfiguration<S> trackingSagaManager(
            Class<S> sagaType,
            Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> messageSourceBuilder) {
        SagaConfiguration<S> configuration = new SagaConfiguration<>(sagaType, c -> null);
        configuration.processor.update(c -> {
            TrackingEventProcessor processor = new TrackingEventProcessor(
                    sagaType.getSimpleName() + "Processor",
                    configuration.sagaManager.get(),
                    messageSourceBuilder.apply(configuration.config),
                    c.getComponent(TokenStore.class, InMemoryTokenStore::new),
                    c.getComponent(TransactionManager.class, NoTransactionManager::instance));
            processor.registerInterceptor(new CorrelationDataInterceptor<>(c.correlationDataProviders()));
            return processor;
        });
        return configuration;
    }

    @SuppressWarnings("unchecked")
    private SagaConfiguration(Class<S> sagaType, Function<Configuration, SubscribableMessageSource<EventMessage<?>>> messageSourceBuilder) {
        String managerName = sagaType.getSimpleName() + "Manager";
        String processorName = sagaType.getSimpleName() + "Processor";
        String repositoryName = sagaType.getSimpleName() + "Repository";
        sagaStore = new Component<>(() -> config, "sagaStore", c -> c.getComponent(SagaStore.class, InMemorySagaStore::new));
        sagaRepository = new Component<>(() -> config, repositoryName,
                                         c -> new AnnotatedSagaRepository<>(sagaType, sagaStore.get(), c.resourceInjector(),
                                                                            c.parameterResolverFactory()));
        sagaManager = new Component<>(() -> config, managerName, c -> new AnnotatedSagaManager<>(sagaType, sagaRepository.get(),
                                                                                                 c.parameterResolverFactory()));
        processor = new Component<>(() -> config, processorName,
                                    c -> {
                                        SubscribingEventProcessor processor = new SubscribingEventProcessor(managerName, sagaManager.get(),
                                                                                                            messageSourceBuilder.apply(c));
                                        processor.registerInterceptor(new CorrelationDataInterceptor<>(c.correlationDataProviders()));
                                        return processor;
                                    });
    }

    /**
     * Configures the Saga Store to use to store Saga instances of this type. By default, Sagas are stored in the
     * Saga Store configured in the global Configuration. This method can be used to override the store for specific
     * Sagas.
     *
     * @param sagaStoreBuilder The builder that returnes a fully initialized Saga Store instance based on the global
     *                         Configuration
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> configureSagaStore(Function<Configuration, SagaStore<? super S>> sagaStoreBuilder) {
        sagaStore.update(sagaStoreBuilder);
        return this;
    }

    /**
     * Registers the handler interceptor provided by the given {@code handlerInterceptorBuilder} function with
     * the processor defined in this configuration.
     *
     * @param handlerInterceptorBuilder The function to create the interceptor based on the current configuration
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> registerHandlerInterceptor(Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> handlerInterceptorBuilder) {
        if (config != null) {
            processor.get().registerInterceptor(handlerInterceptorBuilder.apply(config));
        } else {
            handlerInterceptors.add(handlerInterceptorBuilder);
        }
        return this;
    }

    @Override
    public void initialize(Configuration config) {
        this.config = config;
        for (Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> handlerInterceptor : handlerInterceptors) {
            processor.get().registerInterceptor(handlerInterceptor.apply(config));
        }
    }

    @Override
    public void start() {
        processor.get().start();
    }

    /**
     * Returns the processor that processed events for the Saga in this Configuration.
     *
     * @return The EventProcessor defined in this Configuration
     * @throws IllegalStateException when this configuration hasn't been initialized yet
     */
    public EventProcessor getProcessor() {
        Assert.state(config != null, () -> "Configuration is not initialized yet");
        return processor.get();
    }

    /**
     * Returns the Saga Store used by the Saga defined in this Configuration. If none has been explicitly defined,
     * it will return the Saga Store of the main Configuration.
     *
     * @return The Saga Store defined in this Configuration
     * @throws IllegalStateException when this configuration hasn't been initialized yet
     */
    public SagaStore<? super S> getSagaStore() {
        Assert.state(config != null, () -> "Configuration is not initialized yet");
        return sagaStore.get();
    }

    /**
     * Returns the SagaRepository instance used to load Saga instances in this Configuration.
     *
     * @return the SagaRepository defined in this Configuration
     * @throws IllegalStateException when this configuration hasn't been initialized yet
     */
    public SagaRepository<S> getSagaRepository() {
        Assert.state(config != null, () -> "Configuration is not initialized yet");
        return sagaRepository.get();
    }

    /**
     * Returns the SagaManager responsible for managing the lifecycle and invocation of Saga instances of the type
     * defined in this Configuration
     *
     * @return The SagaManager defined in this configuration
     * @throws IllegalStateException when this configuration hasn't been initialized yet
     */
    public AnnotatedSagaManager<S> getSagaManager() {
        Assert.state(config != null, () -> "Configuration is not initialized yet");
        return sagaManager.get();
    }

    @Override
    public void shutdown() {
        processor.get().shutDown();
    }

}
