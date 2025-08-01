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

package org.axonframework.eventhandling.subscribing;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.configuration.EventProcessorsCustomization;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.eventhandling.pipeline.DefaultEventProcessorHandlingComponent;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubscribingEventProcessorModule extends BaseModule<SubscribingEventProcessorModule>
        implements EventProcessorModule,
        EventProcessorModule.SubscribingSourcePhase,
        EventProcessorModule.EventHandlersPhase {

    private final String processorName;
    private final Map<QualifiedName, ComponentBuilder<EventHandler>> handlerBuilders;
    private ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> subscribableMessageSourceBuilder;

    // todo: defaults - should be configurable
    private final EventProcessorsCustomization eventProcessorsCustomization = new EventProcessorsCustomization();
    private final MessageHandlerInterceptors messageHandlerInterceptors = new MessageHandlerInterceptors();

    public SubscribingEventProcessorModule(String processorName) {
        super(processorName);
        this.processorName = processorName;
        this.handlerBuilders = new HashMap<>();
    }

    @Override
    public EventHandlersPhase eventSource(
            @Nonnull ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> subscribableMessageSourceBuilder) {
        this.subscribableMessageSourceBuilder = subscribableMessageSourceBuilder;
        return this;
    }

    @Override
    public EventHandlersPhase eventHandler(@Nonnull QualifiedName eventName,
                                           @Nonnull ComponentBuilder<EventHandler> eventHandler) {
        handlerBuilders.put(eventName, eventHandler);
        return this;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        // todo: move it to the component registry!
        var eventHandlingComponent = new SimpleEventHandlingComponent();
        handlerBuilders.forEach((key, value) -> eventHandlingComponent.subscribe(key, value.build(parent)));

        var eventSource = subscribableMessageSourceBuilder.build(parent);

        var spanFactory = eventProcessorsCustomization.spanFactory();
        var messageMonitor = eventProcessorsCustomization.messageMonitor();
        var decoratedEventHandlingComponent = new DefaultEventProcessorHandlingComponent(
                spanFactory,
                messageMonitor,
                messageHandlerInterceptors,
                eventHandlingComponent,
                false
        );
        var processor = new SubscribingEventProcessor(
                processorName,
                eventSource,
                List.of(decoratedEventHandlingComponent),
                new SimpleUnitOfWorkFactory(),
                c -> c
        );
        lifecycleRegistry.onStart(2, processor::start);
        lifecycleRegistry.onShutdown(2, processor::shutDown);
        return super.build(parent, lifecycleRegistry);
    }

    @Override
    public EventProcessorModule build() {
        return this;
    }
}
