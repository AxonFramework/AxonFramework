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
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TracingEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class SubscribingEventProcessorModule extends BaseModule<SubscribingEventProcessorModule>
        implements EventProcessorModule,
        EventProcessorModule.SubscribingSourcePhase<SubscribingEventProcessorsCustomization>,
        EventProcessorModule.EventHandlingPhase<SubscribingEventProcessorsCustomization>,
        EventProcessorModule.EventHandlingComponentsPhase<SubscribingEventProcessorsCustomization>,
        EventProcessorModule.CustomizationPhase<SubscribingEventProcessorsCustomization>,
        EventProcessorModule.BuildPhase {

    private final String processorName;
    private final List<ComponentBuilder<EventHandlingComponent>> eventHandlingBuilders;
    private ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> subscribableMessageSourceBuilder;
    private UnaryOperator<SubscribingEventProcessorsCustomization> customizationOverride = c -> c;

    // todo: defaults - should be configurable
    private final MessageHandlerInterceptors messageHandlerInterceptors = new MessageHandlerInterceptors();

    public SubscribingEventProcessorModule(String processorName) {
        super(processorName);
        this.processorName = processorName;
        this.eventHandlingBuilders = new ArrayList<>();
    }

    @Override
    public EventHandlingPhase<SubscribingEventProcessorsCustomization> eventSource(
            @Nonnull ComponentBuilder<SubscribableMessageSource<? extends EventMessage<?>>> subscribableMessageSourceBuilder
    ) {
        this.subscribableMessageSourceBuilder = subscribableMessageSourceBuilder;
        return this;
    }

    @Override
    public EventHandlingComponentsPhase<SubscribingEventProcessorsCustomization> eventHandlingComponent(
            @Nonnull ComponentBuilder<EventHandlingComponent> eventHandlingComponentBuilder) {
        eventHandlingBuilders.add(eventHandlingComponentBuilder);
        return this;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        // todo: move it to the component registry!

        var eventSource = subscribableMessageSourceBuilder.build(parent);

        var eventProcessorsCustomization = customizationOverride.apply(
                parent.getComponent(SubscribingEventProcessorsCustomization.class)
        ); // todo: write customization here!

        var spanFactory = eventProcessorsCustomization.spanFactory();
        var messageMonitor = eventProcessorsCustomization.messageMonitor();

        var eventHandlingComponents = eventHandlingBuilders.stream()
                                                           .map(hb -> hb.build(parent))
                                                           .toList();
        List<EventHandlingComponent> decoratedEventHandlingComponents = eventHandlingComponents
                .stream()
                .map(c -> new TracingEventHandlingComponent(
                        (event) -> spanFactory.createProcessEventSpan(false, event),
                        new MonitoringEventHandlingComponent(
                                messageMonitor,
                                new InterceptingEventHandlingComponent(
                                        messageHandlerInterceptors,
                                        c
                                )
                        )
                )).collect(Collectors.toUnmodifiableList());
        var processor = new SubscribingEventProcessor(
                processorName,
                eventSource,
                decoratedEventHandlingComponents,
                new SimpleUnitOfWorkFactory(),
                c -> c
        );
        lifecycleRegistry.onStart(2, processor::start);
        lifecycleRegistry.onShutdown(2, processor::shutDown);
        return super.build(parent, lifecycleRegistry);
    }

    @Override
    public BuildPhase defaults() {
        return this;
    }

    @Override
    public BuildPhase override(@Nonnull UnaryOperator<SubscribingEventProcessorsCustomization> customizationOverride) {
        this.customizationOverride = customizationOverride;
        return this;
    }

    @Override
    public EventHandlingPhase<SubscribingEventProcessorsCustomization> and() {
        return this;
    }

    @Override
    public CustomizationPhase<SubscribingEventProcessorsCustomization> customization() {
        return this;
    }

    @Override
    public EventProcessorModule build() {
        return this;
    }
}
