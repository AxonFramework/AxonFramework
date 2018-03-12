/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Comparator.comparing;

/**
 * Module Configuration implementation that defines an Event Handling component. Typically, such a configuration
 * consists of a number of Event Handlers and one or more Event Processors that define the transactional semantics of
 * the processing. Each Event Handler is assigned to one Event Processor.
 */
public class EventHandlingConfiguration implements ModuleConfiguration {

    private final List<Component<Object>> eventHandlers = new ArrayList<>();
    private final List<BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>>> defaultHandlerInterceptors = new ArrayList<>();
    private final Map<String, List<Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>>>> handlerInterceptors = new HashMap<>();
    private final Map<String, EventProcessorBuilder> eventProcessors = new HashMap<>();
    private final List<ProcessorSelector> selectors = new ArrayList<>();
    private final List<EventProcessor> initializedProcessors = new ArrayList<>();
    private final Map<String, Function<Configuration, ListenerInvocationErrorHandler>> listenerInvocationErrorHandlers = new HashMap<>();
    private final Map<String, MessageMonitorFactory> messageMonitorFactories = new HashMap<>();
    private final Map<String, Function<Configuration, ErrorHandler>> errorHandlers = new HashMap<>();
    private final Map<String, Function<Configuration, TokenStore>> tokenStore = new HashMap<>();
    private EventProcessorBuilder defaultEventProcessorBuilder = this::defaultEventProcessor;
    // Set up the default selector that determines the processing group by inspecting the @ProcessingGroup annotation;
    // if no annotation is present, the package name is used
    private Function<Object, String> fallback = (o) -> o.getClass().getPackage().getName();
    private final ProcessorSelector defaultSelector = new ProcessorSelector(
            Integer.MIN_VALUE,
            o -> {
                Class<?> handlerType = o.getClass();
                Optional<Map<String, Object>> annAttr = AnnotationUtils.findAnnotationAttributes(handlerType,
                                                                                                 ProcessingGroup.class);
                return Optional.of(annAttr.map(attr -> (String) attr.get("processingGroup"))
                                          .orElseGet(() -> fallback.apply(o)));
            });
    private Configuration config;
    private final Component<ListenerInvocationErrorHandler> defaultListenerInvocationErrorHandler = new Component<>(
            () -> config,
            "listenerInvocationErrorHandler",
            c -> c.getComponent(ListenerInvocationErrorHandler.class, LoggingErrorHandler::new)
    );
    private final Component<ErrorHandler> defaultErrorHandler = new Component<>(
            () -> config, "errorHandler", c -> c.getComponent(ErrorHandler.class, PropagatingErrorHandler::instance)
    );

    /**
     * Creates a default configuration for an Event Handling module that creates a {@link SubscribingEventProcessor}
     * instance for all Event Handlers that have the same Processing Group name. The Processing Group name is determined
     * by inspecting the {@link ProcessingGroup} annotation; if no annotation is present, the package name is used as
     * the Processing Group name. This default behavior can be overridden in the instance returned.
     * <p>
     * At a minimum, the Event Handler beans need to be registered before this component is useful.
     */
    public EventHandlingConfiguration() {
    }

    private SubscribingEventProcessor defaultEventProcessor(Configuration conf, String name, List<?> eh) {
        return subscribingEventProcessor(conf, name, eh, Configuration::eventBus);
    }

    private SubscribingEventProcessor subscribingEventProcessor(Configuration conf, String name, List<?> eh,
                                                                Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
        return new SubscribingEventProcessor(name,
                                             new SimpleEventHandlerInvoker(
                                                     eh,
                                                     conf.parameterResolverFactory(),
                                                     getListenerInvocationErrorHandler(conf, name)
                                             ),
                                             messageSource.apply(conf),
                                             DirectEventProcessingStrategy.INSTANCE,
                                             getErrorHandler(conf, name),
                                             getMessageMonitor(conf, SubscribingEventProcessor.class, name));
    }

