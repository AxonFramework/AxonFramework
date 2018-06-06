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
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Non-thread safe implementation of {@link EventProcessorRegistry}. After calling {@link #initialize(Configuration)},
 * all registrations (except for {@link MessageHandlerInterceptor}s) are ignored.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class DefaultEventProcessorRegistry implements EventProcessorRegistry {

    private final Map<String, List<Function<Configuration, EventHandlerInvoker>>> invokerBuilders = new HashMap<>();
    private final Map<String, EventProcessorBuilder> eventProcessorBuilders = new HashMap<>();
    private final Map<String, Component<EventProcessor>> eventProcessors = new HashMap<>();
    private final Map<String, List<Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>>>> handlerInterceptorsBuilders = new HashMap<>();
    private final Map<String, Function<Configuration, ErrorHandler>> errorHandlers = new HashMap<>();
    private final Map<String, MessageMonitorFactory> messageMonitorFactories = new HashMap<>();
    private final Map<String, Function<Configuration, TokenStore>> tokenStore = new HashMap<>();
    private EventProcessorBuilder defaultEventProcessorBuilder = this::defaultEventProcessor;
    private Configuration configuration;

    private final Component<ErrorHandler> defaultErrorHandler = new Component<>(
            () -> configuration,
            "errorHandler",
            c -> c.getComponent(ErrorHandler.class, PropagatingErrorHandler::instance)
    );

    @Override
    public EventProcessorRegistry registerHandlerInvoker(String processorName,
                                                         Function<Configuration, EventHandlerInvoker> eventHandlerInvokerBuilder) {
        List<Function<Configuration, EventHandlerInvoker>> invokerBuilderFunctions =
                invokerBuilders.computeIfAbsent(processorName, k -> new ArrayList<>());
        invokerBuilderFunctions.add(eventHandlerInvokerBuilder);
        return this;
    }

    @Override
    public EventProcessorRegistry registerEventProcessor(String name, EventProcessorBuilder eventProcessorBuilder) {
        if (eventProcessorBuilders.containsKey(name)) {
            throw new IllegalArgumentException(format("Event processor with name %s already exists", name));
        }
        eventProcessorBuilders.put(name, eventProcessorBuilder);
        return this;
    }

    @Override
    public EventProcessorRegistry registerTrackingEventProcessor(String name,
                                                                 Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> source,
                                                                 Function<Configuration, TrackingEventProcessorConfiguration> processorConfiguration) {
        registerEventProcessor(name, (n, c, ehi) -> trackingEventProcessor(c, n, ehi, processorConfiguration, source));
        return this;
    }

    @Override
    public EventProcessorRegistry registerSubscribingEventProcessor(String name,
                                                                    Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
        registerEventProcessor(name, (n, c, ehi) -> subscribingEventProcessor(n, c, ehi, messageSource));
        return this;
    }

    @Override
    public EventProcessorRegistry usingTrackingProcessors(
            Function<Configuration, TrackingEventProcessorConfiguration> processorConfig) {
        registerEventProcessorFactory(
                (name, config, ehi) ->
                        trackingEventProcessor(config, name, ehi, processorConfig, Configuration::eventBus));
        return this;
    }

    @Override
    public EventProcessorRegistry registerTokenStore(String name, Function<Configuration, TokenStore> tokenStore) {
        this.tokenStore.put(name, tokenStore);
        return this;
    }

    @Override
    public EventProcessorRegistry registerHandlerInterceptor(String processorName,
                                                             Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> interceptorBuilder) {
        if (configuration != null) {
            eventProcessor(processorName).ifPresent(eventProcessor -> eventProcessor
                    .registerInterceptor(interceptorBuilder.apply(configuration)));
        } else {
            handlerInterceptorsBuilders.computeIfAbsent(processorName, k -> new ArrayList<>())
                                       .add(interceptorBuilder);
        }
        return this;
    }

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor(String processorName) {
        Assert.state(configuration != null, () -> "Configuration is not initialized yet");
        return handlerInterceptorsBuilders.getOrDefault(processorName, new ArrayList<>())
                                          .stream()
                                          .map(hi -> hi.apply(configuration))
                                          .collect(Collectors.toList());
    }


    @Override
    public EventProcessorRegistry registerEventProcessorFactory(EventProcessorBuilder eventProcessorBuilder) {
        this.defaultEventProcessorBuilder = eventProcessorBuilder;
        return this;
    }

    @Override
    public EventProcessorRegistry configureErrorHandler(Function<Configuration, ErrorHandler> errorHandlerBuilder) {
        this.defaultErrorHandler.update(errorHandlerBuilder);
        return this;
    }

    @Override
    public EventProcessorRegistry configureErrorHandler(String name,
                                                        Function<Configuration, ErrorHandler> errorHandlerBuilder) {
        this.errorHandlers.put(name, errorHandlerBuilder);
        return this;
    }

    @Override
    public EventProcessorRegistry configureMessageMonitor(String name, MessageMonitorFactory messageMonitorFactory) {
        this.messageMonitorFactories.put(name, messageMonitorFactory);
        return this;
    }

    @Override
    public Map<String, EventProcessor> eventProcessors() {
        Assert.state(configuration != null, () -> "Configuration is not initialized yet");
        Map<String, EventProcessor> result = new HashMap<>(eventProcessors.size());
        eventProcessors.forEach((name, component) -> result.put(name, component.get()));
        return result;
    }

    @Override
    public void initialize(Configuration config) {
        this.configuration = config;
        invokerBuilders.forEach((processorName, builderFunctions) -> eventProcessors
                .put(processorName, new Component<>(config, processorName, c -> {
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
                                               .forEach(eventProcessor::registerInterceptor);
                    return eventProcessor;
                })));
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
        return subscribingEventProcessor(name, conf, eventHandlerInvoker, Configuration::eventBus);
    }

    private SubscribingEventProcessor subscribingEventProcessor(String name, Configuration conf,
                                                                EventHandlerInvoker eventHandlerInvoker,
                                                                Function<Configuration, SubscribableMessageSource<? extends EventMessage<?>>> messageSource) {
        return new SubscribingEventProcessor(name,
                                             eventHandlerInvoker,
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
                                          conf.getComponent(TransactionManager.class, NoTransactionManager::instance),
                                          getMessageMonitor(conf, EventProcessor.class, name),
                                          RollbackConfigurationType.ANY_THROWABLE,
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
}
