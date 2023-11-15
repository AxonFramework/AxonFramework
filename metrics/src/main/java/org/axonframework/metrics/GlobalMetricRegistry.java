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

package org.axonframework.metrics;

import com.codahale.metrics.MetricRegistry;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.config.Configurer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MultiMessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Registry for application metrics with convenient ways to register Axon components.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class GlobalMetricRegistry {

    private static final Logger logger = LoggerFactory.getLogger(GlobalMetricRegistry.class);

    private final MetricRegistry registry;

    /**
     * Initializes a new {@link GlobalMetricRegistry} delegating to a new {@link MetricRegistry} with default settings.
     */
    public GlobalMetricRegistry() {
        this(new MetricRegistry());
    }

    /**
     * Initializes a {@link GlobalMetricRegistry} delegating to the given {@code metricRegistry}.
     *
     * @param metricRegistry the {@link MetricRegistry} which will record the metrics
     */
    public GlobalMetricRegistry(MetricRegistry metricRegistry) {
        this.registry = metricRegistry;
    }

    /**
     * Registers the configured {@link MetricRegistry} with the given {@code configurer} via {@link
     * Configurer#configureMessageMonitor(Function)}. Components registered by the {@link Configurer} will be added by
     * invocation of {@link #registerComponent(Class, String)}.
     *
     * @param configurer the application's {@link Configurer}
     * @return the {@link Configurer}, with the new registration applied, for chaining
     */
    @SuppressWarnings("unchecked")
    public Configurer registerWithConfigurer(Configurer configurer) {
        return configurer.configureMessageMonitor(
                configuration
                        -> (componentType, componentName)
                        -> (MessageMonitor<Message<?>>) registerComponent(componentType, componentName)
        );
    }

    /**
     * Registers new metrics to the {@link MetricRegistry} to monitor a component of given {@code componentType}. The
     * monitor will be registered with the registry under the given {@code componentName}. The returned {@link
     * MessageMonitor} can be installed on the component to initiate the monitoring.
     *
     * @param componentType the type of component to register
     * @param componentName the name under which the component should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of the given {@code componentType}
     */
    public MessageMonitor<? extends Message<?>> registerComponent(Class<?> componentType, String componentName) {
        if (EventProcessor.class.isAssignableFrom(componentType)) {
            return registerEventProcessor(componentName);
        }
        if (CommandBus.class.isAssignableFrom(componentType)) {
            return registerCommandBus(componentName);
        }
        if (EventBus.class.isAssignableFrom(componentType)) {
            return registerEventBus(componentName);
        }
        if (QueryBus.class.isAssignableFrom(componentType)) {
            return registerQueryBus(componentName);
        }
        if (QueryUpdateEmitter.class.isAssignableFrom(componentType)) {
            return registerQueryUpdateEmitter(componentName);
        }
        logger.warn("Cannot provide MessageMonitor for component [{}] of type [{}]. Returning No-Op instance.",
                    componentName, componentType.getSimpleName());
        return NoOpMessageMonitor.instance();
    }

    /**
     * Registers new metrics to the registry to monitor an {@link EventProcessor}. The monitor will be registered with
     * the registry under the given {@code eventProcessorName}. The returned {@link MessageMonitor} can be installed on
     * the {@code EventProcessor} to initiate the monitoring.
     *
     * @param eventProcessorName the name under which the {@link EventProcessor} should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of an {@link EventProcessor}
     */
    public MessageMonitor<? super EventMessage<?>> registerEventProcessor(String eventProcessorName) {
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.builder().build();
        EventProcessorLatencyMonitor eventProcessorLatencyMonitor = new EventProcessorLatencyMonitor();
        CapacityMonitor capacityMonitor = new CapacityMonitor(1, TimeUnit.MINUTES);
        MessageCountingMonitor messageCountingMonitor = new MessageCountingMonitor();

        MetricRegistry eventProcessingRegistry = new MetricRegistry();
        eventProcessingRegistry.register("messageTimer", messageTimerMonitor);
        eventProcessingRegistry.register("latency", eventProcessorLatencyMonitor);
        eventProcessingRegistry.register("messageCounter", messageCountingMonitor);
        eventProcessingRegistry.register("capacity", capacityMonitor);
        registry.register(eventProcessorName, eventProcessingRegistry);

        List<MessageMonitor<? super EventMessage<?>>> monitors = new ArrayList<>();
        monitors.add(messageTimerMonitor);
        monitors.add(eventProcessorLatencyMonitor);
        monitors.add(capacityMonitor);
        monitors.add(messageCountingMonitor);
        return new MultiMessageMonitor<>(monitors);
    }

    /**
     * Registers new metrics to the registry to monitor a {@link CommandBus}. The monitor will be registered with the
     * registry under the given {@code commandBusName}. The returned {@link MessageMonitor} can be installed on the
     * {@code CommandBus} to initiate the monitoring.
     *
     * @param commandBusName the name under which the commandBus should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of a CommandBus
     */
    public MessageMonitor<? super CommandMessage<?>> registerCommandBus(String commandBusName) {
        return registerDefaultHandlerMessageMonitor(commandBusName);
    }

    /**
     * Registers new metrics to the registry to monitor an {@link EventBus}. The monitor will be registered with the
     * registry under the given {@code eventBusName}. The returned {@link MessageMonitor} can be installed on the {@code
     * EventBus} to initiate the monitoring.
     *
     * @param eventBusName the name under which the {@link EventBus} should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of an {@link EventBus}
     */
    public MessageMonitor<? super EventMessage<?>> registerEventBus(String eventBusName) {
        MessageCountingMonitor messageCounterMonitor = new MessageCountingMonitor();
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.builder().build();

        MetricRegistry eventProcessingRegistry = new MetricRegistry();
        eventProcessingRegistry.register("messageCounter", messageCounterMonitor);
        eventProcessingRegistry.register("messageTimer", messageTimerMonitor);
        registry.register(eventBusName, eventProcessingRegistry);

        return new MultiMessageMonitor<>(Arrays.asList(messageCounterMonitor, messageTimerMonitor));
    }

    /**
     * Registers new metrics to the registry to monitor a {@link QueryBus}. The monitor will be registered with the
     * registry under the given {@code queryBusName}. The returned {@link MessageMonitor} can be installed on the {@code
     * QueryBus} to initiate the monitoring.
     *
     * @param queryBusName the name under which the {@link QueryBus} should be registered to the registry
     * @return a {@link MessageMonitor} to monitor the behavior of a {@link QueryBus}
     */
    public MessageMonitor<? super QueryMessage<?, ?>> registerQueryBus(String queryBusName) {
        return registerDefaultHandlerMessageMonitor(queryBusName);
    }

    /**
     * Registers new metrics to the registry to monitor a {@link QueryUpdateEmitter}. The monitor will be registered
     * with the registry under the given {@code updateEmitterName}. The returned {@link MessageMonitor} can be installed
     * on the {@code QueryUpdateEmitter} to initiate the monitoring.
     *
     * @param updateEmitterName the name under which the {@link QueryUpdateEmitter} should be registered to the
     *                          registry
     * @return a {@link MessageMonitor} to monitor the behavior of a {@link QueryUpdateEmitter}
     */
    private MessageMonitor<? extends Message<?>> registerQueryUpdateEmitter(String updateEmitterName) {
        return registerDefaultHandlerMessageMonitor(updateEmitterName);
    }

    private MessageMonitor<Message<?>> registerDefaultHandlerMessageMonitor(String name) {
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.builder().build();
        CapacityMonitor capacityMonitor = new CapacityMonitor(1, TimeUnit.MINUTES);
        MessageCountingMonitor messageCountingMonitor = new MessageCountingMonitor();

        MetricRegistry handlerRegistry = new MetricRegistry();
        handlerRegistry.register("messageTimer", messageTimerMonitor);
        handlerRegistry.register("capacity", capacityMonitor);
        handlerRegistry.register("messageCounter", messageCountingMonitor);
        registry.register(name, handlerRegistry);

        return new MultiMessageMonitor<>(messageTimerMonitor, capacityMonitor, messageCountingMonitor);
    }

    /**
     * Returns the global {@link MetricRegistry} to which components are registered.
     *
     * @return the global registry
     */
    public MetricRegistry getRegistry() {
        return registry;
    }
}
