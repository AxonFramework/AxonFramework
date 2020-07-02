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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DirectEventProcessingStrategy;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.eventhandling.MultiEventHandlerInvoker;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.monitoring.MessageMonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

/**
 * Event processing module configuration. Registers all configuration components within itself, builds the {@link
 * EventProcessingConfiguration} and takes care of module lifecycle.
 *
 * @author Milan Savic
 * @since 4.0
 */
public class EventProcessingModule
        implements ModuleConfiguration, EventProcessingConfiguration, EventProcessingConfigurer {

    private final List<TypeProcessingGroupSelector> typeSelectors = new ArrayList<>();
    private final List<InstanceProcessingGroupSelector> instanceSelectors = new ArrayList<>();
    private final List<SagaConfigurer<?>> sagaConfigurations = new ArrayList<>();
    private final List<Component<Object>> eventHandlerBuilders = new ArrayList<>();
    private final Map<String, Component<ListenerInvocationErrorHandler>> listenerInvocationErrorHandlers = new HashMap<>();
    private final Map<String, Component<ErrorHandler>> errorHandlers = new HashMap<>();
    private final Map<String, EventProcessorBuilder> eventProcessorBuilders = new HashMap<>();
    private final Map<String, Component<EventProcessor>> eventProcessors = new HashMap<>();
    private final List<BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>>> defaultHandlerInterceptors = new ArrayList<>();
    private final Map<String, List<Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>>>> handlerInterceptorsBuilders = new HashMap<>();
    private final Map<String, String> processingGroupsAssignments = new HashMap<>();
    private final Map<String, Component<SequencingPolicy<? super EventMessage<?>>>> sequencingPolicies = new HashMap<>();
    private final Map<String, MessageMonitorFactory> messageMonitorFactories = new HashMap<>();
    private final Map<String, Component<TokenStore>> tokenStore = new HashMap<>();
    private final Map<String, Component<RollbackConfiguration>> rollbackConfigurations = new HashMap<>();
    private final Map<String, Component<TransactionManager>> transactionManagers = new HashMap<>();

    // Set up the default selector that determines the processing group by inspecting the @ProcessingGroup annotation;
    // if no annotation is present, the package name is used
    private Function<Class<?>, String> typeFallback = c -> c.getSimpleName() + "Processor";
    private final TypeProcessingGroupSelector defaultTypeSelector = TypeProcessingGroupSelector
            .defaultSelector(type -> annotatedProcessingGroupOfType(type).orElseGet(() -> typeFallback.apply(type)));

    private Function<Object, String> instanceFallback = EventProcessingModule::packageOfObject;
    private final InstanceProcessingGroupSelector defaultInstanceSelector = InstanceProcessingGroupSelector
            .defaultSelector(o -> annotatedProcessingGroupOfType(o.getClass()).orElseGet(() -> instanceFallback.apply(o)));

    private Configuration configuration;
    private final Component<ListenerInvocationErrorHandler> defaultListenerInvocationErrorHandler = new Component<>(
            () -> configuration,
            "listenerInvocationErrorHandler",
            c -> c.getComponent(ListenerInvocationErrorHandler.class, LoggingErrorHandler::new)
    );
    private final Component<ErrorHandler> defaultErrorHandler = new Component<>(
            () -> configuration,
            "errorHandler",
            c -> c.getComponent(ErrorHandler.class, PropagatingErrorHandler::instance)
    );
    private final Component<SequencingPolicy<? super EventMessage<?>>> defaultSequencingPolicy = new Component<>(
            () -> configuration,
            "sequencingPolicy",
            c -> SequentialPerAggregatePolicy.instance()
    );
    private final Component<TokenStore> defaultTokenStore = new Component<>(
            () -> configuration,
            "tokenStore",
            c -> c.getComponent(TokenStore.class, InMemoryTokenStore::new)
    );
    private final Component<RollbackConfiguration> defaultRollbackConfiguration = new Component<>(
            () -> configuration,
            "rollbackConfiguration",
            c -> c.getComponent(RollbackConfiguration.class, () -> RollbackConfigurationType.ANY_THROWABLE));
    private final Component<SagaStore> sagaStore = new Component<>(
            () -> configuration,
            "sagaStore",
            c -> c.getComponent(SagaStore.class, InMemorySagaStore::new)
    );
    private final Component<TransactionManager> defaultTransactionManager = new Component<>(
            () -> configuration,
            "transactionManager",
            c -> c.getComponent(TransactionManager.class, NoTransactionManager::instance)
    );
    @SuppressWarnings("unchecked")
    private Component<StreamableMessageSource<TrackedEventMessage<?>>> defaultStreamableSource =
            new Component<>(
                    () -> configuration,
                    "defaultStreamableMessageSource",
                    c -> (StreamableMessageSource<TrackedEventMessage<?>>) c.eventBus()
            );
    private Component<SubscribableMessageSource<? extends EventMessage<?>>> defaultSubscribableSource =
            new Component<>(
                    () -> configuration,
                    "defaultSubscribableMessageSource",
                    Configuration::eventBus
            );
    private final Component<TrackingEventProcessorConfiguration> defaultTrackingEventProcessorConfiguration =
            new Component<>(
                    () -> configuration,
                    "trackingEventProcessorConfiguration",
                    c -> c.getComponent(
                            TrackingEventProcessorConfiguration.class,
                            TrackingEventProcessorConfiguration::forSingleThreadedProcessing
                    )
            );
    private EventProcessorBuilder defaultEventProcessorBuilder = this::defaultEventProcessor;
    private Function<String, String> defaultProcessingGroupAssignment = Function.identity();

    @Override
    public void initialize(Configuration configuration) {
        this.configuration = configuration;
        eventProcessors.clear();

        instanceSelectors.sort(comparing(InstanceProcessingGroupSelector::getPriority).reversed());

        Map<String, List<Function<Configuration, EventHandlerInvoker>>> handlerInvokers = new HashMap<>();
        registerSimpleEventHandlerInvokers(handlerInvokers);
        registerSagaManagers(handlerInvokers);

        handlerInvokers.forEach((processorName, invokers) -> {
            Component<EventProcessor> eventProcessorComponent =
                    new Component<>(configuration, processorName, c -> buildEventProcessor(invokers, processorName));
            eventProcessors.put(processorName, eventProcessorComponent);
        });

        initializeProcessors();
    }

    /**
     * Ideally we would be able to just call {@code eventProcessors.values().forEach(Component::get)} to have the {@link
     * Component} register the lifecycle handlers through the {@link LifecycleHandlerInspector} directly upon
     * initialization. However, the Spring {@code AxonConfiguration} and {@code EventHandlerRegistrar} will call the
     * {@link #initialize(Configuration)} method twice. As the {@code #initialize(Configuration)} clears out the list of
     * processors, some processors (for example those for registered Sagas) might pop up twice; once in the first {@code
     * #initialize(Configuration)} call and once in the second. Registering the {@code
     * eventProcessors.values().forEach(Component::get)} at the earliest stage in the start cycle will resolve the
     * problem. Note that this functionality should be adjusted once the Spring configuration is more inline with the
     * default and auto Configuration.
     */
    private void initializeProcessors() {
        this.configuration.onStart(Integer.MIN_VALUE, () -> eventProcessors.values().forEach(Component::get));
    }

    private String selectProcessingGroupByType(Class<?> type) {
        return typeSelectors.stream()
                            .map(s -> s.select(type))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .findFirst()
                            .orElseGet(() -> defaultTypeSelector.select(type)
                                                                .orElseThrow(IllegalStateException::new));
    }

    private void registerSimpleEventHandlerInvokers(
            Map<String, List<Function<Configuration, EventHandlerInvoker>>> handlerInvokers) {
        Map<String, List<Object>> assignments = new HashMap<>();
        eventHandlerBuilders.stream()
                            .map(Component::get)
                            .forEach(handler -> {
                                String processingGroup =
                                        instanceSelectors.stream()
                                                         .map(s -> s.select(handler))
                                                         .filter(Optional::isPresent)
                                                         .map(Optional::get)
                                                         .findFirst()
                                                         .orElse(defaultInstanceSelector.select(handler)
                                                                                        .orElse(selectProcessingGroupByType(
                                                                                                handler.getClass())));
                                assignments.computeIfAbsent(processingGroup, k -> new ArrayList<>())
                                           .add(handler);
                            });
        assignments.forEach((processingGroup, handlers) -> {
            String processorName = processorNameForProcessingGroup(processingGroup);
            handlerInvokers.computeIfAbsent(processorName, k -> new ArrayList<>()).add(
                    c -> SimpleEventHandlerInvoker.builder()
                                                  .eventHandlers(handlers)
                                                  .handlerDefinition(retrieveHandlerDefinition(handlers))
                                                  .parameterResolverFactory(configuration.parameterResolverFactory())
                                                  .listenerInvocationErrorHandler(listenerInvocationErrorHandler(
                                                          processingGroup
                                                  ))
                                                  .sequencingPolicy(sequencingPolicy(processingGroup))
                                                  .build()
            );
        });
    }

    /**
     * The class is required to be provided in case the {@code ClasspathHandlerDefinition is used to retrieve the {@link
     * HandlerDefinition}. Ideally, a {@code HandlerDefinition} would be retrieved per event handling class, as
     * potentially users would be able to define different {@link ClassLoader} instances per event handling class
     * contained in an Event Processor. For now we have deduced the latter to be to much of an edge case. Hence we
     * assume users will use the same ClassLoader for differing event handling instance within a single Event
     * Processor.
     */
    private HandlerDefinition retrieveHandlerDefinition(List<Object> handlers) {
        return configuration.handlerDefinition(handlers.get(0).getClass());
    }

    private void registerSagaManagers(Map<String, List<Function<Configuration, EventHandlerInvoker>>> handlerInvokers) {
        sagaConfigurations.forEach(sc -> {
            SagaConfiguration<?> sagaConfig = sc.initialize(configuration);
            String processingGroup = selectProcessingGroupByType(sagaConfig.type());
            String processorName = processorNameForProcessingGroup(processingGroup);
            handlerInvokers.computeIfAbsent(processorName, k -> new ArrayList<>())
                           .add(c -> sagaConfig.manager());
        });
    }

    private EventProcessor buildEventProcessor(List<Function<Configuration, EventHandlerInvoker>> builderFunctions,
                                               String processorName) {
        List<EventHandlerInvoker> invokers = builderFunctions
                .stream()
                .map(invokerBuilder -> invokerBuilder.apply(configuration))
                .collect(Collectors.toList());
        MultiEventHandlerInvoker multiEventHandlerInvoker = new MultiEventHandlerInvoker(invokers);

        EventProcessor eventProcessor = eventProcessorBuilders
                .getOrDefault(processorName, defaultEventProcessorBuilder)
                .build(processorName, configuration, multiEventHandlerInvoker);

        handlerInterceptorsBuilders.getOrDefault(processorName, new ArrayList<>())
                                   .stream()
                                   .map(hi -> hi.apply(configuration))
                                   .forEach(eventProcessor::registerHandlerInterceptor);

        defaultHandlerInterceptors.stream()
                                  .map(f -> f.apply(configuration, processorName))
                                  .filter(Objects::nonNull)
                                  .forEach(eventProcessor::registerHandlerInterceptor);

        eventProcessor.registerHandlerInterceptor(
                new CorrelationDataInterceptor<>(configuration.correlationDataProviders())
        );

        return eventProcessor;
    }

    //<editor-fold desc="configuration methods">
    @SuppressWarnings("unchecked")
    @Override
    public <T extends EventProcessor> Optional<T> eventProcessorByProcessingGroup(String processingGroup) {
        ensureInitialized();
        return Optional.ofNullable((T) eventProcessors().get(processorNameForProcessingGroup(processingGroup)));
    }

    @Override
    public Map<String, EventProcessor> eventProcessors() {
        ensureInitialized();
        Map<String, EventProcessor> result = new HashMap<>(eventProcessors.size());
        eventProcessors.forEach((name, component) -> result.put(name, component.get()));
        return result;
    }

    @Override
    public String sagaProcessingGroup(Class<?> sagaType) {
        return selectProcessingGroupByType(sagaType);
    }
    //</editor-fold>

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor(String processorName) {
        ensureInitialized();
        return eventProcessor(processorName).map(EventProcessor::getHandlerInterceptors)
                                            .orElse(Collections.emptyList());
    }

    @Override
    public ListenerInvocationErrorHandler listenerInvocationErrorHandler(String processingGroup) {
        ensureInitialized();
        return listenerInvocationErrorHandlers.containsKey(processingGroup)
                ? listenerInvocationErrorHandlers.get(processingGroup).get()
                : defaultListenerInvocationErrorHandler.get();
    }

    @Override
    public SequencingPolicy<? super EventMessage<?>> sequencingPolicy(String processingGroup) {
        ensureInitialized();
        return sequencingPolicies.containsKey(processingGroup)
                ? sequencingPolicies.get(processingGroup).get()
                : defaultSequencingPolicy.get();
    }

    @Override
    public RollbackConfiguration rollbackConfiguration(String componentName) {
        ensureInitialized();
        return rollbackConfigurations.containsKey(componentName)
                ? rollbackConfigurations.get(componentName).get()
                : defaultRollbackConfiguration.get();
    }

    @Override
    public ErrorHandler errorHandler(String componentName) {
        ensureInitialized();
        return errorHandlers.containsKey(componentName)
                ? errorHandlers.get(componentName).get()
                : defaultErrorHandler.get();
    }

    @Override
    public SagaStore sagaStore() {
        ensureInitialized();
        return sagaStore.get();
    }

    @Override
    public List<SagaConfiguration<?>> sagaConfigurations() {
        ensureInitialized();
        return sagaConfigurations.stream().map(sc -> sc.initialize(configuration)).collect(Collectors.toList());
    }

    private String processorNameForProcessingGroup(String processingGroup) {
        ensureInitialized();
        return processingGroupsAssignments.getOrDefault(processingGroup,
                                                        defaultProcessingGroupAssignment
                                                                .apply(processingGroup));
    }

    @Override
    public MessageMonitor<? super Message<?>> messageMonitor(Class<?> componentType,
                                                             String eventProcessorName) {
        ensureInitialized();
        if (messageMonitorFactories.containsKey(eventProcessorName)) {
            return messageMonitorFactories.get(eventProcessorName).create(configuration,
                                                                          componentType,
                                                                          eventProcessorName);
        } else {
            return configuration.messageMonitor(componentType, eventProcessorName);
        }
    }

    @Override
    public TokenStore tokenStore(String processorName) {
        ensureInitialized();
        return tokenStore.containsKey(processorName)
                ? tokenStore.get(processorName).get()
                : defaultTokenStore.get();
    }

    @Override
    public TransactionManager transactionManager(String processingGroup) {
        ensureInitialized();
        return transactionManagers.containsKey(processingGroup)
                ? transactionManagers.get(processingGroup).get()
                : defaultTransactionManager.get();
    }

    private void ensureInitialized() {
        assertNonNull(configuration, "Configuration is not initialized yet");
    }

    //<editor-fold desc="configurer methods">

    @Override
    public <T> EventProcessingConfigurer registerSaga(Class<T> sagaType, Consumer<SagaConfigurer<T>> sagaConfigurer) {
        SagaConfigurer<T> configurer = SagaConfigurer.forType(sagaType);
        sagaConfigurer.accept(configurer);
        this.sagaConfigurations.add(configurer);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerSagaStore(
            Function<Configuration, SagaStore> sagaStoreBuilder) {
        this.sagaStore.update(sagaStoreBuilder);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerEventHandler(
            Function<Configuration, Object> eventHandlerBuilder) {
        this.eventHandlerBuilders.add(new Component<>(() -> configuration,
                                                      "eventHandler",
                                                      eventHandlerBuilder));
        return this;
    }

    //</editor-fold>

    @Override
    public EventProcessingConfigurer registerDefaultListenerInvocationErrorHandler(
            Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder) {
        defaultListenerInvocationErrorHandler.update(listenerInvocationErrorHandlerBuilder);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerListenerInvocationErrorHandler(String processingGroup,
                                                                            Function<Configuration, ListenerInvocationErrorHandler> listenerInvocationErrorHandlerBuilder) {
        listenerInvocationErrorHandlers.put(processingGroup, new Component<>(() -> configuration,
                                                                             "listenerInvocationErrorHandler",
                                                                             listenerInvocationErrorHandlerBuilder));
        return this;
    }

    @Override
    public EventProcessingConfigurer configureDefaultStreamableMessageSource(Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> defaultSource) {
        this.defaultStreamableSource.update(defaultSource);
        return this;
    }

    @Override
    public EventProcessingConfigurer configureDefaultSubscribableMessageSource(Function<Configuration, SubscribableMessageSource<EventMessage<?>>> defaultSource) {
        this.defaultSubscribableSource.update(defaultSource);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerTrackingEventProcessor(String name,
                                                                    Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source) {
        return registerTrackingEventProcessor(name, source, c -> defaultTrackingEventProcessorConfiguration.get());
    }

    @Override
    public EventProcessingConfigurer registerTrackingEventProcessor(String name,
                                                                    Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source,
                                                                    Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration) {
        registerEventProcessor(
                name,
                (n, c, ehi) -> trackingEventProcessor(n, ehi, processorConfiguration.apply(c), source.apply(c))
        );
        return this;
    }

    @Override
    public EventProcessingConfigurer registerEventProcessorFactory(
            EventProcessorBuilder eventProcessorBuilder) {
        this.defaultEventProcessorBuilder = eventProcessorBuilder;
        return this;
    }

    @Override
    public EventProcessingConfigurer registerEventProcessor(String name,
                                                            EventProcessorBuilder eventProcessorBuilder) {
        if (this.eventProcessorBuilders.containsKey(name)) {
            throw new AxonConfigurationException(format("Event processor with name %s already exists", name));
        }
        this.eventProcessorBuilders.put(name, eventProcessorBuilder);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerTokenStore(String processingGroup,
                                                        Function<Configuration, TokenStore> tokenStore) {
        this.tokenStore.put(processingGroup, new Component<>(() -> configuration,
                                                             "tokenStore",
                                                             tokenStore));
        return this;
    }

    @Override
    public EventProcessingConfigurer registerTokenStore(Function<Configuration, TokenStore> tokenStore) {
        this.defaultTokenStore.update(tokenStore);
        return this;
    }

    @Override
    public EventProcessingConfigurer usingSubscribingEventProcessors() {
        this.defaultEventProcessorBuilder = (name, conf, eventHandlerInvoker) ->
                subscribingEventProcessor(name, eventHandlerInvoker, defaultSubscribableSource.get());
        return this;
    }

    @Override
    public EventProcessingConfigurer usingTrackingEventProcessors() {
        this.defaultEventProcessorBuilder = (name, conf, eventHandlerInvoker) ->
                trackingEventProcessor(name, eventHandlerInvoker, defaultTrackingEventProcessorConfiguration.get(),
                                       defaultStreamableSource.get());
        return this;
    }

    @Override
    public EventProcessingConfigurer registerSubscribingEventProcessor(String name,
                                                                       Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
        registerEventProcessor(name, (n, c, ehi) -> subscribingEventProcessor(n, ehi, messageSource.apply(c)));
        return this;
    }

    @Override
    public EventProcessingConfigurer registerDefaultErrorHandler(
            Function<Configuration, ErrorHandler> errorHandlerBuilder) {
        this.defaultErrorHandler.update(errorHandlerBuilder);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerErrorHandler(String eventProcessorName,
                                                          Function<Configuration, ErrorHandler> errorHandlerBuilder) {
        this.errorHandlers.put(eventProcessorName, new Component<>(() -> configuration,
                                                                   "errorHandler",
                                                                   errorHandlerBuilder));
        return this;
    }

    @Override
    public EventProcessingConfigurer byDefaultAssignHandlerInstancesTo(Function<Object, String> assignmentFunction) {
        this.instanceFallback = assignmentFunction;
        return this;
    }

    @Override
    public EventProcessingConfigurer byDefaultAssignHandlerTypesTo(Function<Class<?>, String> assignmentFunction) {
        this.typeFallback = assignmentFunction;
        return this;
    }

    @Override
    public EventProcessingConfigurer assignHandlerInstancesMatching(String processingGroup, int priority,
                                                                    Predicate<Object> criteria) {
        this.instanceSelectors.add(new InstanceProcessingGroupSelector(processingGroup, priority, criteria));
        return this;
    }

    @Override
    public EventProcessingConfigurer assignHandlerTypesMatching(String processingGroup, int priority,
                                                                Predicate<Class<?>> criteria) {
        this.typeSelectors.add(new TypeProcessingGroupSelector(processingGroup, priority, criteria));
        return this;
    }

    @Override
    public EventProcessingConfigurer assignProcessingGroup(String processingGroup, String processorName) {
        this.processingGroupsAssignments.put(processingGroup, processorName);
        return this;
    }

    @Override
    public EventProcessingConfigurer assignProcessingGroup(Function<String, String> assignmentRule) {
        this.defaultProcessingGroupAssignment = assignmentRule;
        return this;
    }

    @Override
    public EventProcessingConfigurer registerHandlerInterceptor(String processorName,
                                                                Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder) {
        if (configuration != null) {
            Component<EventProcessor> eps = eventProcessors.get(processorName);
            if (eps != null && eps.isInitialized()) {
                eps.get().registerHandlerInterceptor(interceptorBuilder.apply(configuration));
            }
        }
        this.handlerInterceptorsBuilders.computeIfAbsent(processorName, k -> new ArrayList<>())
                                        .add(interceptorBuilder);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerDefaultHandlerInterceptor(
            BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder) {
        this.defaultHandlerInterceptors.add(interceptorBuilder);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerSequencingPolicy(String processingGroup,
                                                              Function<Configuration, SequencingPolicy<? super EventMessage<?>>> policyBuilder) {
        this.sequencingPolicies.put(processingGroup, new Component<>(() -> configuration,
                                                                     "sequencingPolicy",
                                                                     policyBuilder));
        return this;
    }

    @Override
    public EventProcessingConfigurer registerDefaultSequencingPolicy(
            Function<Configuration, SequencingPolicy<? super EventMessage<?>>> policyBuilder) {
        this.defaultSequencingPolicy.update(policyBuilder);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerMessageMonitorFactory(String eventProcessorName,
                                                                   MessageMonitorFactory messageMonitorFactory) {
        this.messageMonitorFactories.put(eventProcessorName, messageMonitorFactory);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerRollbackConfiguration(String name,
                                                                   Function<Configuration, RollbackConfiguration> rollbackConfigurationBuilder) {
        this.rollbackConfigurations.put(name, new Component<>(() -> configuration,
                                                              "rollbackConfiguration",
                                                              rollbackConfigurationBuilder));
        return this;
    }

    @Override
    public EventProcessingConfigurer registerTransactionManager(String name,
                                                                Function<Configuration, TransactionManager> transactionManagerBuilder) {
        this.transactionManagers.put(name, new Component<>(() -> configuration,
                                                           "transactionManager",
                                                           transactionManagerBuilder));
        return this;
    }

    @Override
    public EventProcessingConfigurer registerTrackingEventProcessorConfiguration(
            Function<Configuration, TrackingEventProcessorConfiguration> trackingEventProcessorConfigurationBuilder) {
        this.defaultTrackingEventProcessorConfiguration.update(trackingEventProcessorConfigurationBuilder);
        return this;
    }

    private EventProcessor defaultEventProcessor(String name,
                                                 Configuration conf,
                                                 EventHandlerInvoker eventHandlerInvoker) {
        if (conf.eventBus() instanceof StreamableMessageSource) {
            return trackingEventProcessor(
                    name,
                    eventHandlerInvoker,
                    defaultTrackingEventProcessorConfiguration.get(),
                    defaultStreamableSource.get()
            );
        } else {
            return subscribingEventProcessor(name, eventHandlerInvoker, defaultSubscribableSource.get());
        }
    }

    private SubscribingEventProcessor subscribingEventProcessor(String name,
                                                                EventHandlerInvoker eventHandlerInvoker,
                                                                SubscribableMessageSource<? extends EventMessage<?>> messageSource) {
        return SubscribingEventProcessor.builder()
                                        .name(name)
                                        .eventHandlerInvoker(eventHandlerInvoker)
                                        .rollbackConfiguration(rollbackConfiguration(name))
                                        .errorHandler(errorHandler(name))
                                        .messageMonitor(messageMonitor(SubscribingEventProcessor.class, name))
                                        .messageSource(messageSource)
                                        .processingStrategy(DirectEventProcessingStrategy.INSTANCE)
                                        .transactionManager(transactionManager(name))
                                        .build();
    }

    private TrackingEventProcessor trackingEventProcessor(String name,
                                                          EventHandlerInvoker eventHandlerInvoker,
                                                          TrackingEventProcessorConfiguration config,
                                                          StreamableMessageSource<TrackedEventMessage<?>> source) {
        return TrackingEventProcessor.builder()
                                     .name(name)
                                     .eventHandlerInvoker(eventHandlerInvoker)
                                     .rollbackConfiguration(rollbackConfiguration(name))
                                     .errorHandler(errorHandler(name))
                                     .messageMonitor(messageMonitor(TrackingEventProcessor.class, name))
                                     .messageSource(source)
                                     .tokenStore(tokenStore(name))
                                     .transactionManager(transactionManager(name))
                                     .trackingEventProcessorConfiguration(config)
                                     .build();
    }

    /**
     * Gets the package name from the class of the given object.
     * <p>
     * Since class.getPackage() can be null e.g. for generated classes, the
     * package name is determined the old fashioned way based on the full
     * qualified class name.
     * 
     * @param object
     *            {@link Object}
     * @return {@link String}
     */
    protected static String packageOfObject(Object object) {
        return object.getClass().getName().replace("." + object.getClass().getSimpleName(), "");
    }

    private static Optional<String> annotatedProcessingGroupOfType(Class<?> type) {
        Optional<Map<String, Object>> annAttr = findAnnotationAttributes(type, ProcessingGroup.class);
        return annAttr.map(attr -> (String) attr.get("processingGroup"));
    }

    //<editor-fold desc="configuration state">
    private static class InstanceProcessingGroupSelector extends ProcessingGroupSelector<Object> {

        private static InstanceProcessingGroupSelector defaultSelector(Function<Object, String> selectorFunction) {
            return new InstanceProcessingGroupSelector(Integer.MIN_VALUE, selectorFunction.andThen(Optional::of));
        }

        private InstanceProcessingGroupSelector(int priority, Function<Object, Optional<String>> selectorFunction) {
            super(priority, selectorFunction);
        }

        private InstanceProcessingGroupSelector(String name, int priority, Predicate<Object> criteria) {
            super(name, priority, criteria);
        }
    }

    private static class TypeProcessingGroupSelector extends ProcessingGroupSelector<Class<?>> {

        private static TypeProcessingGroupSelector defaultSelector(Function<Class<?>, String> selectorFunction) {
            return new TypeProcessingGroupSelector(Integer.MIN_VALUE, selectorFunction.andThen(Optional::of));
        }

        private TypeProcessingGroupSelector(int priority, Function<Class<?>, Optional<String>> selectorFunction) {
            super(priority, selectorFunction);
        }

        private TypeProcessingGroupSelector(String name, int priority, Predicate<Class<?>> criteria) {
            super(name, priority, criteria);
        }
    }

    private static class ProcessingGroupSelector<T> {

        private final int priority;
        private final Function<T, Optional<String>> function;

        private ProcessingGroupSelector(int priority, Function<T, Optional<String>> selectorFunction) {
            this.priority = priority;
            this.function = selectorFunction;
        }

        private ProcessingGroupSelector(String name, int priority, Predicate<T> criteria) {
            this(priority, handler -> {
                if (criteria.test(handler)) {
                    return Optional.of(name);
                }
                return Optional.empty();
            });
        }

        public Optional<String> select(T handler) {
            return function.apply(handler);
        }

        public int getPriority() {
            return priority;
        }
    }
    //</editor-fold>
}
