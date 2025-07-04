/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Simple implementation of the {@link EventProcessingModule}.
 * <p>
 * This implementation handles the configuration and lifecycle management of event processors, event handlers, and sagas
 * within the module.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SimpleEventProcessingModule extends BaseModule<SimpleEventProcessingModule>
        implements EventProcessingModule,
                   EventProcessingModule.SetupPhase,
                   EventProcessingModule.EventHandlerPhase,
                   EventProcessingModule.EventProcessorPhase,
                   EventProcessingModule.SubscribingEventProcessorPhase,
                   EventProcessingModule.PooledStreamingEventProcessorPhase {

    private final List<ComponentBuilder<Object>> eventHandlerBuilders = new ArrayList<>();
    private final List<SagaRegistration> sagaRegistrations = new ArrayList<>();
    private final Map<String, ComponentBuilder<EventHandlerInvoker>> eventHandlerInvokers = new HashMap<>();
    private final Map<String, EventProcessorConfiguration> eventProcessorConfigurations = new HashMap<>();
    private final Map<String, SubscribingEventProcessorConfiguration> subscribingProcessorConfigurations = new HashMap<>();
    private final Map<String, PooledStreamingEventProcessorConfiguration> pooledStreamingProcessorConfigurations = new HashMap<>();

    // Current processor being configured
    private String currentProcessorName;
    private EventProcessorType currentProcessorType;

    SimpleEventProcessingModule(@Nonnull String moduleName) {
        super(moduleName);
    }

    @Override
    public EventHandlerPhase eventHandlers() {
        return this;
    }

    @Override
    public EventProcessorPhase eventProcessors() {
        return this;
    }

    @Override
    public EventHandlerPhase eventHandler(@Nonnull ComponentBuilder<Object> eventHandlerBuilder) {
        requireNonNull(eventHandlerBuilder, "Event handler builder cannot be null.");
        eventHandlerBuilders.add(eventHandlerBuilder);
        return this;
    }

    @Override
    public <T> EventHandlerPhase saga(@Nonnull Class<T> sagaType) {
        requireNonNull(sagaType, "Saga type cannot be null.");
        sagaRegistrations.add(new SagaRegistration(sagaType, null));
        return this;
    }

    @Override
    public <T> EventHandlerPhase saga(@Nonnull Class<T> sagaType,
                                      @Nonnull Consumer<SagaConfigurer<T>> sagaConfigurer) {
        requireNonNull(sagaType, "Saga type cannot be null.");
        requireNonNull(sagaConfigurer, "Saga configurer cannot be null.");
        sagaRegistrations.add(new SagaRegistration(sagaType, sagaConfigurer));
        return this;
    }

    @Override
    public EventHandlerPhase eventHandlerInvoker(@Nonnull String processingGroup,
                                                 @Nonnull ComponentBuilder<EventHandlerInvoker> eventHandlerInvokerBuilder) {
        requireNonNull(processingGroup, "Processing group cannot be null.");
        requireNonNull(eventHandlerInvokerBuilder, "Event handler invoker builder cannot be null.");
        eventHandlerInvokers.put(processingGroup, eventHandlerInvokerBuilder);
        return this;
    }

    @Override
    public SubscribingEventProcessorPhase subscribingEventProcessor(@Nonnull String processorName) {
        requireNonNull(processorName, "Processor name cannot be null.");
        currentProcessorName = processorName;
        currentProcessorType = EventProcessorType.SUBSCRIBING;
        subscribingProcessorConfigurations.putIfAbsent(processorName, new SubscribingEventProcessorConfiguration());
        return this;
    }

    @Override
    public PooledStreamingEventProcessorPhase pooledStreamingEventProcessor(@Nonnull String processorName) {
        requireNonNull(processorName, "Processor name cannot be null.");
        currentProcessorName = processorName;
        currentProcessorType = EventProcessorType.POOLED_STREAMING;
        pooledStreamingProcessorConfigurations.putIfAbsent(processorName, new PooledStreamingEventProcessorConfiguration());
        return this;
    }

    @Override
    public EventProcessorPhase customEventProcessor(@Nonnull String processorName,
                                                    @Nonnull ComponentBuilder<EventProcessor> eventProcessorBuilder) {
        requireNonNull(processorName, "Processor name cannot be null.");
        requireNonNull(eventProcessorBuilder, "Event processor builder cannot be null.");
        eventProcessorConfigurations.put(processorName, new EventProcessorConfiguration(eventProcessorBuilder));
        return this;
    }

    @Override
    public SubscribingEventProcessorPhase messageSource(
            @Nonnull ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSourceBuilder) {
        requireNonNull(messageSourceBuilder, "Message source builder cannot be null.");
        if (currentProcessorType != EventProcessorType.SUBSCRIBING) {
            throw new IllegalStateException("Message source can only be configured for subscribing event processors.");
        }
        subscribingProcessorConfigurations.get(currentProcessorName).messageSourceBuilder = messageSourceBuilder;
        return this;
    }

    @Override
    public SubscribingEventProcessorPhase configureSubscribing(@Nonnull Consumer<SubscribingEventProcessor.Builder> processorConfigurer) {
        requireNonNull(processorConfigurer, "Processor configurer cannot be null.");
        if (currentProcessorType != EventProcessorType.SUBSCRIBING) {
            throw new IllegalStateException("This configure method can only be called for subscribing event processors.");
        }
        subscribingProcessorConfigurations.get(currentProcessorName).processorConfigurer = processorConfigurer;
        return this;
    }

    @Override
    public PooledStreamingEventProcessorPhase eventSource(
            @Nonnull ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSourceBuilder) {
        requireNonNull(eventSourceBuilder, "Event source builder cannot be null.");
        if (currentProcessorType != EventProcessorType.POOLED_STREAMING) {
            throw new IllegalStateException("Event source can only be configured for pooled streaming event processors.");
        }
        pooledStreamingProcessorConfigurations.get(currentProcessorName).eventSourceBuilder = eventSourceBuilder;
        return this;
    }

    @Override
    public PooledStreamingEventProcessorPhase tokenStore(@Nonnull ComponentBuilder<TokenStore> tokenStoreBuilder) {
        requireNonNull(tokenStoreBuilder, "Token store builder cannot be null.");
        if (currentProcessorType != EventProcessorType.POOLED_STREAMING) {
            throw new IllegalStateException("Token store can only be configured for pooled streaming event processors.");
        }
        pooledStreamingProcessorConfigurations.get(currentProcessorName).tokenStoreBuilder = tokenStoreBuilder;
        return this;
    }

    @Override
    public PooledStreamingEventProcessorPhase configurePooledStreaming(@Nonnull Consumer<PooledStreamingEventProcessor.Builder> processorConfigurer) {
        requireNonNull(processorConfigurer, "Processor configurer cannot be null.");
        if (currentProcessorType != EventProcessorType.POOLED_STREAMING) {
            throw new IllegalStateException("This configure method can only be called for pooled streaming event processors.");
        }
        pooledStreamingProcessorConfigurations.get(currentProcessorName).processorConfigurer = processorConfigurer;
        return this;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        registerComponents();
        return super.build(parent, lifecycleRegistry);
    }

    private void registerComponents() {
        componentRegistry(cr -> {
            // Register event handlers
            for (ComponentBuilder<Object> eventHandlerBuilder : eventHandlerBuilders) {
                cr.registerComponent(Object.class, eventHandlerBuilder);
            }

            // Register sagas
            for (SagaRegistration sagaRegistration : sagaRegistrations) {
                registerSaga(sagaRegistration);
            }

            // Register event handler invokers
            for (Map.Entry<String, ComponentBuilder<EventHandlerInvoker>> entry : eventHandlerInvokers.entrySet()) {
                cr.registerComponent(ComponentDefinition.ofTypeAndName(EventHandlerInvoker.class, entry.getKey())
                                                        .withBuilder(entry.getValue()));
            }

            // Register event processors
            for (Map.Entry<String, EventProcessorConfiguration> entry : eventProcessorConfigurations.entrySet()) {
                cr.registerComponent(ComponentDefinition.ofTypeAndName(EventProcessor.class, entry.getKey())
                                                        .withBuilder(entry.getValue().eventProcessorBuilder));
            }

            // Register subscribing event processors
            for (Map.Entry<String, SubscribingEventProcessorConfiguration> entry : subscribingProcessorConfigurations.entrySet()) {
                cr.registerComponent(ComponentDefinition.ofTypeAndName(EventProcessor.class, entry.getKey())
                                                        .withBuilder(createSubscribingEventProcessor(entry.getKey(), entry.getValue())));
            }

            // Register pooled streaming event processors
            for (Map.Entry<String, PooledStreamingEventProcessorConfiguration> entry : pooledStreamingProcessorConfigurations.entrySet()) {
                cr.registerComponent(ComponentDefinition.ofTypeAndName(EventProcessor.class, entry.getKey())
                                                        .withBuilder(createPooledStreamingEventProcessor(entry.getKey(), entry.getValue())));
            }
        });
    }

    private ComponentBuilder<EventProcessor> createSubscribingEventProcessor(String processorName, SubscribingEventProcessorConfiguration config) {
        return configuration -> {
            SubscribingEventProcessor.Builder builder = SubscribingEventProcessor.builder()
                    .name(processorName)
                    .eventHandlerInvoker(createEventHandlerInvoker(processorName, configuration))
                    .messageSource(config.messageSourceBuilder != null 
                                  ? config.messageSourceBuilder.build(configuration)
                                  : configuration.getComponent(org.axonframework.eventhandling.EventBus.class));

            if (config.processorConfigurer != null) {
                config.processorConfigurer.accept(builder);
            }

            return builder.build();
        };
    }

    private ComponentBuilder<EventProcessor> createPooledStreamingEventProcessor(String processorName, PooledStreamingEventProcessorConfiguration config) {
        return configuration -> {
            PooledStreamingEventProcessor.Builder builder = PooledStreamingEventProcessor.builder()
                    .name(processorName)
                    .eventHandlerInvoker(createEventHandlerInvoker(processorName, configuration))
                    .coordinatorExecutor(Executors.newSingleThreadScheduledExecutor())
                    .workerExecutor(Executors.newScheduledThreadPool(4));

            if (config.eventSourceBuilder != null) {
                builder.eventSource(config.eventSourceBuilder.build(configuration));
            } else {
                @SuppressWarnings("unchecked")
                StreamableEventSource<? extends EventMessage<?>> eventSource = 
                    (StreamableEventSource<? extends EventMessage<?>>) configuration.getComponent(org.axonframework.eventsourcing.eventstore.EventStore.class);
                builder.eventSource(eventSource);
            }

            if (config.tokenStoreBuilder != null) {
                builder.tokenStore(config.tokenStoreBuilder.build(configuration));
            } else {
                builder.tokenStore(configuration.getComponent(TokenStore.class));
            }

            if (config.processorConfigurer != null) {
                config.processorConfigurer.accept(builder);
            }

            return builder.build();
        };
    }

    private EventHandlerInvoker createEventHandlerInvoker(String processorName, Configuration configuration) {
        return eventHandlerInvokers.containsKey(processorName) 
               ? eventHandlerInvokers.get(processorName).build(configuration)
               : SimpleEventHandlerInvoker.builder().build();
    }

    @SuppressWarnings("unchecked")
    private <T> void registerSaga(SagaRegistration sagaRegistration) {
        // This is a simplified saga registration - in a real implementation,
        // you would use the SagaConfigurer to properly configure the saga
        Class<T> sagaType = (Class<T>) sagaRegistration.sagaType;
        if (sagaRegistration.sagaConfigurer != null) {
            // Apply saga configuration if provided
            Consumer<SagaConfigurer<T>> configurer = (Consumer<SagaConfigurer<T>>) sagaRegistration.sagaConfigurer;
            // Here you would typically use the EventProcessingConfigurer's registerSaga method
            // This is a placeholder for the actual saga registration logic
        }
    }

    @Override
    public SimpleEventProcessingModule build() {
        return this;
    }

    private enum EventProcessorType {
        SUBSCRIBING,
        POOLED_STREAMING,
        CUSTOM
    }

    private static class EventProcessorConfiguration {
        private final ComponentBuilder<EventProcessor> eventProcessorBuilder;

        EventProcessorConfiguration(ComponentBuilder<EventProcessor> eventProcessorBuilder) {
            this.eventProcessorBuilder = eventProcessorBuilder;
        }
    }

    private static class SubscribingEventProcessorConfiguration {
        private ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> messageSourceBuilder;
        private Consumer<SubscribingEventProcessor.Builder> processorConfigurer;
    }

    private static class PooledStreamingEventProcessorConfiguration {
        private ComponentBuilder<StreamableEventSource<? extends EventMessage<?>>> eventSourceBuilder;
        private ComponentBuilder<TokenStore> tokenStoreBuilder;
        private Consumer<PooledStreamingEventProcessor.Builder> processorConfigurer;
    }

    private static class SagaRegistration {
        private final Class<?> sagaType;
        private final Consumer<?> sagaConfigurer;

        SagaRegistration(Class<?> sagaType, Consumer<?> sagaConfigurer) {
            this.sagaType = sagaType;
            this.sagaConfigurer = sagaConfigurer;
        }
    }
} 