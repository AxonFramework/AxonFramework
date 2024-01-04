/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DirectEventProcessingStrategy;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
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
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorSupport;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.ThrowableCause;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;
import static org.axonframework.config.EventProcessingConfigurer.PooledStreamingProcessorConfiguration.noOp;

/**
 * Event processing module configuration. Registers all configuration components within itself, builds the {@link
 * EventProcessingConfiguration} and takes care of module lifecycle.
 *
 * @author Milan Savic
 * @since 4.0
 */
public class EventProcessingModule
        implements ModuleConfiguration, EventProcessingConfiguration, EventProcessingConfigurer {

    private static final String CONFIGURED_DEFAULT_TEP_CONFIG = "___DEFAULT_TEP_CONFIG";
    private static final TrackingEventProcessorConfiguration DEFAULT_TEP_CONFIG =
            TrackingEventProcessorConfiguration.forSingleThreadedProcessing();
    private static final TrackingEventProcessorConfiguration DEFAULT_SAGA_TEP_CONFIG =
            DEFAULT_TEP_CONFIG.andInitialTrackingToken(StreamableMessageSource::createHeadToken);
    private static final String CONFIGURED_DEFAULT_PSEP_CONFIG = "___DEFAULT_PSEP_CONFIG";
    private static final PooledStreamingProcessorConfiguration DEFAULT_SAGA_PSEP_CONFIG =
            (config, builder) -> builder.initialToken(StreamableMessageSource::createHeadToken);
    private static final Function<Class<?>, String> DEFAULT_SAGA_PROCESSING_GROUP_FUNCTION =
            c -> c.getSimpleName() + "Processor";

    private final List<TypeProcessingGroupSelector> typeSelectors = new ArrayList<>();
    private final List<InstanceProcessingGroupSelector> instanceSelectors = new ArrayList<>();
    private final Map<String, String> processingGroupsAssignments = new HashMap<>();
    // the default selector determines the processing group by inspecting the @ProcessingGroup annotation
    private final TypeProcessingGroupSelector annotationGroupSelector = TypeProcessingGroupSelector
            .defaultSelector(type -> annotatedProcessingGroupOfType(type).orElse(null));
    private TypeProcessingGroupSelector typeFallback =
            TypeProcessingGroupSelector.defaultSelector(DEFAULT_SAGA_PROCESSING_GROUP_FUNCTION);
    private InstanceProcessingGroupSelector instanceFallbackSelector = InstanceProcessingGroupSelector.defaultSelector(EventProcessingModule::packageOfObject);

    private final Map<String, SagaConfigurer<?>> sagaConfigurations = new HashMap<>();
    private final List<Component<Object>> eventHandlerBuilders = new ArrayList<>();
    private final Map<String, EventProcessorBuilder> eventProcessorBuilders = new HashMap<>();

    protected final Map<String, Component<EventProcessor>> eventProcessors = new HashMap<>();
    protected final Map<String, DeadLetteringEventHandlerInvoker> deadLetteringEventHandlerInvokers = new HashMap<>();

    protected final List<BiFunction<Configuration, String, MessageHandlerInterceptor<? super EventMessage<?>>>> defaultHandlerInterceptors = new ArrayList<>();
    protected final Map<String, List<Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>>>> handlerInterceptorsBuilders = new HashMap<>();
    protected final Map<String, Component<ListenerInvocationErrorHandler>> listenerInvocationErrorHandlers = new HashMap<>();
    protected final Map<String, Component<ErrorHandler>> errorHandlers = new HashMap<>();
    protected final Map<String, Component<SequencingPolicy<? super EventMessage<?>>>> sequencingPolicies = new HashMap<>();
    protected final Map<String, MessageMonitorFactory> messageMonitorFactories = new HashMap<>();
    protected final Map<String, Component<TokenStore>> tokenStore = new HashMap<>();
    protected final Map<String, Component<RollbackConfiguration>> rollbackConfigurations = new HashMap<>();
    protected final Map<String, Component<TransactionManager>> transactionManagers = new HashMap<>();
    protected final Map<String, Component<SequencedDeadLetterQueue<EventMessage<?>>>> deadLetterQueues = new HashMap<>();
    protected final Map<String, Component<EnqueuePolicy<EventMessage<?>>>> deadLetterPolicies = new HashMap<>();

    protected final Map<String, Component<TrackingEventProcessorConfiguration>> tepConfigs = new HashMap<>();
    protected final Map<String, PooledStreamingProcessorConfiguration> psepConfigs = new HashMap<>();
    protected final Map<String, DeadLetteringInvokerConfiguration> deadLetteringInvokerConfigs = new HashMap<>();
    protected Function<String, Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>>> deadLetterQueueProvider = processingGroup -> null;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    protected Configuration configuration;

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
    @SuppressWarnings({"unchecked", "rawtypes"})
    private final Component<EnqueuePolicy<EventMessage<?>>> defaultDeadLetterPolicy = new Component<>(
            () -> configuration, "deadLetterPolicy",
            c -> c.getComponent(EnqueuePolicy.class,
                                () -> (letter, cause) -> Decisions.enqueue(ThrowableCause.truncated(cause))
            )
    );
    @SuppressWarnings("unchecked")
    private final Component<StreamableMessageSource<TrackedEventMessage<?>>> defaultStreamableSource =
            new Component<>(
                    () -> configuration,
                    "defaultStreamableMessageSource",
                    c -> (StreamableMessageSource<TrackedEventMessage<?>>) c.eventBus()
            );
    private final Component<SubscribableMessageSource<? extends EventMessage<?>>> defaultSubscribableSource =
            new Component<>(
                    () -> configuration,
                    "defaultSubscribableMessageSource",
                    Configuration::eventBus
            );
    private final Component<TrackingEventProcessorConfiguration> defaultTepConfig =
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
        configuration.onStart(Integer.MIN_VALUE, this::initializeProcessors);
    }

    /**
     * Initializes the event processors by assigning each of the event handlers according to the defined selectors. When
     * processors have already been initialized, this method does nothing.
     */
    private void initializeProcessors() {
        if (initialized.get()) {
            return;
        }

        synchronized (initialized) {
            if (initialized.get()) {
                return;
            }

            instanceSelectors.sort(comparing(InstanceProcessingGroupSelector::getPriority).reversed());

            Map<String, List<Function<Configuration, EventHandlerInvoker>>> handlerInvokers = new HashMap<>();
            registerEventHandlerInvokers(handlerInvokers);
            registerSagaManagers(handlerInvokers);

            handlerInvokers.forEach((processorName, invokers) -> {
                Component<EventProcessor> eventProcessorComponent = new Component<>(
                        configuration, processorName, c -> buildEventProcessor(invokers, processorName)
                );
                eventProcessors.put(processorName, eventProcessorComponent);
            });

            eventProcessors.values().forEach(Component::get);
            initialized.set(true);
        }
    }

    private String selectProcessingGroupByType(Class<?> type) {
        // when selecting on type,
        List<TypeProcessingGroupSelector> selectors = new ArrayList<>(typeSelectors);
        selectors.add(annotationGroupSelector);
        selectors.add(typeFallback);

        return selectors.stream()
                        .map(s -> s.select(type))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Could not select a processing group for type [" + type.getSimpleName() + "]"
                        ));
    }

    private void registerEventHandlerInvokers(
            Map<String, List<Function<Configuration, EventHandlerInvoker>>> handlerInvokers
    ) {
        Map<String, List<Object>> assignments = new HashMap<>();

        // we combine the selectors in the order of precedence (instances, then types, then default instance, default types and fallbacks)
        List<InstanceProcessingGroupSelector> selectors = new ArrayList<>(instanceSelectors);
        typeSelectors.stream().map(InstanceToTypeProcessingGroupSelectorAdapter::new).forEach(selectors::add);
        selectors.add(new InstanceToTypeProcessingGroupSelectorAdapter(annotationGroupSelector));
        selectors.add(instanceFallbackSelector);

        eventHandlerBuilders.stream()
                            .map(Component::get)
                            .forEach(handler -> {
                                String processingGroup =
                                        selectors.stream()
                                                 .map(s -> s.select(handler))
                                                 .filter(Optional::isPresent)
                                                 .map(Optional::get)
                                                 .findFirst()
                                                 .orElseThrow(() -> new IllegalStateException(
                                                         "Could not select a processing group for handler ["
                                                                 + handler.getClass().getSimpleName() + "]"
                                                 ));
                                assignments.computeIfAbsent(processingGroup, k -> new ArrayList<>())
                                           .add(handler);
                            });
        assignments.forEach((processingGroup, handlers) -> {
            String processorName = processorNameForProcessingGroup(processingGroup);
            if (!deadLetterQueues.containsKey(processingGroup)) {
                registerDefaultDeadLetterQueueIfPresent(processingGroup);
            }
            handlerInvokers.computeIfAbsent(processorName, k -> new ArrayList<>()).add(
                    c -> !deadLetterQueues.containsKey(processingGroup)
                            ? simpleInvoker(processingGroup, handlers)
                            : deadLetteringInvoker(processorName, processingGroup, handlers)
            );
        });
    }

    private void registerDefaultDeadLetterQueueIfPresent(String processingGroup) {
        Optional.ofNullable(deadLetterQueueProvider.apply(processingGroup))
                .ifPresent(deadLetterQueueFunction ->
                                   registerDeadLetterQueue(processingGroup, deadLetterQueueFunction));
    }

    private SimpleEventHandlerInvoker simpleInvoker(String processingGroup, List<Object> handlers) {
        return SimpleEventHandlerInvoker.builder()
                                        .eventHandlers(handlers)
                                        .handlerDefinition(retrieveHandlerDefinition(handlers))
                                        .parameterResolverFactory(configuration.parameterResolverFactory())
                                        .listenerInvocationErrorHandler(listenerInvocationErrorHandler(processingGroup))
                                        .sequencingPolicy(sequencingPolicy(processingGroup))
                                        .build();
    }

    private DeadLetteringEventHandlerInvoker deadLetteringInvoker(String processorName,
                                                                  String processingGroup,
                                                                  List<Object> handlers) {
        SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue =
                deadLetterQueue(processingGroup).orElseThrow(() -> new IllegalStateException(
                        "Cannot find a Dead Letter Queue for processing group [" + processingGroup + "]."
                ));
        EnqueuePolicy<EventMessage<?>> enqueuePolicy =
                deadLetterPolicy(processingGroup).orElseThrow(() -> new IllegalStateException(
                        "Cannot find a Dead Letter Policy for processing group [" + processingGroup + "]."
                ));
        DeadLetteringEventHandlerInvoker.Builder builder =
                DeadLetteringEventHandlerInvoker.builder()
                                                .eventHandlers(handlers)
                                                .handlerDefinition(retrieveHandlerDefinition(handlers))
                                                .parameterResolverFactory(configuration.parameterResolverFactory())
                                                .sequencingPolicy(sequencingPolicy(processingGroup))
                                                .queue(deadLetterQueue)
                                                .enqueuePolicy(enqueuePolicy)
                                                .transactionManager(transactionManager(processorName));
        DeadLetteringEventHandlerInvoker deadLetteringInvoker =
                deadLetteringInvokerConfigs.getOrDefault(processingGroup, DeadLetteringInvokerConfiguration.noOp())
                                           .apply(configuration, builder)
                                           .build();
        addInterceptors(processorName, deadLetteringInvoker);
        deadLetteringEventHandlerInvokers.put(processingGroup, deadLetteringInvoker);
        return deadLetteringInvoker;
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
        sagaConfigurations.values().forEach(sc -> {
            SagaConfiguration<?> sagaConfig = sc.initialize(configuration);
            String processingGroup = selectProcessingGroupByType(sagaConfig.type());
            String processorName = processorNameForProcessingGroup(processingGroup);
            // The Event Processor type is unknown as it is not build yet, so we check for both TEP or PSEP configs.
            if (noTepCustomization(processorName)) {
                registerTrackingEventProcessorConfiguration(processorName, config -> DEFAULT_SAGA_TEP_CONFIG);
            }
            if (noPsepCustomization(processorName)) {
                registerPooledStreamingEventProcessorConfiguration(processorName, DEFAULT_SAGA_PSEP_CONFIG);
            }

            handlerInvokers.computeIfAbsent(processorName, k -> new ArrayList<>())
                           .add(c -> sagaConfig.manager());
        });
    }

    private boolean noTepCustomization(String processorName) {
        return !eventProcessorBuilders.containsKey(processorName)
                && !tepConfigs.containsKey(processorName)
                && !tepConfigs.containsKey(CONFIGURED_DEFAULT_TEP_CONFIG);
    }

    private boolean noPsepCustomization(String processorName) {
        return !eventProcessorBuilders.containsKey(processorName)
                && !psepConfigs.containsKey(processorName)
                && !psepConfigs.containsKey(CONFIGURED_DEFAULT_PSEP_CONFIG);
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

        addInterceptors(processorName, eventProcessor);

        return eventProcessor;
    }

    private void addInterceptors(String processorName, MessageHandlerInterceptorSupport<EventMessage<?>> processor){
        handlerInterceptorsBuilders.getOrDefault(processorName, new ArrayList<>())
                                   .stream()
                                   .map(hi -> hi.apply(configuration))
                                   .forEach(processor::registerHandlerInterceptor);

        defaultHandlerInterceptors.stream()
                                  .map(f -> f.apply(configuration, processorName))
                                  .filter(Objects::nonNull)
                                  .forEach(processor::registerHandlerInterceptor);

        processor.registerHandlerInterceptor(
                new CorrelationDataInterceptor<>(configuration.correlationDataProviders())
        );
    }

    //<editor-fold desc="configuration methods">
    @SuppressWarnings("unchecked")
    @Override
    public <T extends EventProcessor> Optional<T> eventProcessorByProcessingGroup(String processingGroup) {
        return Optional.ofNullable((T) eventProcessors().get(processorNameForProcessingGroup(processingGroup)));
    }

    @Override
    public Map<String, EventProcessor> eventProcessors() {
        validateConfigInitialization();
        initializeProcessors();
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
        validateConfigInitialization();
        return eventProcessor(processorName).map(EventProcessor::getHandlerInterceptors)
                                            .orElse(Collections.emptyList());
    }

    @Override
    public ListenerInvocationErrorHandler listenerInvocationErrorHandler(String processingGroup) {
        validateConfigInitialization();
        return listenerInvocationErrorHandlers.containsKey(processingGroup)
                ? listenerInvocationErrorHandlers.get(processingGroup).get()
                : defaultListenerInvocationErrorHandler.get();
    }

    @Override
    public SequencingPolicy<? super EventMessage<?>> sequencingPolicy(String processingGroup) {
        validateConfigInitialization();
        return sequencingPolicies.containsKey(processingGroup)
                ? sequencingPolicies.get(processingGroup).get()
                : defaultSequencingPolicy.get();
    }

    @Override
    public RollbackConfiguration rollbackConfiguration(String processorName) {
        validateConfigInitialization();
        return rollbackConfigurations.containsKey(processorName)
                ? rollbackConfigurations.get(processorName).get()
                : defaultRollbackConfiguration.get();
    }

    @Override
    public ErrorHandler errorHandler(String processorName) {
        validateConfigInitialization();
        return errorHandlers.containsKey(processorName)
                ? errorHandlers.get(processorName).get()
                : defaultErrorHandler.get();
    }

    @Override
    public SagaStore sagaStore() {
        validateConfigInitialization();
        return sagaStore.get();
    }

    @Override
    public List<SagaConfiguration<?>> sagaConfigurations() {
        validateConfigInitialization();
        return sagaConfigurations.values().stream().map(sc -> sc.initialize(configuration))
                                 .collect(Collectors.toList());
    }

    private String processorNameForProcessingGroup(String processingGroup) {
        validateConfigInitialization();
        return processingGroupsAssignments.getOrDefault(processingGroup,
                                                        defaultProcessingGroupAssignment
                                                                .apply(processingGroup));
    }

    @Override
    public MessageMonitor<? super Message<?>> messageMonitor(Class<?> componentType,
                                                             String eventProcessorName) {
        validateConfigInitialization();
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
        validateConfigInitialization();
        return tokenStore.containsKey(processorName)
                ? tokenStore.get(processorName).get()
                : defaultTokenStore.get();
    }

    @Override
    public TransactionManager transactionManager(String processorName) {
        validateConfigInitialization();
        return transactionManagers.containsKey(processorName)
                ? transactionManagers.get(processorName).get()
                : defaultTransactionManager.get();
    }

    @Override
    public Optional<SequencedDeadLetterQueue<EventMessage<?>>> deadLetterQueue(@Nonnull String processingGroup) {
        validateConfigInitialization();
        if (!deadLetterQueues.containsKey(processingGroup)) {
            registerDefaultDeadLetterQueueIfPresent(processingGroup);
        }
        return deadLetterQueues.containsKey(processingGroup)
                ? Optional.ofNullable(deadLetterQueues.get(processingGroup).get()) : Optional.empty();
    }

    @Override
    public Optional<EnqueuePolicy<EventMessage<?>>> deadLetterPolicy(@Nonnull String processingGroup) {
        validateConfigInitialization();
        return deadLetterPolicies.containsKey(processingGroup)
                ? Optional.ofNullable(deadLetterPolicies.get(processingGroup).get())
                : Optional.ofNullable(defaultDeadLetterPolicy.get());
    }

    @Override
    public Optional<SequencedDeadLetterProcessor<EventMessage<?>>> sequencedDeadLetterProcessor(
            @Nonnull String processingGroup
    ) {
        validateConfigInitialization();
        return Optional.ofNullable(deadLetteringEventHandlerInvokers.get(processingGroup));
    }

    private void validateConfigInitialization() {
        assertNonNull(
                configuration, "Cannot proceed because the Configuration is not initialized for this module yet."
        );
    }

    //<editor-fold desc="configurer methods">

    @Override
    public <T> EventProcessingConfigurer registerSaga(Class<T> sagaType, Consumer<SagaConfigurer<T>> sagaConfigurer) {
        SagaConfigurer<T> configurer = SagaConfigurer.forType(sagaType);
        sagaConfigurer.accept(configurer);
        this.sagaConfigurations.put(sagaType.getName(), configurer);
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
    public EventProcessingConfigurer registerTrackingEventProcessor(
            String name,
            Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source
    ) {
        return registerTrackingEventProcessor(name, source, c -> trackingEventProcessorConfig(name));
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
    public EventProcessingConfigurer registerTokenStore(String processorName,
                                                        Function<Configuration, TokenStore> tokenStore) {
        this.tokenStore.put(processorName, new Component<>(() -> configuration,
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
        this.defaultEventProcessorBuilder = (name, conf, eventHandlerInvoker) -> trackingEventProcessor(
                name, eventHandlerInvoker, trackingEventProcessorConfig(name), defaultStreamableSource.get()
        );
        return this;
    }

    @Override
    public EventProcessingConfigurer usingPooledStreamingEventProcessors() {
        this.defaultEventProcessorBuilder = (name, conf, eventHandlerInvoker) -> pooledStreamingEventProcessor(
                name, eventHandlerInvoker, conf, defaultStreamableSource.get(), noOp()
        );
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
        this.instanceFallbackSelector = InstanceProcessingGroupSelector.defaultSelector(assignmentFunction);
        return this;
    }

    @Override
    public EventProcessingConfigurer byDefaultAssignHandlerTypesTo(Function<Class<?>, String> assignmentFunction) {
        this.typeFallback = TypeProcessingGroupSelector.defaultSelector(assignmentFunction);
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
        Component<EventProcessor> eps = eventProcessors.get(processorName);
        if (eps != null && eps.isInitialized()) {
            eps.get().registerHandlerInterceptor(interceptorBuilder.apply(configuration));
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
    public EventProcessingConfigurer registerDefaultTransactionManager(
            Function<Configuration, TransactionManager> transactionManagerBuilder
    ) {
        this.defaultTransactionManager.update(transactionManagerBuilder);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerTrackingEventProcessorConfiguration(
            String name,
            Function<Configuration, TrackingEventProcessorConfiguration> trackingEventProcessorConfigurationBuilder
    ) {
        this.tepConfigs.put(name, new Component<>(() -> configuration,
                                                  "trackingEventProcessorConfiguration",
                                                  trackingEventProcessorConfigurationBuilder));
        return this;
    }

    @Override
    public EventProcessingConfigurer registerTrackingEventProcessorConfiguration(
            Function<Configuration, TrackingEventProcessorConfiguration> trackingEventProcessorConfigurationBuilder
    ) {
        this.tepConfigs.put(CONFIGURED_DEFAULT_TEP_CONFIG,
                            new Component<>(() -> configuration,
                                            "trackingEventProcessorConfiguration",
                                            trackingEventProcessorConfigurationBuilder));
        return this;
    }

    @Override
    public EventProcessingConfigurer registerPooledStreamingEventProcessor(
            String name,
            Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> messageSource,
            PooledStreamingProcessorConfiguration processorConfiguration
    ) {
        registerEventProcessor(
                name,
                (n, c, ehi) -> pooledStreamingEventProcessor(n, ehi, c, messageSource.apply(c), processorConfiguration)
        );
        return this;
    }

    @Override
    public EventProcessingConfigurer registerPooledStreamingEventProcessorConfiguration(
            String name,
            PooledStreamingProcessorConfiguration pooledStreamingProcessorConfiguration
    ) {
        psepConfigs.put(name, pooledStreamingProcessorConfiguration);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerDeadLetterQueue(
            @Nonnull String processingGroup,
            @Nonnull Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>> queueBuilder
    ) {
        this.deadLetterQueues.put(
                processingGroup, new Component<>(() -> configuration, "deadLetterQueue", queueBuilder)
        );
        return this;
    }

    @Override
    public EventProcessingConfigurer registerDefaultDeadLetterPolicy(
            @Nonnull Function<Configuration, EnqueuePolicy<EventMessage<?>>> policyBuilder
    ) {
        this.defaultDeadLetterPolicy.update(policyBuilder);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerDeadLetterPolicy(
            @Nonnull String processingGroup,
            @Nonnull Function<Configuration, EnqueuePolicy<EventMessage<?>>> policyBuilder
    ) {
        deadLetterPolicies.put(processingGroup,
                               new Component<>(() -> configuration, "deadLetterPolicy", policyBuilder));
        return this;
    }

    @Override
    public EventProcessingConfigurer registerDeadLetteringEventHandlerInvokerConfiguration(
            @Nonnull String processingGroup,
            @Nonnull DeadLetteringInvokerConfiguration configuration
    ) {
        this.deadLetteringInvokerConfigs.put(processingGroup, configuration);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerPooledStreamingEventProcessorConfiguration(
            PooledStreamingProcessorConfiguration pooledStreamingProcessorConfiguration
    ) {
        this.psepConfigs.put(CONFIGURED_DEFAULT_PSEP_CONFIG, pooledStreamingProcessorConfiguration);
        return this;
    }

    @Override
    public EventProcessingConfigurer registerDeadLetterQueueProvider(
            Function<String, Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>>> deadLetterQueueProvider) {
        this.deadLetterQueueProvider = deadLetterQueueProvider;
        return this;
    }

    private EventProcessor defaultEventProcessor(String name,
                                                 Configuration conf,
                                                 EventHandlerInvoker eventHandlerInvoker) {
        if (conf.eventBus() instanceof StreamableMessageSource) {
            return trackingEventProcessor(
                    name,
                    eventHandlerInvoker,
                    trackingEventProcessorConfig(name),
                    defaultStreamableSource.get()
            );
        } else {
            return subscribingEventProcessor(name, eventHandlerInvoker, defaultSubscribableSource.get());
        }
    }

    private TrackingEventProcessorConfiguration trackingEventProcessorConfig(String name) {
        if (tepConfigs.containsKey(name)) {
            return tepConfigs.get(name).get();
        }
        return tepConfigs.getOrDefault(CONFIGURED_DEFAULT_TEP_CONFIG, defaultTepConfig).get();
    }

    /**
     * Default {@link SubscribingEventProcessor} configuration based on this configure module.
     *
     * @param name of the processor
     * @param eventHandlerInvoker used by the processor for the vent handling
     * @param messageSource where to retrieve events from
     * @return Default {@link SubscribingEventProcessor} configuration based on this configure module.
     */
    protected EventProcessor subscribingEventProcessor(String name,
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
                                        .spanFactory(configuration.getComponent(EventProcessorSpanFactory.class))
                                        .build();
    }

    /**
     * Default {@link TrackingEventProcessor} configuration based on this configure module.
     *
     * @param name of the processor
     * @param eventHandlerInvoker used by the processor for the event handling
     * @param config for the tracking event processor construction
     * @param source where to retrieve events from
     * @return Default {@link TrackingEventProcessor} configuration based on this configure module.
     */
    protected EventProcessor trackingEventProcessor(String name,
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
                                     .spanFactory(configuration.getComponent(EventProcessorSpanFactory.class))
                                     .build();
    }

    /**
     * Default {@link PooledStreamingEventProcessor} configuration based on this configure module.
     *
     * @param name of the processor
     * @param eventHandlerInvoker used by the processor for the event handling
     * @param config main configuration providing access for Axon components
     * @param messageSource where to retrieve events from
     * @param processorConfiguration for the pooled event processor construction
     * @return Default {@link PooledStreamingEventProcessor} configuration based on this configure module.
     */
    protected EventProcessor pooledStreamingEventProcessor(
            String name,
            EventHandlerInvoker eventHandlerInvoker,
            Configuration config,
            StreamableMessageSource<TrackedEventMessage<?>> messageSource,
            PooledStreamingProcessorConfiguration processorConfiguration
    ) {
        PooledStreamingEventProcessor.Builder builder =
                PooledStreamingEventProcessor.builder()
                                             .name(name)
                                             .eventHandlerInvoker(eventHandlerInvoker)
                                             .rollbackConfiguration(rollbackConfiguration(name))
                                             .errorHandler(errorHandler(name))
                                             .messageMonitor(messageMonitor(PooledStreamingEventProcessor.class, name))
                                             .messageSource(messageSource)
                                             .tokenStore(tokenStore(name))
                                             .transactionManager(transactionManager(name))
                                             .coordinatorExecutor(processorName -> {
                                                 ScheduledExecutorService coordinatorExecutor =
                                                         defaultExecutor(1, "Coordinator[" + processorName + "]");
                                                 config.onShutdown(coordinatorExecutor::shutdown);
                                                 return coordinatorExecutor;
                                             })
                                             .workerExecutor(processorName -> {
                                                 ScheduledExecutorService workerExecutor =
                                                         defaultExecutor(4, "WorkPackage[" + processorName + "]");
                                                 config.onShutdown(workerExecutor::shutdown);
                                                 return workerExecutor;
                                             })
                                             .spanFactory(config.getComponent(EventProcessorSpanFactory.class));

        return psepConfigs.getOrDefault(CONFIGURED_DEFAULT_PSEP_CONFIG, noOp())
                          .andThen(psepConfigs.getOrDefault(name, noOp()))
                          .andThen(processorConfiguration)
                          .apply(config, builder)
                          .build();
    }

    private ScheduledExecutorService defaultExecutor(int poolSize, String factoryName) {
        return Executors.newScheduledThreadPool(poolSize, new AxonThreadFactory(factoryName));
    }

    /**
     * Gets the package name from the class of the given object.
     * <p>
     * Since class.getPackage() can be null e.g. for generated classes, the package name is determined the old fashioned
     * way based on the full qualified class name.
     *
     * @param object {@link Object}
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
            return new InstanceProcessingGroupSelector(Integer.MIN_VALUE, selectorFunction.andThen(Optional::ofNullable));
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
            return new TypeProcessingGroupSelector(Integer.MIN_VALUE, selectorFunction.andThen(Optional::ofNullable));
        }

        private TypeProcessingGroupSelector(int priority, Function<Class<?>, Optional<String>> selectorFunction) {
            super(priority, selectorFunction);
        }

        private TypeProcessingGroupSelector(String name, int priority, Predicate<Class<?>> criteria) {
            super(name, priority, criteria);
        }
    }

    private static class InstanceToTypeProcessingGroupSelectorAdapter extends InstanceProcessingGroupSelector {

        private InstanceToTypeProcessingGroupSelectorAdapter(TypeProcessingGroupSelector delegate) {
            super(delegate.getPriority(), i -> delegate.select(i.getClass()));
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
