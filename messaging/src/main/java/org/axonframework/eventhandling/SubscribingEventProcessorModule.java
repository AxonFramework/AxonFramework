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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.BaseModule;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.SubscribableMessageSource;

import java.util.HashMap;
import java.util.Map;

public class SubscribingEventProcessorModule extends BaseModule<SubscribingEventProcessorModule>
        implements EventProcessorModule,
        EventProcessorModule.SubscribingSourcePhase,
        EventProcessorModule.EventHandlersPhase {

    private final String processorName;
    private final Map<QualifiedName, ComponentBuilder<EventHandler>> handlerBuilders;
    private ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> subscribableMessageSourceBuilder;

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

        var processor = SubscribingEventProcessor.builder()
                                                 .name(processorName)
                                                 .eventHandlingComponent(eventHandlingComponent)
                                                 .messageSource(eventSource)
                                                 .build();
        lifecycleRegistry.onStart(2, processor::start);
        lifecycleRegistry.onShutdown(2, processor::shutDown);
        return super.build(parent, lifecycleRegistry);
    }

    @Override
    public EventProcessorModule build() {
        return this;
    }
}
