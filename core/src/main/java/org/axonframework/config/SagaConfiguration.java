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
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Module Configuration implementation that defines a Saga. This component allows the configuration of the type of
 * Event Processor used, as well as where to store Saga instances.
 */
public class SagaConfiguration<S> implements ModuleConfiguration {

    private final Component<EventProcessor> processor;
    private final Component<TrackingEventProcessorConfiguration> trackingEventProcessorConfiguration;
    private final Component<AnnotatedSagaManager<S>> sagaManager;
    private final Component<SagaRepository<S>> sagaRepository;
    private final Component<SagaStore<? super S>> sagaStore;
    private final Component<RollbackConfiguration> rollbackConfiguration;
    private final Component<ErrorHandler> errorHandler;
    private final Component<ListenerInvocationErrorHandler> listenerInvocationErrorHandler;
    private final Component<TokenStore> tokenStore;
    private final Component<TransactionManager> transactionManager;
    private final Component<MessageMonitor<? super EventMessage<?>>> messageMonitor;
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
        return subscribingSagaManager(sagaType, messageSourceBuilder, c -> DirectEventProcessingStrategy.INSTANCE);
    }

    /**
     * Initialize a configuration for a Saga of given {@code sagaType}, using a Subscribing Event Processor to process
     * incoming Events from the message source provided by given {@code messageSourceBuilder}.
     * <p>
     * This methods allows a custom {@link EventProcessingStrategy} to be provided, in case handlers shouldn't be
     * invoked in the thread that delivers the message.
     *
     * @param sagaType                The type of Saga to handle events with
     * @param messageSourceBuilder    The function providing the message source based on the configuration
     * @param eventProcessingStrategy The strategy to use to invoke the event handlers.
     * @param <S>                     The type of Saga configured in this configuration
     * @return a SagaConfiguration instance, ready for further configuration
     */
    public static <S> SagaConfiguration<S> subscribingSagaManager(
            Class<S> sagaType,
            Function<Configuration, SubscribableMessageSource<EventMessage<?>>> messageSourceBuilder,
            Function<Configuration, EventProcessingStrategy> eventProcessingStrategy) {
        return new SagaConfiguration<>(sagaType, SubscribingEventProcessor.class,
                                       messageSourceBuilder, eventProcessingStrategy);
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
        SagaConfiguration<S> sagaConfiguration = new SagaConfiguration<>(sagaType, TrackingEventProcessor.class,
                                                                         c -> null, c -> null);
        String processorName = sagaType.getSimpleName() + "Processor";
        sagaConfiguration.processor.update(configuration -> {
            TrackingEventProcessor processor = new TrackingEventProcessor(
                    processorName,
                    sagaConfiguration.sagaManager.get(),
                    messageSourceBuilder.apply(sagaConfiguration.config),
                    sagaConfiguration.tokenStore.get(),
                    sagaConfiguration.transactionManager.get(),
                    sagaConfiguration.messageMonitor.get(),
                    sagaConfiguration.rollbackConfiguration.get(),
                    sagaConfiguration.errorHandler.get(),
                    sagaConfiguration.trackingEventProcessorConfiguration.get());
            processor.registerInterceptor(new CorrelationDataInterceptor<>(configuration.correlationDataProviders()));
            return processor;
        });
        return sagaConfiguration;
    }

    @SuppressWarnings("unchecked")
    private SagaConfiguration(Class<S> sagaType, Class<? extends EventProcessor> eventProcessorType,
                              Function<Configuration, SubscribableMessageSource<EventMessage<?>>> messageSourceBuilder,
                              Function<Configuration, EventProcessingStrategy> processingStrategy) {
        String managerName = sagaType.getSimpleName() + "Manager";
        String processorName = sagaType.getSimpleName() + "Processor";
        String repositoryName = sagaType.getSimpleName() + "Repository";
        transactionManager = new Component<>(() -> config, "transactionManager",
                                             c -> c.getComponent(TransactionManager.class, NoTransactionManager::instance));
        messageMonitor = new Component<>(() -> config, "messageMonitor",
                                         c -> c.messageMonitor(eventProcessorType, processorName));
        tokenStore = new Component<>(() -> config, "messageMonitor",
                                     c -> c.getComponent(TokenStore.class, InMemoryTokenStore::new));
        errorHandler = new Component<>(() -> config, "errorHandler",
                                       c -> c.getComponent(ErrorHandler.class,
                                                           () -> PropagatingErrorHandler.INSTANCE));
        listenerInvocationErrorHandler = new Component<>(() -> config, "listenerInvocationErrorHandler",
                                                     c -> c.getComponent(ListenerInvocationErrorHandler.class, LoggingErrorHandler::new));
        rollbackConfiguration = new Component<>(() -> config, "rollbackConfiguration",
                                                c -> c.getComponent(RollbackConfiguration.class,
                                                                    () -> RollbackConfigurationType.ANY_THROWABLE));
        sagaStore = new Component<>(() -> config, "sagaStore", c -> c.getComponent(SagaStore.class, InMemorySagaStore::new));
        sagaRepository = new Component<>(() -> config, repositoryName,
                                         c -> new AnnotatedSagaRepository<>(sagaType, sagaStore.get(), c.resourceInjector(),
                                                                            c.parameterResolverFactory()));
        sagaManager = new Component<>(() -> config, managerName, c -> new AnnotatedSagaManager<>(sagaType, sagaRepository.get(),
                                                                                                 c.parameterResolverFactory(),
                                                                                                 listenerInvocationErrorHandler
                                                                                                         .get()));
        trackingEventProcessorConfiguration = new Component<>(() -> config, "ProcessorConfiguration",
                                                              c -> c.getComponent(TrackingEventProcessorConfiguration.class,
                                                                                  TrackingEventProcessorConfiguration::forSingleThreadedProcessing));
        processor = new Component<>(() -> config, processorName,
                                    c -> {
                                        SubscribingEventProcessor processor = new SubscribingEventProcessor(processorName, sagaManager.get(),
                                                                                                            rollbackConfiguration.get(),
                                                                                                            messageSourceBuilder.apply(c),
                                                                                                            processingStrategy.apply(c),
                                                                                                            errorHandler.get(),
                                                                                                            messageMonitor.get());
                                        processor.registerInterceptor(new CorrelationDataInterceptor<>(c.correlationDataProviders()));
                                        return processor;
                                    });
    }

    /**
     * Configures the Saga Store to use to store Saga instances of this type. By default, Sagas are stored in the
     * Saga Store configured in the global Configuration. This method can be used to override the store for specific
     * Sagas.
     *
     * @param sagaStoreBuilder The builder that returns a fully initialized Saga Store instance based on the global
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

    /**
     * Registers the {@link TrackingEventProcessorConfiguration} to use when building the processor for this Saga type.
     * <p>
     * Note that the provided configuration is ignored when a subscribing processor is being used.
     *
     * @param trackingEventProcessorConfiguration The function to create the configuration instance
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> configureTrackingProcessor(Function<Configuration, TrackingEventProcessorConfiguration> trackingEventProcessorConfiguration) {
        this.trackingEventProcessorConfiguration.update(trackingEventProcessorConfiguration);
        return this;
    }

    /**
     * Registers the given {@code tokenStore} for use by a TrackingProcessor for the Saga being configured. The
     * TokenStore is ignored when a Subscribing Processor has been configured.
     *
     * @param tokenStore The function returning a TokenStore based on the given Configuration
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> configureTokenStore(Function<Configuration, TokenStore> tokenStore) {
        this.tokenStore.update(tokenStore);
        return this;
    }

    /**
     * Configures the ErrorHandler to use when an error occurs processing an Event.
     * <p>
     * The default is to propagate errors, causing the processors to release their token and go into a retry loop.
     *
     * @param errorHandler The function to create the ErrorHandler
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> configureErrorHandler(Function<Configuration, ErrorHandler> errorHandler) {
        this.errorHandler.update(errorHandler);
        return this;
    }

    /**
     * Configures the ListenerInvocationErrorHandler to use when processing of event in saga fails.
     * <p>
     * The default is to log errors.
     *
     * @param listenerInvocationErrorHandler The function to create ListenerInvocationErrorHandler
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> configureListenerInvocationErrorHandler(
            Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandler) {
        this.listenerInvocationErrorHandler.update(listenerInvocationErrorHandler);
        return this;
    }

    /**
     * Defines the policy to roll back or commit a Unit of Work in case exceptions occur.
     * <p>
     * Defaults to roll back on all exceptions.
     *
     * @param rollbackConfiguration The function providing the RollbackConfiguration to use
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> configureRollbackConfiguration(Function<Configuration, RollbackConfiguration> rollbackConfiguration) {
        this.rollbackConfiguration.update(rollbackConfiguration);
        return this;
    }

    /**
     * Defines the Transaction Manager to use when processing Events for this Saga. Typically, this transaction manager
     * will manage the transaction around storing of the tokens and Saga instances.
     * <p>
     * Defaults to the Transaction Manager defined in the main Configuration.
     *
     * @param transactionManager The function providing the TransactionManager to use
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> configureTransactionManager(Function<Configuration, TransactionManager> transactionManager) {
        this.transactionManager.update(transactionManager);
        return this;
    }

    /**
     * Configures a MessageMonitor to be used to monitor Events processed on by the Saga being configured.
     *
     * @param messageMonitor The function to create the MessageMonitor
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> configureMessageMonitor(Function<Configuration, MessageMonitor<? super EventMessage<?>>> messageMonitor) {
        this.messageMonitor.update(messageMonitor);
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
