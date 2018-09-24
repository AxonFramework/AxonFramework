/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DirectEventProcessingStrategy;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.MultiEventHandlerInvoker;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

/**
 * Non-thread safe event processing configuration. Holds a registry of {@link EventProcessor}s and {@link
 * EventHandlerInvoker}s and all necessary information in order to tie those two together. After calling {@link
 * #initialize(Configuration)}, all registrations (except for {@link MessageHandlerInterceptor}s) are ignored.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class EventProcessingConfiguration implements ModuleConfiguration {

    private final Map<String, List<Function<Configuration, EventHandlerInvoker>>> invokerBuilders = new HashMap<>();
    private final Map<String, String> processingGroupsAssignments = new HashMap<>();
    private Function<String, String> defaultProcessingGroupAssignment = Function.identity();
    private final Map<String, EventProcessorBuilder> eventProcessorBuilders = new HashMap<>();
    private final Map<String, Component<EventProcessor>> eventProcessors = new HashMap<>();
    private final List<BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>>> defaultHandlerInterceptors = new ArrayList<>();
    private final Map<String, List<Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>>>> handlerInterceptorsBuilders = new HashMap<>();
    private final Map<String, Function<Configuration, ErrorHandler>> errorHandlers = new HashMap<>();
    private final Map<String, Function<Configuration, RollbackConfiguration>> rollbackConfigurations = new HashMap<>();
    private final Map<String, Function<Configuration, TransactionManager>> transactionManagers = new HashMap<>();
    private final Map<String, MessageMonitorFactory> messageMonitorFactories = new HashMap<>();
    private final Map<String, Function<Configuration, TokenStore>> tokenStore = new HashMap<>();
    private EventProcessorBuilder defaultEventProcessorBuilder = this::defaultEventProcessor;
    private Configuration configuration;

    private final Component<ErrorHandler> defaultErrorHandler = new Component<>(
            () -> configuration,
            "errorHandler",
            c -> c.getComponent(ErrorHandler.class, PropagatingErrorHandler::instance)
    );

    private final Component<RollbackConfiguration> defaultRollbackConfiguration = new Component<>(
            () -> configuration,
            "rollbackConfiguration",
            c -> c.getComponent(RollbackConfiguration.class, () -> RollbackConfigurationType.ANY_THROWABLE)
    );

    private final Component<TransactionManager> defaultTransactionManager = new Component<>(
            () -> configuration,
            "transactionManager",
            c -> c.getComponent(TransactionManager.class, NoTransactionManager::instance)
    );

    /**
     * Registers handler invoker within this registry which will be assigned to the event processing group with given
     * {@code processingGroup} during the initialization phase.
     *
     * @param processingGroup            The name of the Processing Group
     * @param eventHandlerInvokerBuilder Builder which builds event handler invoker. Builder can use configuration to
     *                                   obtain other components
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerHandlerInvoker(String processingGroup,
                                                               Function<Configuration, EventHandlerInvoker> eventHandlerInvokerBuilder) {
        List<Function<Configuration, EventHandlerInvoker>> invokerBuilderFunctions =
                invokerBuilders.computeIfAbsent(processingGroup, k -> new ArrayList<>());
        invokerBuilderFunctions.add(eventHandlerInvokerBuilder);
        return this;
    }

    /**
     * Defines a mapping for assigning processing groups to processors.
     *
     * @param processingGroup The processing group to be assigned
     * @param processorName   The processor name to assign group to
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration assignProcessingGroup(String processingGroup, String processorName) {
        processingGroupsAssignments.put(processingGroup, processorName);
        return this;
    }

    /**
     * Defines a rule for assigning processing groups to processors if processing group to processor name mapping does
     * not contain the entry.
     *
     * @param assignmentRule The function which takes processing group and returns processor name
     * @return event processing configuration for chaining purposes
     *
     * @see #assignProcessingGroup(String, String)
     */
    public EventProcessingConfiguration assignProcessingGroup(Function<String, String> assignmentRule) {
        defaultProcessingGroupAssignment = assignmentRule;
        return this;
    }

    /**
     * Defines the Event Processor builder for an Event Processor with the given {@code name}. Event Processors
     * registered using this method have priority over those defined in
     * {@link #registerEventProcessorFactory(EventProcessorBuilder)}.
     * <p>
     * The given builder is expected to create a fully initialized Event Processor implementation based on the name and
     * list of event handler beans. The builder also received the global configuration instance, from which it can
     * retrieve components.
     * <p>
     * Note that the processor must be initialized, but shouldn't be started yet. The processor's
     * {@link EventProcessor#start()} method is invoked when the global configuration is started.
     *
     * @param name                  The name of the Event Processor for which to use this builder
     * @param eventProcessorBuilder The builder function for the Event Processor
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerEventProcessor(String name,
                                                               EventProcessorBuilder eventProcessorBuilder) {
        if (eventProcessorBuilders.containsKey(name)) {
            throw new IllegalArgumentException(format("Event processor with name %s already exists", name));
        }
        eventProcessorBuilders.put(name, eventProcessorBuilder);
        return this;
    }

    /**
     * Register a TrackingEventProcessor using default configuration for the given {@code name}. Unlike
     * {@link #usingTrackingProcessors()}, this method will not default all processors to tracking, but instead only
     * use tracking for event handler that have been assigned to the processor with given {@code name}.
     * <p>
     * Events will be read from the EventBus (or EventStore) registered with the main configuration
     *
     * @param name The name of the processor
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerTrackingEventProcessor(String name) {
        return registerTrackingEventProcessorUsingSource(name, Configuration::eventBus);
    }

    /**
     * Registers a TrackingEventProcessor using the given {@code source} to read messages from.
     *
     * @param name   The name of the TrackingEventProcessor
     * @param source The source of messages for this processor
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerTrackingEventProcessorUsingSource(String name,
                                                                                  Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source) {
        return registerTrackingEventProcessor(name,
                                              source,
                                              c -> c.getComponent(TrackingEventProcessorConfiguration.class,
                                                                  TrackingEventProcessorConfiguration::forSingleThreadedProcessing));
    }

    /**
     * Registers a TrackingEventProcessor with the given {@code name}, reading from the Event Bus (or Store) from the
     * main configuration and using the given {@code processorConfiguration}.
     *
     * @param name                   The name of the Tracking Processor
     * @param processorConfiguration The configuration for the processor
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerTrackingEventProcessor(String name,
                                                                       Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration) {
        return registerTrackingEventProcessor(name, Configuration::eventBus, processorConfiguration);
    }

    /**
     * Registers a TrackingEventProcessor with the given {@code name}, reading from the given {@code source} and using
     * the given {@code processorConfiguration}.
     *
     * @param name                   The name of the Tracking Processor
     * @param source                 The source to read Events from
     * @param processorConfiguration The configuration for the processor
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerTrackingEventProcessor(String name,
                                                                       Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source,
                                                                       Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration) {
        registerEventProcessor(name, (n, c, ehi) -> trackingEventProcessor(c, n, ehi, processorConfiguration, source));
        return this;
    }

    /**
     * Register a subscribing event processor with given {@code name} that subscribes to the Event Bus.
     *
     * @param name The name of the Event Processor
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerSubscribingEventProcessor(String name) {
        return registerSubscribingEventProcessor(name, Configuration::eventBus);
    }

    /**
     * Register a subscribing event processor with given {@code name} that subscribes to the given {@code
     * messageSource}. This allows the use of standard Subscribing Event Processors that listen to another source than
     * the Event Bus.
     *
     * @param name          The name of the Event Processor
     * @param messageSource The source the processor should read from
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerSubscribingEventProcessor(String name,
                                                                          Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
        registerEventProcessor(name, (n, c, ehi) -> subscribingEventProcessor(n, c, ehi, messageSource));
        return this;
    }

    /**
     * Configure the use of Tracking Event Processors, instead of the default Subscribing ones. Tracking processors
     * work in their own thread(s), making processing asynchronous from the publication process.
     * <p>
     * The processor will use the {@link TokenStore} implementation provided in the global Configuration, and will
     * default to an {@link InMemoryTokenStore} when no Token Store was defined. Note that it is not recommended to use
     * the in-memory TokenStore in a production environment.
     * <p>
     * The processors will use the a {@link TrackingEventProcessorConfiguration} registered with the configuration, or
     * otherwise to a single threaded configuration (which means the processor will run in a single Thread and a batch
     * size of 1).
     *
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration usingTrackingProcessors() {
        return usingTrackingProcessors(c -> c.getComponent(TrackingEventProcessorConfiguration.class,
                                                           TrackingEventProcessorConfiguration::forSingleThreadedProcessing));
    }

    /**
     * Configure the use of Tracking Event Processors, instead of the default Subscribing ones. Tracking processors
     * work in their own thread(s), making processing asynchronous from the publication process.
     * <p>
     * The processor will use the {@link TokenStore} implementation provided in the global Configuration, and will
     * default to an {@link InMemoryTokenStore} when no Token Store was defined. Note that it is not recommended to use
     * the in-memory TokenStore in a production environment.
     *
     * @param processorConfig The configuration for the processors to use
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration usingTrackingProcessors(
            Function<Configuration, TrackingEventProcessorConfiguration> processorConfig) {
        registerEventProcessorFactory(
                (name, config, ehi) -> {
                    TrackingEventProcessor trackingEventProcessor =
                            trackingEventProcessor(config, name, ehi, processorConfig, Configuration::eventBus);
                    trackingEventProcessor
                            .registerHandlerInterceptor(new CorrelationDataInterceptor<>(config.correlationDataProviders()));
                    return trackingEventProcessor;
                });
        return this;
    }

    /**
     * Register the TokenStore to use for a processor of given {@code name}.
     * <p>
     * If no explicit TokenStore implementation is available for a Processor, it is taken from the main Configuration.
     * <p>
     * Note that this configuration is ignored if the processor with given name isn't a Tracking Processor.
     *
     * @param name       The name of the processor to configure the token store for
     * @param tokenStore The function providing the TokenStore based on a given Configuration
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerTokenStore(String name,
                                                           Function<Configuration, TokenStore> tokenStore) {
        this.tokenStore.put(name, tokenStore);
        return this;
    }

    /**
     * Register the given {@code interceptorBuilder} to build an Message Handling Interceptor for the Event Processor
     * with given {@code processorName}.
     * <p>
     * The {@code interceptorBuilder} may return {@code null}, in which case the return value is ignored.
     *
     * @param processorName      The name of the processor to register the interceptor on
     * @param interceptorBuilder The function providing the interceptor to register, or {@code null}
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerHandlerInterceptor(String processorName,
                                                                   Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder) {
        if (configuration != null) {
            eventProcessor(processorName).ifPresent(eventProcessor -> eventProcessor
                    .registerHandlerInterceptor(interceptorBuilder.apply(configuration)));
        }
        handlerInterceptorsBuilders.computeIfAbsent(processorName, k -> new ArrayList<>())
                                   .add(interceptorBuilder);
        return this;
    }

    /**
     * Register the given {@code interceptorBuilder} to build an Message Handling Interceptor for Event Processors
     * created in this configuration.
     * <p>
     * The {@code interceptorBuilder} is invoked once for each processor created, and may return {@code null}, in which
     * case the return value is ignored.
     * <p>
     *
     * @param interceptorBuilder The builder function that provides an interceptor for each available processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventProcessingConfiguration registerHandlerInterceptor(
            BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder) {
        defaultHandlerInterceptors.add(interceptorBuilder);
        return this;
    }

    /**
     * Returns a list of interceptors for a processor with given {@code processorName}.
     *
     * @param processorName The name of the processor
     * @return a list of interceptors for a processor with given {@code processorName}
     */
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor(String processorName) {
        Assert.state(configuration != null, () -> "Configuration is not initialized yet");

        Component<EventProcessor> eventProcessorComponent = eventProcessors.get(processorName);

        if (eventProcessorComponent == null) {
            return emptyList();
        }

        return eventProcessorComponent.get().getHandlerInterceptors();
    }

    /**
     * Allows for more fine-grained definition of the Event Processor to use for each group of Event Listeners. The
     * given builder is expected to create a fully initialized Event Processor implementation based on the name and
     * list of event handler beans. The builder also received the global configuration instance, from which it can
     * retrieve components.
     * <p>
     * Note that the processor must be initialized, but shouldn't be started yet. The processor's
     * {@link EventProcessor#start()} method is invoked when the global configuration is started.
     *
     * @param eventProcessorBuilder The builder function for the Event Processor
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration registerEventProcessorFactory(EventProcessorBuilder eventProcessorBuilder) {
        this.defaultEventProcessorBuilder = eventProcessorBuilder;
        return this;
    }

    /**
     * Configures the default {@link org.axonframework.eventhandling.ErrorHandler} for any
     * {@link org.axonframework.eventhandling.EventProcessor}. This can be overridden per EventProcessor by calling the
     * {@link #configureErrorHandler(String, Function)} function.
     *
     * @param errorHandlerBuilder The {@link org.axonframework.eventhandling.ErrorHandler} to use for the
     *                            {@link org.axonframework.eventhandling.EventProcessor} with the given {@code name}
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration configureErrorHandler(
            Function<Configuration, ErrorHandler> errorHandlerBuilder) {
        this.defaultErrorHandler.update(errorHandlerBuilder);
        return this;
    }

    /**
     * Configures a {@link org.axonframework.eventhandling.ErrorHandler} for the
     * {@link org.axonframework.eventhandling.EventProcessor} of the given {@code name}. This
     * overrides the default ErrorHandler configured through the {@link org.axonframework.config.Configurer}.
     *
     * @param name                The name of the event processor
     * @param errorHandlerBuilder The {@link org.axonframework.eventhandling.ErrorHandler} to use for the
     *                            {@link org.axonframework.eventhandling.EventProcessor} with the given {@code name}
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration configureErrorHandler(String name,
                                                              Function<Configuration, ErrorHandler> errorHandlerBuilder) {
        this.errorHandlers.put(name, errorHandlerBuilder);
        return this;
    }

    /**
     * Configures a {@link RollbackConfiguration} for the {@link EventProcessor} of the given {@code name}. This
     * overrides the default RollbackConfiguration configured through the {@link Configurer}.
     *
     * @param name                         The name of the event processor
     * @param rollbackConfigurationBuilder The {@link RollbackConfiguration} to use for the {@link EventProcessor} with
     *                                     the given {@code name}
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration configureRollbackConfiguration(String name,
                                                                       Function<Configuration, RollbackConfiguration> rollbackConfigurationBuilder) {
        this.rollbackConfigurations.put(name, rollbackConfigurationBuilder);
        return this;
    }

    /**
     * Configures a {@link TransactionManager} for the {@link EventProcessor} of the given {@code name}. This overrides
     * the default TransactionManager configured through the {@link Configurer}.
     *
     * @param name                      The name of the event processor
     * @param transactionManagerBuilder The {@link TransactionManager} to use of the {@link EventProcessor} with the
     *                                  given {@code name}
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration configureTransactionManager(String name,
                                                                    Function<Configuration, TransactionManager> transactionManagerBuilder) {
        this.transactionManagers.put(name, transactionManagerBuilder);
        return this;
    }

    /**
     * Configures the builder function to create the Message Monitor for the {@link EventProcessor} of the given name.
     * This overrides any Message Monitor configured through {@link Configurer}.
     *
     * @param name                  The name of the event processor
     * @param messageMonitorBuilder The builder function to use
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration configureMessageMonitor(String name,
                                                                Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
        return configureMessageMonitor(name,
                                       (configuration, componentType, componentName) -> messageMonitorBuilder
                                               .apply(configuration));
    }

    /**
     * Configures the factory to create the Message Monitor for the {@link EventProcessor} of the given name. This
     * overrides any Message Monitor configured through {@link Configurer}.
     *
     * @param name                  The name of the event processor
     * @param messageMonitorFactory The factory to use
     * @return event processing configuration for chaining purposes
     */
    public EventProcessingConfiguration configureMessageMonitor(String name,
                                                                MessageMonitorFactory messageMonitorFactory) {
        this.messageMonitorFactories.put(name, messageMonitorFactory);
        return this;
    }

    /**
     * Obtains all registered event processors. This method is to be called after Event Processor Registry is
     * initialized.
     *
     * @return map of registered event processors within this registry where processor name is the key
     */
    public Map<String, EventProcessor> eventProcessors() {
        Assert.state(configuration != null, () -> "Configuration is not initialized yet");
        Map<String, EventProcessor> result = new HashMap<>(eventProcessors.size());
        eventProcessors.forEach((name, component) -> result.put(name, component.get()));
        return result;
    }

    /**
     * Obtains Event Processor by name. This method is to be called after Event Processor Registry is initialized.
     *
     * @param name The name of the event processor
     * @param <T>  The type of processor expected
     * @return optional whether event processor with given name exists
     */
    @SuppressWarnings("unchecked")
    public <T extends EventProcessor> Optional<T> eventProcessor(String name) {
        return (Optional<T>) Optional.ofNullable(eventProcessors().get(name));
    }

    /**
     * Returns the Event Processor with the given {@code name}, if present and of the given {@code expectedType}.
     *
     * @param name         The name of the processor to return
     * @param expectedType The type of processor expected
     * @param <T>          The type of processor expected
     * @return an Optional referencing the processor, if present and of expected type
     */
    public <T extends EventProcessor> Optional<T> eventProcessor(String name, Class<T> expectedType) {
        return eventProcessor(name).filter(expectedType::isInstance).map(expectedType::cast);
    }

    /**
     * Obtains Event Processor by processing group name. This method is to be called after Event Processor Registry is
     * initialized.
     *
     * @param processingGroup The name of the processing group
     * @param <T>             The type of processor expected
     * @return an Optional referencing the processor
     */
    @SuppressWarnings("unchecked")
    public <T extends EventProcessor> Optional<T> eventProcessorByProcessingGroup(String processingGroup) {
        return (Optional<T>) Optional.ofNullable(eventProcessors()
                                                         .get(processorNameForProcessingGroup(processingGroup)));
    }

    /**
     * Returns the Event Processor by the given {@code processingGroup}, if present and of the given {@code
     * expectedType}.
     *
     * @param processingGroup The name of the processing group
     * @param expectedType    The type of processor expected
     * @param <T>             The type of processor expected
     * @return an Optional referencing the processor, if present and of expected type
     */
    public <T extends EventProcessor> Optional<T> eventProcessorByProcessingGroup(String processingGroup,
                                                                                  Class<T> expectedType) {
        return eventProcessorByProcessingGroup(processingGroup).filter(expectedType::isInstance)
                                                               .map(expectedType::cast);
    }

    @Override
    public int phase() {
        return 10;
    }

    @Override
    public void initialize(Configuration config) {
        this.configuration = config;
        eventProcessors.clear();
        Map<String, List<Function<Configuration, EventHandlerInvoker>>> builderFunctionsPerProcessor = new HashMap<>();
        invokerBuilders.forEach((processingGroup, builderFunctions) -> {
            String processorName = processorNameForProcessingGroup(processingGroup);
            builderFunctionsPerProcessor.compute(processorName, (k, currentBuilderFunctions) -> {
                if (currentBuilderFunctions == null) {
                    return builderFunctions;
                }
                currentBuilderFunctions.addAll(builderFunctions);
                return currentBuilderFunctions;
            });
        });
        builderFunctionsPerProcessor.forEach((processorName, builderFunctions) ->
            eventProcessors.put(processorName,
                                new Component<>(config,
                                                processorName,
                                                c -> buildEventProcessor(config, builderFunctions, processorName)))
        );
    }

    @Override
    public void start() {
        eventProcessors.forEach((name, component) -> component.get().start());
    }

    @Override
    public void shutdown() {
        eventProcessors.forEach((name, component) -> component.get().shutDown());
    }

    private SubscribingEventProcessor defaultEventProcessor(String name, Configuration conf,
                                                            EventHandlerInvoker eventHandlerInvoker) {
        SubscribingEventProcessor eventProcessor = subscribingEventProcessor(name,
                                                                             conf,
                                                                             eventHandlerInvoker,
                                                                             Configuration::eventBus);
        eventProcessor.registerHandlerInterceptor(new CorrelationDataInterceptor<>(conf.correlationDataProviders()));
        return eventProcessor;
    }

    private SubscribingEventProcessor subscribingEventProcessor(String name, Configuration conf,
                                                                EventHandlerInvoker eventHandlerInvoker,
                                                                Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
        return new SubscribingEventProcessor(name,
                                             eventHandlerInvoker,
                                             getRollbackConfiguration(conf, name),
                                             messageSource.apply(conf),
                                             DirectEventProcessingStrategy.INSTANCE,
                                             getErrorHandler(conf, name),
                                             getMessageMonitor(conf, SubscribingEventProcessor.class, name));
    }

    private TrackingEventProcessor trackingEventProcessor(Configuration conf, String name,
                                                          EventHandlerInvoker eventHandlerInvoker,
                                                          Function<Configuration, TrackingEventProcessorConfiguration> config,
                                                          Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source) {
        return new TrackingEventProcessor(name,
                                          eventHandlerInvoker,
                                          source.apply(conf),
                                          tokenStore.getOrDefault(
                                                  name,
                                                  c -> c.getComponent(TokenStore.class, InMemoryTokenStore::new)
                                          ).apply(conf),
                                          getTransactionManager(conf, name),
                                          getMessageMonitor(conf, EventProcessor.class, name),
                                          getRollbackConfiguration(conf, name),
                                          getErrorHandler(conf, name),
                                          config.apply(conf));
    }

    private MessageMonitor<? super Message<?>> getMessageMonitor(Configuration config,
                                                                 Class<?> componentType,
                                                                 String componentName) {
        if (messageMonitorFactories.containsKey(componentName)) {
            return messageMonitorFactories.get(componentName).create(config, componentType, componentName);
        } else {
            return config.messageMonitor(componentType, componentName);
        }
    }

    private ErrorHandler getErrorHandler(Configuration config, String componentName) {
        return errorHandlers.containsKey(componentName)
                ? errorHandlers.get(componentName).apply(config)
                : defaultErrorHandler.get();
    }

    private RollbackConfiguration getRollbackConfiguration(Configuration config, String componentName) {
        return rollbackConfigurations.containsKey(componentName)
                ? rollbackConfigurations.get(componentName).apply(config)
                : defaultRollbackConfiguration.get();
    }

    private TransactionManager getTransactionManager(Configuration config, String componentName) {
        return transactionManagers.containsKey(componentName)
                ? transactionManagers.get(componentName).apply(config)
                : defaultTransactionManager.get();
    }

    private String processorNameForProcessingGroup(String processingGroup) {
        return processingGroupsAssignments.getOrDefault(processingGroup,
                                                        defaultProcessingGroupAssignment.apply(processingGroup));
    }

    private EventProcessor buildEventProcessor(Configuration config,
                                               List<Function<Configuration, EventHandlerInvoker>> builderFunctions,
                                               String processorName) {
        List<EventHandlerInvoker> invokers = builderFunctions
                .stream()
                .map(invokerBuilder -> invokerBuilder.apply(config))
                .collect(Collectors.toList());
        MultiEventHandlerInvoker multiEventHandlerInvoker = new MultiEventHandlerInvoker(invokers);

        EventProcessor eventProcessor = eventProcessorBuilders
                .getOrDefault(processorName, defaultEventProcessorBuilder)
                .build(processorName,
                       configuration,
                       multiEventHandlerInvoker);

        handlerInterceptorsBuilders.getOrDefault(processorName, new ArrayList<>())
                                   .stream()
                                   .map(hi -> hi.apply(config))
                                   .forEach(eventProcessor::registerHandlerInterceptor);

        defaultHandlerInterceptors.stream()
                                  .map(f -> f.apply(configuration, processorName))
                                  .filter(Objects::nonNull)
                                  .forEach(eventProcessor::registerHandlerInterceptor);

        return eventProcessor;
    }

    /**
     * Contract which defines how to build an event processor.
     */
    @FunctionalInterface
    public interface EventProcessorBuilder {

        /**
         * Builds the event processor.
         *
         * @param name                The name of the Event Processor to create
         * @param configuration       The global configuration the implementation may use to obtain dependencies
         * @param eventHandlerInvoker The invoker which is used to invoke event handlers and assigned to this processor
         * @return the event processor
         */
        EventProcessor build(String name, Configuration configuration, EventHandlerInvoker eventHandlerInvoker);
    }
}