    /**
     * Returns the list of Message Handler Interceptors registered for the given {@code processorName}.
     *
     * @param configuration The main configuration
     * @param processorName The name of the processor to retrieve interceptors for
     * @return a list of Interceptors
     *
     * @see EventHandlingConfiguration#registerHandlerInterceptor(BiFunction)
     * @see EventHandlingConfiguration#registerHandlerInterceptor(String, Function)
     */
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor(Configuration configuration,
                                                                                    String processorName) {
        List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new ArrayList<>();
        defaultHandlerInterceptors.stream()
                                  .map(f -> f.apply(configuration, processorName))
                                  .filter(Objects::nonNull)
                                  .forEach(interceptors::add);
        handlerInterceptors.getOrDefault(processorName, Collections.emptyList())
                           .stream()
                           .map(f -> f.apply(configuration))
                           .filter(Objects::nonNull)
                           .forEach(interceptors::add);
        interceptors.add(new CorrelationDataInterceptor<>(configuration.correlationDataProviders()));
        return interceptors;
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
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration usingTrackingProcessors() {
        return usingTrackingProcessors(c -> c.getComponent(TrackingEventProcessorConfiguration.class,
                                                           TrackingEventProcessorConfiguration::forSingleThreadedProcessing),
                                       c -> new SequentialPerAggregatePolicy());
    }

    /**
     * Configure the use of Tracking Event Processors, instead of the default Subscribing ones. Tracking processors
     * work in their own thread(s), making processing asynchronous from the publication process.
     * <p>
     * The processor will use the {@link TokenStore} implementation provided in the global Configuration, and will
     * default to an {@link InMemoryTokenStore} when no Token Store was defined. Note that it is not recommended to use
     * the in-memory TokenStore in a production environment.
     *
     * @param config           The configuration for the processors to use
     * @param sequencingPolicy The policy for processing events sequentially
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration usingTrackingProcessors(
            Function<Configuration, TrackingEventProcessorConfiguration> config,
            Function<Configuration, SequencingPolicy<? super EventMessage<?>>> sequencingPolicy) {
        return registerEventProcessorFactory(
                (conf, name, handlers) -> buildTrackingEventProcessor(conf, name, handlers, config,
                                                                      Configuration::eventBus,
                                                                      sequencingPolicy));
    }

    /**
     * Register a TrackingProcessor using default configuration for the given {@code name}. Unlike
     * {@link #usingTrackingProcessors()}, this method will not default all processors to tracking, but instead only
     * use tracking for event handler that have been assigned to the processor with given {@code name}.
     * <p>
     * Events will be read from the EventBus (or EventStore) registered with the main configuration
     *
     * @param name The name of the processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    @SuppressWarnings("UnusedReturnValue")
    public EventHandlingConfiguration registerTrackingProcessor(String name) {
        return registerTrackingProcessor(name, Configuration::eventBus);
    }

    /**
     * Registers a TrackingProcessor using the given {@code source} to read messages from.
     *
     * @param name   The name of the TrackingProcessor
     * @param source The source of messages for this processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    @SuppressWarnings("unchecked")
    public EventHandlingConfiguration registerTrackingProcessor(String name,
                                                                Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source) {
        return registerTrackingProcessor(
                name,
                source,
                c -> c.getComponent(TrackingEventProcessorConfiguration.class,
                                    TrackingEventProcessorConfiguration::forSingleThreadedProcessing),
                c -> c.getComponent(SequencingPolicy.class, SequentialPerAggregatePolicy::new)
        );
    }

    /**
     * Registers a TrackingProcessor with the given {@code name}, reading from the Event Bus (or Store) from the main
     * configuration and using the given {@code processorConfiguration}. The given {@code sequencingPolicy} defines
     * the policy for events that need to be executed sequentially.
     *
     * @param name                   The name of the Tracking Processor
     * @param processorConfiguration The configuration for the processor
     * @param sequencingPolicy       The sequencing policy to apply when processing events in parallel
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerTrackingProcessor(String name,
                                                                Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration,
                                                                Function<Configuration, SequencingPolicy<? super EventMessage<?>>> sequencingPolicy) {
        return registerTrackingProcessor(name, Configuration::eventBus, processorConfiguration, sequencingPolicy);
    }

    /**
     * Registers a TrackingProcessor with the given {@code name}, reading from the given {@code source} and using the
     * given {@code processorConfiguration}. The given {@code sequencingPolicy} defines the policy for events that need
     * to be executed sequentially.
     *
     * @param name                   The name of the Tracking Processor
     * @param source                 The source to read Events from
     * @param processorConfiguration The configuration for the processor
     * @param sequencingPolicy       The sequencing policy to apply when processing events in parallel
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerTrackingProcessor(String name,
                                                                Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source,
                                                                Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration,
                                                                Function<Configuration, SequencingPolicy<? super EventMessage<?>>> sequencingPolicy) {
        return registerEventProcessor(name, (conf, n, handlers) ->
                buildTrackingEventProcessor(conf, name, handlers, processorConfiguration, source, sequencingPolicy));
    }

    private EventProcessor buildTrackingEventProcessor(Configuration conf, String name, List<?> handlers,
                                                       Function<Configuration, TrackingEventProcessorConfiguration> config,
                                                       Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source,
                                                       Function<Configuration, SequencingPolicy<? super EventMessage<?>>> sequencingPolicy) {
        return new TrackingEventProcessor(name,
                                          new SimpleEventHandlerInvoker(handlers,
                                                                        conf.parameterResolverFactory(),
                                                                        getListenerInvocationErrorHandler(conf, name),
                                                                        sequencingPolicy.apply(conf)),
                                          source.apply(conf),
                                          tokenStore.getOrDefault(
                                                  name,
                                                  c -> c.getComponent(TokenStore.class, InMemoryTokenStore::new)
                                          ).apply(conf),
                                          conf.getComponent(TransactionManager.class, NoTransactionManager::instance),
                                          getMessageMonitor(conf, EventProcessor.class, name),
                                          RollbackConfigurationType.ANY_THROWABLE,
                                          getErrorHandler(conf, name),
                                          config.apply(conf));
    }

    private ListenerInvocationErrorHandler getListenerInvocationErrorHandler(Configuration config,
                                                                             String componentName) {
        return listenerInvocationErrorHandlers.containsKey(componentName)
                ? listenerInvocationErrorHandlers.get(componentName).apply(config)
                : defaultListenerInvocationErrorHandler.get();
    }

    private MessageMonitor<? super Message<?>> getMessageMonitor(Configuration configuration,
                                                                 Class<?> componentType,
                                                                 String componentName) {
        if (messageMonitorFactories.containsKey(componentName)) {
            return messageMonitorFactories.get(componentName).create(configuration, componentType, componentName);
        } else {
            return configuration.messageMonitor(componentType, componentName);
        }
    }

    private ErrorHandler getErrorHandler(Configuration config, String componentName) {
        return errorHandlers.containsKey(componentName)
                ? errorHandlers.get(componentName).apply(config)
                : defaultErrorHandler.get();
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
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerEventProcessorFactory(EventProcessorBuilder eventProcessorBuilder) {
        this.defaultEventProcessorBuilder = eventProcessorBuilder;
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
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerEventProcessor(String name, EventProcessorBuilder eventProcessorBuilder) {
        eventProcessors.put(name, eventProcessorBuilder);
        return this;
    }

    /**
     * Register the given {@code interceptorBuilder} to build an Message Handling Interceptor for the Event Processor
     * with given {@code processorName}.
     * <p>
     * The {@code interceptorBuilder} may return {@code null}, in which case the return value is ignored.
     * <p>
     * Note that a CorrelationDataInterceptor is registered by default. To change correlation data attached to messages,
     * see {@link Configurer#configureCorrelationDataProviders(Function)}.
     *
     * @param processorName      The name of the processor to register the interceptor on
     * @param interceptorBuilder The function providing the interceptor to register, or {@code null}
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerHandlerInterceptor(String processorName,
                                                                 Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder) {
        handlerInterceptors
                .computeIfAbsent(processorName, k -> new ArrayList<>())
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
     * Note that a CorrelationDataInterceptor is registered by default. To change correlation data attached to messages,
     * see {@link Configurer#configureCorrelationDataProviders(Function)}.
     *
     * @param interceptorBuilder The builder function that provides an interceptor for each available processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerHandlerInterceptor(
            BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder) {
        defaultHandlerInterceptors.add(interceptorBuilder);
        return this;
    }

    /**
     * Registers the Event Processor name to assign Event Handler beans to when no other, more explicit, rule matches
     * and no {@link ProcessingGroup} annotation is found.
     *
     * @param name The Event Processor name to assign Event Handlers to
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration byDefaultAssignTo(String name) {
        return byDefaultAssignTo((object) -> name);
    }

    /**
     * Registers a function that defines the Event Processor name to assign Event Handler beans to when no other, more
     * explicit, rule matches and no {@link ProcessingGroup} annotation is found.
     *
     * @param assignmentFunction The function that returns a Processor Name for each Event Handler bean
     * @return this EventHandlingConfiguration instance for further configuration
     */
    @SuppressWarnings("UnusedReturnValue")
    public EventHandlingConfiguration byDefaultAssignTo(Function<Object, String> assignmentFunction) {
        fallback = assignmentFunction;
        return this;
    }

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Event Processor
     * with given {@code name}, with neutral priority (value 0).
     * <p>
     * Note that, when beans match multiple criteria for different processors with equal priority, the outcome is
     * undefined.
     *
     * @param name     The name of the Event Processor to assign matching Event Handlers to
     * @param criteria The criteria for Event Handler to match
     * @return this EventHandlingConfiguration instance for further configuration
     */
    @SuppressWarnings("UnusedReturnValue")
    public EventHandlingConfiguration assignHandlersMatching(String name, Predicate<Object> criteria) {
        return assignHandlersMatching(name, 0, criteria);
    }

    /**
     * Configures a rule to assign Event Handler beans that match the given {@code criteria} to the Event Processor
     * with given {@code name}, with given {@code priority}. Rules with higher value of {@code priority} take precedence
     * over those with a lower value.
     * <p>
     * Note that, when beans match multiple criteria for different processors with equal priority, the outcome is
     * undefined.
     *
     * @param name     The name of the Event Processor to assign matching Event Handlers to
     * @param priority The priority for this rule
     * @param criteria The criteria for Event Handler to match
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration assignHandlersMatching(String name, int priority, Predicate<Object> criteria) {
        selectors.add(new ProcessorSelector(name, priority, criteria));
        return this;
    }

    /**
     * Register an Event Handler Bean with this configuration. The builder function receives the global Configuration
     * and is expected to return a fully initialized Event Handler bean, which is to be assigned to an Event Processor
     * using configured rules.
     *
     * @param eventHandlerBuilder The builder function for the Event Handler bean
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerEventHandler(Function<Configuration, Object> eventHandlerBuilder) {
        eventHandlers.add(new Component<>(() -> config, "eventHandler", eventHandlerBuilder));
        return this;
    }

    @Override
    public void initialize(Configuration config) {
        selectors.sort(comparing(ProcessorSelector::getPriority).reversed());
        this.config = config;

        Map<String, List<Object>> assignments = new HashMap<>();

        eventHandlers.stream().map(Component::get).forEach(handler -> {
            String processor =
                    selectors.stream().map(s -> s.select(handler)).filter(Optional::isPresent).map(Optional::get)
                             .findFirst()
                             .orElseGet(() -> defaultSelector.select(handler).orElseThrow(IllegalStateException::new));
            assignments.computeIfAbsent(processor, k -> new ArrayList<>()).add(handler);
        });

        assignments.forEach((name, handlers) -> {
            EventProcessor eventProcessor = eventProcessors.getOrDefault(name, defaultEventProcessorBuilder)
                                                           .createEventProcessor(config, name, handlers);
            interceptorsFor(config, name).forEach(eventProcessor::registerInterceptor);
            initializedProcessors.add(eventProcessor);
        });
    }

    @Override
    public void start() {
        initializedProcessors.forEach(EventProcessor::start);
    }

    @Override
    public void shutdown() {
        initializedProcessors.forEach(EventProcessor::shutDown);
    }

    /**
     * Register a subscribing processor with given {@code name} that subscribes to the Event Bus.
     *
     * @param name The name of the Event Processor
     * @return this EventHandlingConfiguration instance for further configuration
     */
    @SuppressWarnings("UnusedReturnValue")
    public EventHandlingConfiguration registerSubscribingEventProcessor(String name) {
        return registerEventProcessor(
                name, (conf, n, eh) -> subscribingEventProcessor(conf, n, eh, Configuration::eventBus));
    }

    /**
     * Register a subscribing processor with given {@code name} that subscribes to the given {@code messageSource}.
     * This allows the use of standard Subscribing Processors that listen to another source than the Event Bus.
     *
     * @param name          The name of the Event Processor
     * @param messageSource The source the processor should read from
     * @return this EventHandlingConfiguration instance for further configuration
     */
    @SuppressWarnings("UnusedReturnValue")
    public EventHandlingConfiguration registerSubscribingEventProcessor(
            String name,
            Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
        return registerEventProcessor(
                name,
                (c, n, eh) -> subscribingEventProcessor(c, n, eh, messageSource));
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
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration registerTokenStore(String name, Function<Configuration, TokenStore> tokenStore) {
        this.tokenStore.put(name, tokenStore);
        return this;
    }

    /**
     * Returns a list of Event Processors that have been initialized. Note that an empty list may be returned if this
     * configuration hasn't been {@link #initialize(Configuration) initialized} yet.
     *
     * @return a read-only list of processors initialized in this configuration.
     */
    public List<EventProcessor> getProcessors() {
        return Collections.unmodifiableList(initializedProcessors);
    }


    /**
     * Returns the Event Processor with the given {@code name}, if present. This method also returns an unresolved
     * optional if the Processor was configured, but it hasn't been assigned any Event Handlers.
     *
     * @param name The name of the processor to return
     * @return an Optional referencing the processor, if present.
     */
    public <T extends EventProcessor> Optional<T> getProcessor(String name) {
        //noinspection unchecked
        return (Optional<T>) initializedProcessors.stream().filter(p -> name.equals(p.getName())).findAny();
    }

    /**
     * Returns the Event Processor with the given {@code name}, if present and of the given {@code expectedType}. This
     * method also returns an empty optional if the Processor was configured, but it hasn't been assigned any Event
     * Handlers.
     *
     * @param name         The name of the processor to return
     * @param expectedType The type of processor expected
     * @param <T>          The type of processor expected
     * @return an Optional referencing the processor, if present and of expected type.
     */
    public <T extends EventProcessor> Optional<T> getProcessor(String name, Class<T> expectedType) {
        return getProcessor(name).filter(expectedType::isInstance).map(expectedType::cast);
    }

    /**
     * Configures the default {@link org.axonframework.eventhandling.ListenerInvocationErrorHandler} for any
     * {@link org.axonframework.eventhandling.EventProcessor}. This can be overridden per EventProcessor by calling the
     * {@link EventHandlingConfiguration#configureListenerInvocationErrorHandler(String, Function)} function.
     *
     * @param listenerInvocationErrorHandlerBuilder The {@link org.axonframework.eventhandling.ListenerInvocationErrorHandler}
     *                                              to use for the {@link org.axonframework.eventhandling.EventProcessor}
     *                                              with the given {@code name}
     * @return this {@link EventHandlingConfiguration} instance for further configuration
     */
    public EventHandlingConfiguration configureListenerInvocationErrorHandler(
            Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder) {
        defaultListenerInvocationErrorHandler.update(listenerInvocationErrorHandlerBuilder);
        return this;
    }

    /**
     * Configures a {@link org.axonframework.eventhandling.ListenerInvocationErrorHandler} for the
     * {@link org.axonframework.eventhandling.EventProcessor} of the given {@code name}. This overrides the default
     * ListenerInvocationErrorHandler configured through the {@link org.axonframework.config.Configurer}.
     *
     * @param name                                  The name of the event processor
     * @param listenerInvocationErrorHandlerBuilder The {@link org.axonframework.eventhandling.ListenerInvocationErrorHandler}
     *                                              to use for the {@link org.axonframework.eventhandling.EventProcessor}
     *                                              with the given {@code name}
     * @return this {@link EventHandlingConfiguration} instance for further configuration
     */
    public EventHandlingConfiguration configureListenerInvocationErrorHandler(String name,
                                                                              Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder) {
        listenerInvocationErrorHandlers.put(name, listenerInvocationErrorHandlerBuilder);
        return this;
    }

    /**
     * Configures the builder function to create the Message Monitor for the {@link EventProcessor} of the given name.
     * This overrides any Message Monitor configured through {@link Configurer}.
     *
     * @param name                  The name of the event processor
     * @param messageMonitorBuilder The builder function to use
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration configureMessageMonitor(String name,
                                                              Function<Configuration, MessageMonitor<Message<?>>> messageMonitorBuilder) {
        return configureMessageMonitor(
                name,
                (configuration, componentType, componentName) -> messageMonitorBuilder.apply(configuration)
        );
    }

    /**
     * Configures the factory to create the Message Monitor for the {@link EventProcessor} of the given name. This
     * overrides any Message Monitor configured through {@link Configurer}.
     *
     * @param name                  The name of the event processor
     * @param messageMonitorFactory The factory to use
     * @return this EventHandlingConfiguration instance for further configuration
     */
    public EventHandlingConfiguration configureMessageMonitor(String name,
                                                              MessageMonitorFactory messageMonitorFactory) {
        messageMonitorFactories.put(name, messageMonitorFactory);
        return this;
    }

    /**
     * Configures the default {@link org.axonframework.eventhandling.ErrorHandler} for any
     * {@link org.axonframework.eventhandling.EventProcessor}. This can be overridden per EventProcessor by calling the
     * {@link EventHandlingConfiguration#configureErrorHandler(String, Function)} function.
     *
     * @param errorHandlerBuilder The {@link org.axonframework.eventhandling.ErrorHandler} to use for the
     *                            {@link org.axonframework.eventhandling.EventProcessor} with the given {@code name}
     * @return this {@link EventHandlingConfiguration} instance for further configuration
     */
    public EventHandlingConfiguration configureErrorHandler(Function<Configuration, ErrorHandler> errorHandlerBuilder) {
        defaultErrorHandler.update(errorHandlerBuilder);
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
     * @return this {@link EventHandlingConfiguration} instance for further configuration
     */
    public EventHandlingConfiguration configureErrorHandler(String name,
                                                            Function<Configuration, ErrorHandler> errorHandlerBuilder) {
        errorHandlers.put(name, errorHandlerBuilder);
        return this;
    }

    /**
     * Interface describing a Builder function for Event Processors.
     *
     * @see #createEventProcessor(Configuration, String, List)
     */
    @FunctionalInterface
    public interface EventProcessorBuilder {

        /**
         * Builder function for an Event Processor.
         *
         * @param configuration The global configuration the implementation may use to obtain dependencies
         * @param name          The name of the Event Processor to create
         * @param eventHandlers The Event Handler beans assigned to this processor
         * @return a fully initialized Event Processor
         */
        EventProcessor createEventProcessor(Configuration configuration, String name, List<?> eventHandlers);
    }

    private static class ProcessorSelector {

        private final int priority;
        private final Function<Object, Optional<String>> function;

        private ProcessorSelector(int priority, Function<Object, Optional<String>> selectorFunction) {
            this.priority = priority;
            this.function = selectorFunction;
        }

        private ProcessorSelector(String name, int priority, Predicate<Object> criteria) {
            this(priority, handler -> {
                if (criteria.test(handler)) {
                    return Optional.of(name);
                }
                return Optional.empty();
            });
        }

        public Optional<String> select(Object handler) {
            return function.apply(handler);
        }

        public int getPriority() {
            return priority;
        }
    }
}
