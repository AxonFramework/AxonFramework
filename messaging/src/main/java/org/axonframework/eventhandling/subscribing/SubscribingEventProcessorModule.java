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
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.TracingEventHandlingComponent;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.lifecycle.Phase;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class SubscribingEventProcessorModule extends BaseModule<SubscribingEventProcessorModule>
        implements EventProcessorModule,
        EventProcessorModule.CustomizationPhase<SubscribingEventProcessorConfiguration>
{

    private final String processorName;
    private ComponentBuilder<SubscribingEventProcessorConfiguration> configurationBuilder;

    // todo: defaults - should be configurable
    private final MessageHandlerInterceptors messageHandlerInterceptors = new MessageHandlerInterceptors();

    public SubscribingEventProcessorModule(String processorName) {
        super(processorName);
        this.processorName = processorName;
    }

    @Override
    public Configuration build(@Nonnull Configuration parent, @Nonnull LifecycleRegistry lifecycleRegistry) {
        // todo: move it to the component registry!
        var configuration = configurationBuilder.build(parent);

        var spanFactory = configuration.spanFactory();
        var messageMonitor = configuration.messageMonitor();
        var eventHandlingComponents = configuration.eventHandlingComponents();
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
                configuration.eventHandlingComponents(decoratedEventHandlingComponents)
        );
        lifecycleRegistry.onStart(Phase.INBOUND_EVENT_CONNECTORS, processor::start);
        lifecycleRegistry.onShutdown(Phase.INBOUND_EVENT_CONNECTORS, processor::shutdownAsync);
        return super.build(parent, lifecycleRegistry);
    }


    @Override
    public EventProcessorModule configure(
            @Nonnull ComponentBuilder<SubscribingEventProcessorConfiguration> configurationBuilder) {
        this.configurationBuilder = configurationBuilder;
        return this;
    }

    @Override
    public EventProcessorModule customize(
            @Nonnull ComponentBuilder<UnaryOperator<SubscribingEventProcessorConfiguration>> customizationBuilder) {
        configure(cfg -> customizationBuilder.build(cfg).apply(parentConfigurationOrDefault(cfg)));
        return this;
    }

    private static SubscribingEventProcessorConfiguration parentConfigurationOrDefault(
            Configuration cfg
    ) {
        return cfg.getOptionalComponent(SubscribingEventProcessorConfiguration.class)
                  .orElseGet(SubscribingEventProcessorConfiguration::new);
    }

    @Override
    public EventProcessorModule build() {
        return this;
    }
}
