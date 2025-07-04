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
import org.axonframework.eventhandling.EventHandlerInvoker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A simple implementation of the {@link EventProcessingModule} interface.
 * <p>
 * This implementation provides basic event handler and saga registration capabilities, focusing on the management
 * of event handlers and their assignment to processing groups.
 * <p>
 * Note: This is a simplified implementation that registers event handlers and sagas as components.
 * For more advanced event processing configurations, consider using specialized modules.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SimpleEventProcessingModule extends BaseModule<SimpleEventProcessingModule>
        implements EventProcessingModule,
                   EventProcessingModule.SetupPhase,
                   EventProcessingModule.EventHandlerPhase {

    private final List<ComponentBuilder<Object>> eventHandlers = new ArrayList<>();
    private final List<SagaRegistration> sagaRegistrations = new ArrayList<>();
    private final Map<String, ComponentBuilder<EventHandlerInvoker>> eventHandlerInvokers = new HashMap<>();
    private String defaultProcessingGroup;

    /**
     * Creates a new {@code SimpleEventProcessingModule} with the given {@code moduleName}.
     *
     * @param moduleName The name of the event processing module.
     */
    public SimpleEventProcessingModule(@Nonnull String moduleName) {
        super(requireNonNull(moduleName, "Module name cannot be null."));
    }

    @Override
    public EventProcessingModule.EventHandlerPhase eventHandlers() {
        return this;
    }

    @Override
    public EventProcessingModule.EventHandlerPhase eventHandler(@Nonnull ComponentBuilder<Object> eventHandlerBuilder) {
        requireNonNull(eventHandlerBuilder, "Event handler builder cannot be null.");
        eventHandlers.add(eventHandlerBuilder);
        return this;
    }

    @Override
    public <T> EventProcessingModule.EventHandlerPhase saga(@Nonnull Class<T> sagaType) {
        requireNonNull(sagaType, "Saga type cannot be null.");
        sagaRegistrations.add(new SagaRegistration(sagaType, null));
        return this;
    }

    @Override
    public <T> EventProcessingModule.EventHandlerPhase saga(@Nonnull Class<T> sagaType, 
                                     @Nonnull Consumer<SagaConfigurer<T>> sagaConfigurer) {
        requireNonNull(sagaType, "Saga type cannot be null.");
        requireNonNull(sagaConfigurer, "Saga configurer cannot be null.");
        SagaConfigurer<T> configurer = new SagaConfigurer<>(sagaType);
        sagaConfigurer.accept(configurer);
        sagaRegistrations.add(new SagaRegistration(sagaType, configurer));
        return this;
    }

    @Override
    public EventProcessingModule.EventHandlerPhase eventHandlerInvoker(@Nonnull String processingGroup,
                                                 @Nonnull ComponentBuilder<EventHandlerInvoker> eventHandlerInvokerBuilder) {
        requireNonNull(processingGroup, "Processing group cannot be null.");
        requireNonNull(eventHandlerInvokerBuilder, "Event handler invoker builder cannot be null.");
        eventHandlerInvokers.put(processingGroup, eventHandlerInvokerBuilder);
        return this;
    }

    @Override
    public EventProcessingModule.EventHandlerPhase defaultProcessingGroup(@Nonnull String processingGroup) {
        requireNonNull(processingGroup, "Processing group cannot be null.");
        this.defaultProcessingGroup = processingGroup;
        return this;
    }

    @Override
    public EventProcessingModule build() {
        registerEventHandlers();
        registerSagas();
        registerEventHandlerInvokers();
        return this;
    }

    private void registerEventHandlers() {
        componentRegistry(cr -> {
            for (ComponentBuilder<Object> eventHandler : eventHandlers) {
                cr.registerComponent(Object.class, eventHandler);
            }
        });
    }

    private void registerSagas() {
        componentRegistry(cr -> {
            for (SagaRegistration sagaRegistration : sagaRegistrations) {
                // Register the saga type as a component
                @SuppressWarnings("unchecked")
                Class<Object> sagaType = (Class<Object>) sagaRegistration.sagaType;
                cr.registerComponent(sagaType, config -> {
                    try {
                        return sagaType.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create saga instance for type: " + sagaType, e);
                    }
                });
            }
        });
    }

    private void registerEventHandlerInvokers() {
        componentRegistry(cr -> {
            for (Map.Entry<String, ComponentBuilder<EventHandlerInvoker>> entry : eventHandlerInvokers.entrySet()) {
                String processingGroup = entry.getKey();
                ComponentBuilder<EventHandlerInvoker> invokerBuilder = entry.getValue();
                cr.registerComponent(EventHandlerInvoker.class, processingGroup, invokerBuilder);
            }
        });
    }

    /**
     * Helper class to hold saga registration information.
     */
    private static class SagaRegistration {
        private final Class<?> sagaType;
        private final SagaConfigurer<?> sagaConfigurer;

        SagaRegistration(Class<?> sagaType, SagaConfigurer<?> sagaConfigurer) {
            this.sagaType = sagaType;
            this.sagaConfigurer = sagaConfigurer;
        }
    }
} 